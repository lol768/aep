package services.tabula

import domain.tabula
import com.google.inject.ImplementedBy
import helpers.{TrustedAppsHelper, WSRequestUriBuilder}
import javax.inject.{Inject, Singleton}
import play.api.cache.AsyncCacheApi
import play.api.libs.json.{JsPath, JsValue, JsonValidationError, Reads}
import play.api.libs.ws.{WSClient, WSRequest}
import system.TimingCategories
import uk.ac.warwick.sso.client.trusted.{TrustedApplicationUtils, TrustedApplicationsManager}
import warwick.core.helpers.ServiceResults.{ServiceError, ServiceResult}
import warwick.core.timing.{TimingContext, TimingService}
import warwick.caching._
import warwick.core.Logging
import warwick.core.helpers.ServiceResults

import scala.jdk.CollectionConverters._
import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}
import ServiceResults.Implicits._
import com.google.inject.name.Named
import TabulaAssessmentService._
import play.api.Logger
import uk.ac.warwick.util.termdates.AcademicYear

@ImplementedBy(classOf[CachingTabulaAssessmentService])
trait TabulaAssessmentService {
  def getAssessments(options: GetAssessmentsOptions)(implicit t: TimingContext): Future[Return]
  def getAssessmentGroupMembers(options: GetAssessmentGroupMembersOptions)(implicit t: TimingContext): Future[ServiceResult[Map[String, tabula.ExamMembership]]]
}

object TabulaAssessmentService {
  type Return = ServiceResult[Seq[tabula.AssessmentComponent]]

  case class GetAssessmentsOptions(
    deptCode: String,
    withExamPapersOnly: Boolean = false,
  ) {
    def cacheKey = s"d:$deptCode;e:$withExamPapersOnly"
  }

  case class GetAssessmentGroupMembersOptions(
    deptCode: String,
    academicYear: AcademicYear,
    paperCodes: Seq[String]
  )
}

class CachingTabulaAssessmentService @Inject() (
  @Named("TabulaAssessmentService-NoCache") impl: TabulaAssessmentService,
  cache: AsyncCacheApi,
  timing: TimingService,
)(implicit ec: ExecutionContext) extends TabulaAssessmentService with Logging {

  private lazy val ttlStrategy: Return => Ttl = a => a.fold(
    _ => Ttl(soft = 10.seconds, medium = 1.minute, hard = 1.hour),
    _ => Ttl(soft = 1.hour, medium = 1.day, hard = 7.days)
  )

  private lazy val wrappedCache = VariableTtlCacheHelper.async[Return](cache, logger, ttlStrategy, timing)

  override def getAssessments(options: GetAssessmentsOptions)(implicit t: TimingContext): Future[Return] = timing.time(TimingCategories.TabulaRead) {
    wrappedCache.getOrElseUpdate(options.cacheKey) {
      impl.getAssessments(options)
    }
  }

  override def getAssessmentGroupMembers(options: GetAssessmentGroupMembersOptions)(implicit t: TimingContext): Future[ServiceResult[Map[String, tabula.ExamMembership]]] = impl.getAssessmentGroupMembers(options)

}

@Singleton
class TabulaAssessmentServiceImpl @Inject() (
  ws: WSClient,
  config: TabulaConfiguration,
  trustedApplicationsManager: TrustedApplicationsManager,
  tabulaHttp: TabulaHttp,
)(implicit ec: ExecutionContext) extends TabulaAssessmentService with Logging {

  import tabulaHttp._

  override def getAssessments(options: GetAssessmentsOptions)(implicit t: TimingContext): Future[Return] = {
    val url = config.getAssessmentsUrl(options.deptCode)
    val req = ws.url(url)
      .withQueryStringParameters(
        "withExamPapersOnly" -> options.withExamPapersOnly.toString
      )
    implicit def l: Logger = logger

    doGet(url, req, description = "getAssessments").successFlatMapTo { jsValue =>
      parseAndValidate(jsValue, TabulaResponseParsers.responseAssessmentComponentsReads)
    }
  }

  override def getAssessmentGroupMembers(options: GetAssessmentGroupMembersOptions)(implicit t: TimingContext): Future[ServiceResult[Map[String, tabula.ExamMembership]]] = {
    val url = config.getAssessmentComponentMembersUrl(options.deptCode, options.academicYear)
    val req = ws.url(url)
      .withQueryStringParameters(
        options.paperCodes.map("paperCode" -> _): _*
      )
    implicit def l: Logger = logger

    doGet(url, req, description = "getAssessmentGroupMembers").successFlatMapTo { jsValue =>
      parseAndValidate(jsValue, TabulaResponseParsers.responsePaperCodesReads)
    }
  }

}

/**
  * Common behaviour for Tabula HTTP API calls.
  */
class TabulaHttp @Inject() (
  ws: WSClient,
  config: TabulaConfiguration,
  trustedApplicationsManager: TrustedApplicationsManager,
)(implicit ec: ExecutionContext) extends Logging {

  /**
    * Make a trusted GET request to an URL.
    *
    * @param url Full URL
    * @param wsRequest Request object
    * @param description Description for logging
    * @param l Logger, for logging
    * @return JsValue if request was trusted (but use parseAndValidate to check it was successful)
    */
  def doGet(url: String, wsRequest: WSRequest, description: String)(implicit l: Logger): Future[ServiceResult[JsValue]] = {
    val trustedHeaders: Seq[(String, String)] = TrustedApplicationUtils.getRequestHeaders(
      trustedApplicationsManager.getCurrentApplication,
      config.usercode,
      WSRequestUriBuilder.buildUri(wsRequest).toString
    ).asScala.map(h => h.getName -> h.getValue).toSeq

    wsRequest.addHttpHeaders(trustedHeaders: _*)
      .get()
      .map { r =>
        try {
          Right(TrustedAppsHelper.validateResponse(url, r).json)
        } catch {
          case ex: Exception =>
            val msg = s"Trustedapps error in Tabula $description"
            logger.error(msg, ex)
            ServiceResults.exceptionToServiceResult(ex, Some(msg))
        }

      }
  }

  def parseAndValidate[A](jsValue: JsValue, reads: Reads[A])(implicit l: Logger): Future[ServiceResult[A]] = Future.successful {
    TabulaResponseParsers.validateAPIResponse(jsValue, reads).fold(
      errors => handleValidationError[A](jsValue, errors),
      components => Right(components)
    )
  }

  private def handleValidationError[A](json: JsValue, errors: scala.collection.Seq[(JsPath, scala.collection.Seq[JsonValidationError])])(implicit l: Logger): ServiceResult[A] = {
    val serviceErrors = errors.map { case (path, validationErrors) =>
      ServiceError(s"$path: ${validationErrors.map(_.message).mkString(", ")}")
    }
    logger.error(s"Could not parse JSON result from Tabula:\n$json\n${serviceErrors.map(_.message).mkString("\n")}")
    Left(serviceErrors.toList)
  }
}
