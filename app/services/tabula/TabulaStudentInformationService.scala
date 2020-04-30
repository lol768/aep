package services.tabula

import com.google.inject.ImplementedBy
import com.google.inject.name.Named
import domain.tabula
import javax.inject.{Inject, Singleton}
import play.api.Logger
import play.api.cache.AsyncCacheApi
import play.api.libs.ws.WSClient
import services.tabula.TabulaStudentInformationService.{GetMultipleStudentInformationOptions, GetStudentInformationOptions, MultipleStudentInformationReturn, StudentInformationReturn}
import system.TimingCategories
import warwick.caching._
import warwick.core.Logging
import warwick.core.helpers.ServiceResults.Implicits._
import warwick.core.helpers.ServiceResults.ServiceResult
import warwick.core.helpers.{JavaTime, ServiceResults}
import warwick.core.timing.{TimingContext, TimingService}
import warwick.sso.UniversityID

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}

@ImplementedBy(classOf[CachingTabulaStudentInformationService])
trait TabulaStudentInformationService {
  def getStudentInformation(options: GetStudentInformationOptions)(implicit t: TimingContext): Future[StudentInformationReturn]
  def getMultipleStudentInformation(options: GetMultipleStudentInformationOptions)(implicit t: TimingContext): Future[MultipleStudentInformationReturn]
}

object TabulaStudentInformationService {
  type StudentInformationReturn = ServiceResult[tabula.SitsProfile]
  type MultipleStudentInformationReturn = ServiceResult[Map[UniversityID, tabula.SitsProfile]]

  case class GetStudentInformationOptions(
    universityID: UniversityID,
  ) {
    def cacheKey = s"u:${universityID.string}"
  }

  case class GetMultipleStudentInformationOptions(
    universityIDs: Seq[UniversityID],
  )
}

class CachingTabulaStudentInformationService @Inject() (
  @Named("TabulaStudentInformationService-NoCache") impl: TabulaStudentInformationService,
  cache: AsyncCacheApi,
  timing: TimingService,
)(implicit ec: ExecutionContext) extends TabulaStudentInformationService with Logging {

  // Tabula's profile import is once a day so no point having a shorter soft ttl than that
  private lazy val ttlStrategy: StudentInformationReturn => Ttl = a => a.fold(
    _ => Ttl(soft = 10.seconds, medium = 1.minute, hard = 1.hour),
    _ => Ttl(soft = 1.day, medium = 3.days, hard = 14.days) // https://media.giphy.com/media/s3vwh1LY1fUUU/source.gif
  )

  private lazy val wrappedCache = VariableTtlCacheHelper.async[StudentInformationReturn](cache, logger, ttlStrategy, timing)

  override def getStudentInformation(options: GetStudentInformationOptions)(implicit t: TimingContext): Future[StudentInformationReturn] = {
    wrappedCache.getOrElseUpdate(options.cacheKey) {
      impl.getStudentInformation(options)
    }
  }

  override def getMultipleStudentInformation(options: GetMultipleStudentInformationOptions)(implicit t: TimingContext): Future[MultipleStudentInformationReturn] = {
    if (options.universityIDs.isEmpty) return Future.successful(ServiceResults.success(Map.empty))

    Future.sequence(
      options.universityIDs.map { universityId =>
        timing.time(TimingCategories.CacheRead) {
          cache.get[CacheElement[StudentInformationReturn]](GetStudentInformationOptions(universityId).cacheKey)
            .filter(_.exists(_.value.isRight)) // Only consider cached success
            .map(universityId -> _)
            .recover(_ => universityId -> None) // Just ignore any failed cache gets
        }
      }
    ).flatMap { cacheResults =>
      val missing: Seq[UniversityID] = cacheResults.filter(_._2.isEmpty).map(_._1)

      // Stale values (> medium expiry) are updated from source first and are only returned if there's an error
      val stale: Seq[(UniversityID, tabula.SitsProfile)] =
        cacheResults.filter(_._2.exists(_.isStale)).map { case (universityId, cacheElement) =>
          universityId -> cacheElement.get.value.getOrElse(throw new IllegalArgumentException)
        }

      // Slightly stale (> soft expiry) are returned and updated in the background
      val slightlyStale: Seq[(UniversityID, tabula.SitsProfile)] =
        cacheResults.filter(_._2.exists(ce => !ce.isStale && ce.isSlightlyStale)).map { case (universityId, cacheElement) =>
          universityId -> cacheElement.get.value.getOrElse(throw new IllegalArgumentException)
        }

      // Fresh values are just returned
      val fresh: Seq[(UniversityID, tabula.SitsProfile)] =
        cacheResults.filter(_._2.exists(ce => !ce.isStale && !ce.isSlightlyStale)).map { case (universityId, cacheElement) =>
          universityId -> cacheElement.get.value.getOrElse(throw new IllegalArgumentException)
        }

      // TODO Make VariableTtlCacheHelper.doSet public, this is the same thing
      def cacheResult(universityID: UniversityID, result: tabula.SitsProfile): Future[CacheElement[StudentInformationReturn]] = {
        val value = ServiceResults.success(result)

        val key = GetStudentInformationOptions(universityID).cacheKey
        val ttl = ttlStrategy(value)
        val now = JavaTime.instant
        val softExpiry = now.plusSeconds(ttl.soft.toSeconds).getEpochSecond
        val mediumExpiry = now.plusSeconds(ttl.medium.toSeconds).getEpochSecond
        val element = CacheElement(value, now.getEpochSecond, softExpiry, mediumExpiry)
        timing.time(TimingCategories.CacheWrite)(cache.set(key, element, ttl.hard))
          .recover { case e: Throwable => logger.error(s"Failure to update cache for $key", e) }
          .map(_ => element)
      }

      def cacheResultsAndReturn(results: Map[UniversityID, tabula.SitsProfile]): Future[MultipleStudentInformationReturn] =
        // Set returned values in the cache
        Future.sequence(results.toSeq.map(r => cacheResult(r._1, r._2))).map(_ => ServiceResults.success(results))

      val noop: Future[MultipleStudentInformationReturn] = Future.successful(ServiceResults.success(Map.empty))

      // Handle these as separate futures as it just makes it simpler
      ServiceResults.futureSequence(Seq(
        // Fetch missing items
        if (missing.nonEmpty)
          impl.getMultipleStudentInformation(GetMultipleStudentInformationOptions(missing)).successFlatMapTo(cacheResultsAndReturn)
        else noop,

        // Fetch stale items, falling back to existing on error
        if (stale.nonEmpty)
          impl.getMultipleStudentInformation(GetMultipleStudentInformationOptions(stale.map(_._1))).flatMap(_.fold(
            errors => {
              logger.error(s"Unable to fetch fresh value for ${stale.map(_._1).mkString(", ")} failed ($errors). Returning stale value")
              Future.successful(ServiceResults.success(stale.toMap))
            },
            cacheResultsAndReturn
          ))
        else noop,

        // Fire off an update for slightly stale items but return the existing cached values immediately
        if (slightlyStale.nonEmpty) {
          impl.getMultipleStudentInformation(GetMultipleStudentInformationOptions(slightlyStale.map(_._1))).successFlatMapTo(cacheResultsAndReturn)

          Future.successful(ServiceResults.success(slightlyStale.toMap))
        } else noop,

        // Fresh
        Future.successful(ServiceResults.success(fresh.toMap))
      )).successMapTo(_.fold(Map.empty)(_ ++ _))
    }
  }
}

@Singleton
class TabulaStudentInformationServiceImpl @Inject() (
  ws: WSClient,
  config: TabulaConfiguration,
  tabulaHttp: TabulaHttp,
  timing: TimingService,
)(implicit ec: ExecutionContext) extends TabulaStudentInformationService with Logging {

  import tabulaHttp._

  override def getStudentInformation(options: GetStudentInformationOptions)(implicit t: TimingContext): Future[StudentInformationReturn] = timing.time(TimingCategories.TabulaRead) {
    val url = config.getStudentInformationUrl(options.universityID)
    val req = ws.url(url)
      .withQueryStringParameters(
        "fields" -> TabulaResponseParsers.TabulaProfileData.memberFields.mkString(",")
      )
    implicit def l: Logger = logger

    doRequest(url, "GET", req, description = "getStudentInformation").successFlatMapTo { jsValue =>
      parseAndValidate(jsValue, TabulaResponseParsers.TabulaProfileData.singleMemberResponseReads)
    }.successMapTo(_.toUserProfile)
  }

  override def getMultipleStudentInformation(options: GetMultipleStudentInformationOptions)(implicit t: TimingContext): Future[MultipleStudentInformationReturn] = timing.time(TimingCategories.TabulaRead) {
    if (options.universityIDs.isEmpty) return Future.successful(ServiceResults.success(Map.empty))

    // Restrict the batch size to avoid hitting the HTTP header size limit on Tabula's Tomcat
    ServiceResults.futureSequence(options.universityIDs.grouped(100).toSeq.map { universityIDs =>
      val url = config.getMultipleStudentInformationUrl
      val req = ws.url(url)
        .withQueryStringParameters(
          (Seq(
            "fields" -> TabulaResponseParsers.TabulaProfileData.memberFields.mkString(",")
          ) ++ universityIDs.map(u => "members" -> u.string)): _*
        )
      implicit def l: Logger = logger

      doRequest(url, "GET", req, description = "getMultipleStudentInformation").successFlatMapTo { jsValue =>
        parseAndValidate(jsValue, TabulaResponseParsers.TabulaProfileData.multiMemberResponseReads)
      }.successMapTo(_.view.mapValues(_.toUserProfile).toMap)
    }).successMapTo(_.fold(Map.empty)(_ ++ _))
  }
}
