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
import uk.ac.warwick.sso.client.trusted.TrustedApplicationsManager
import warwick.caching._
import warwick.core.Logging
import warwick.core.helpers.ServiceResults
import warwick.core.helpers.ServiceResults.Implicits._
import warwick.core.helpers.ServiceResults.ServiceResult
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

  private lazy val ttlStrategy: StudentInformationReturn => Ttl = a => a.fold(
    _ => Ttl(soft = 10.seconds, medium = 1.minute, hard = 1.hour),
    _ => Ttl(soft = 1.hour, medium = 1.day, hard = 7.days)
  )

  private lazy val wrappedCache = VariableTtlCacheHelper.async[StudentInformationReturn](cache, logger, ttlStrategy, timing)

  override def getStudentInformation(options: GetStudentInformationOptions)(implicit t: TimingContext): Future[StudentInformationReturn] = {
    wrappedCache.getOrElseUpdate(options.cacheKey) {
      impl.getStudentInformation(options)
    }
  }

  override def getMultipleStudentInformation(options: GetMultipleStudentInformationOptions)(implicit t: TimingContext): Future[MultipleStudentInformationReturn] =
    Future.sequence(
      options.universityIDs.map { universityID =>
        getStudentInformation(GetStudentInformationOptions(universityID))
      }
    ).map(results =>
      ServiceResults.success(results.flatMap(_.toOption).map(profile => profile.universityID -> profile).toMap)
    )
}

@Singleton
class TabulaStudentInformationServiceImpl @Inject() (
  ws: WSClient,
  config: TabulaConfiguration,
  trustedApplicationsManager: TrustedApplicationsManager,
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
      parseAndValidate(jsValue, TabulaResponseParsers.TabulaProfileData.memberReads)
    }.map(_.map(_.toUserProfile))
  }

  // Shouldn't be called directly, the caching service should sort this out
  override def getMultipleStudentInformation(options: GetMultipleStudentInformationOptions)(implicit t: TimingContext): Future[MultipleStudentInformationReturn] = timing.time(TimingCategories.TabulaRead) {
    Future.successful(ServiceResults.error("Not implemented"))
  }
}
