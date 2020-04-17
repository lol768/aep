package services.tabula

import java.io.{ByteArrayOutputStream, InputStream}
import java.time.Duration
import java.util.UUID

import akka.NotUsed
import akka.stream.scaladsl.Source
import play.api.mvc.MultipartFormData.FilePart
import com.google.common.io.ByteSource
import com.google.inject.ImplementedBy
import com.google.inject.name.Named
import domain.tabula.Assignment
import domain.{Assessment, StudentAssessment, tabula}
import helpers.{TrustedAppsHelper, WSRequestUriBuilder}
import javax.inject.{Inject, Singleton}
import play.api.Logger
import play.api.cache.AsyncCacheApi
import play.api.libs.json._
import play.api.libs.ws.{WSClient, WSRequest}
import play.api.mvc.MultipartFormData.FilePart
import services.tabula.TabulaAssessmentService._
import services.{AssessmentService, StudentAssessmentService, UploadedFileService}
import system.TimingCategories
import uk.ac.warwick.sso.client.trusted.{TrustedApplicationUtils, TrustedApplicationsManager}
import uk.ac.warwick.util.termdates.AcademicYear
import warwick.caching._
import warwick.core.Logging
import warwick.core.helpers.ServiceResults.Implicits._
import warwick.core.helpers.ServiceResults.{ServiceError, ServiceResult}
import warwick.core.helpers.{JavaTime, ServiceResults}
import warwick.core.system.AuditLogContext
import warwick.core.timing.{TimingContext, TimingService}
import warwick.fileuploads.UploadedFile
import warwick.objectstore.ObjectStorageService

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}
import scala.jdk.CollectionConverters._
import scala.sys.process.ProcessBuilder.Source

@ImplementedBy(classOf[CachingTabulaAssessmentService])
trait TabulaAssessmentService {
  def getAssessments(options: GetAssessmentsOptions)(implicit t: TimingContext): Future[AssessmentComponentsReturn]

  def getAssessmentGroupMembers(options: GetAssessmentGroupMembersOptions)(implicit t: TimingContext): Future[ServiceResult[Map[String, tabula.ExamMembership]]]

  def generateAssignments(assessment: Assessment)(implicit t: TimingContext, ctx: AuditLogContext): Future[ServiceResult[Assessment]]

  def generateAssignmentSubmissions(assessment: Assessment, studentAssessments: Seq[StudentAssessment])(implicit t: TimingContext, ctx: AuditLogContext): Future[ServiceResult[Assessment]]

}

object TabulaAssessmentService {
  type AssessmentComponentsReturn = ServiceResult[Seq[tabula.AssessmentComponent]]

  case class GetAssessmentsOptions(
    deptCode: String,
    withExamPapersOnly: Boolean = false,
    inUseOnly: Boolean = true,
    examProfileCode: Option[String],
  ) {
    def cacheKey = s"d:$deptCode;e:$withExamPapersOnly;i:$inUseOnly;p:$examProfileCode"
  }

  case class GetAssessmentGroupMembersOptions(
    deptCode: String,
    academicYear: AcademicYear,
    paperCodes: Seq[String]
  )

}

class CachingTabulaAssessmentService @Inject()(
  @Named("TabulaAssessmentService-NoCache") impl: TabulaAssessmentService,
  cache: AsyncCacheApi,
  timing: TimingService,
)(implicit ec: ExecutionContext) extends TabulaAssessmentService with Logging {

  private lazy val ttlStrategy: AssessmentComponentsReturn => Ttl = a => a.fold(
    _ => Ttl(soft = 10.seconds, medium = 1.minute, hard = 1.hour),
    _ => Ttl(soft = 10.minutes, medium = 1.hour, hard = 4.hours)
  )

  private lazy val wrappedCache = VariableTtlCacheHelper.async[AssessmentComponentsReturn](cache, logger, ttlStrategy, timing)

  override def getAssessments(options: GetAssessmentsOptions)(implicit t: TimingContext): Future[AssessmentComponentsReturn] =
    wrappedCache.getOrElseUpdate(options.cacheKey) {
      impl.getAssessments(options)
    }

  override def getAssessmentGroupMembers(options: GetAssessmentGroupMembersOptions)(implicit t: TimingContext): Future[ServiceResult[Map[String, tabula.ExamMembership]]] =
    impl.getAssessmentGroupMembers(options)

  override def generateAssignments(assessment: Assessment)(implicit t: TimingContext, ctx: AuditLogContext): Future[ServiceResult[Assessment]] =
    impl.generateAssignments(assessment)

  override def generateAssignmentSubmissions(assessment: Assessment, studentAssessment: Seq[StudentAssessment])(implicit t: TimingContext, ctx: AuditLogContext): Future[ServiceResult[Assessment]] =
    impl.generateAssignmentSubmissions(assessment, studentAssessment)
}

@Singleton
class TabulaAssessmentServiceImpl @Inject()(
  ws: WSClient,
  config: TabulaConfiguration,
  trustedApplicationsManager: TrustedApplicationsManager,
  tabulaHttp: TabulaHttp,
  timing: TimingService,
  assessmentService: AssessmentService,
  studentAssessmentService: StudentAssessmentService,
  uploadedFileSerive: UploadedFileService,
  objectStorageService: ObjectStorageService
)(implicit ec: ExecutionContext) extends TabulaAssessmentService with Logging {

  import tabulaHttp._

  override def getAssessments(options: GetAssessmentsOptions)(implicit t: TimingContext): Future[AssessmentComponentsReturn] = timing.time(TimingCategories.TabulaRead) {
    val url = config.getAssessmentsUrl(options.deptCode)
    val req = ws.url(url)
      .withQueryStringParameters(Seq(
        Some("withExamPapersOnly" -> options.withExamPapersOnly.toString),
        Some("inUseOnly" -> options.inUseOnly.toString),
        options.examProfileCode.map("examProfileCode" -> _),
      ).flatten: _*)

    implicit def l: Logger = logger

    doRequest(url, method = "GET", req, description = "getAssessments").successFlatMapTo { jsValue =>
      parseAndValidate(jsValue, TabulaResponseParsers.responseAssessmentComponentsReads)
    }
  }

  override def getAssessmentGroupMembers(options: GetAssessmentGroupMembersOptions)(implicit t: TimingContext): Future[ServiceResult[Map[String, tabula.ExamMembership]]] = timing.time(TimingCategories.TabulaRead) {
    val url = config.getAssessmentComponentMembersUrl(options.deptCode, options.academicYear)
    val req = ws.url(url)
      .withQueryStringParameters(
        options.paperCodes.map("paperCode" -> _): _*
      )

    implicit def l: Logger = logger

    doRequest(url, method = "GET", req, description = "getAssessmentGroupMembers").successFlatMapTo { jsValue =>
      parseAndValidate(jsValue, TabulaResponseParsers.responsePaperCodesReads)
    }
  }

  private def createAssignment(assessment: Assessment, academicYear: AcademicYear, occurrences: Set[String]): Future[ServiceResult[Assignment]] = {
    val moduleCode = assessment.moduleCode.split("-").head.toLowerCase
    val url = config.getCreateAssignmentUrl(moduleCode)

    val sitsLinks = occurrences.map(o => Json.obj(
      "moduleCode" -> assessment.moduleCode,
      "occurrence" -> o,
      "sequence" -> assessment.sequence
    ))

    // TODO - if openEnded causes problems use assessment.lastAllowedStartTime
    // note - will may need a new create assignment API that ignores validation on close time (only supports 10-4)

    val isPreviousAcademicYear = academicYear != AcademicYear.forDate(JavaTime.offsetDateTime)

    val body: JsValue = Json.obj(
      "name" -> s"${assessment.title} - ${assessment.paperCode} (AEP submissions)",
      "openEnded" -> true,
      "collectSubmissions" -> false,
      "publishFeedback" -> false,
      "automaticallySubmitToTurnitin" -> true,
      // "resitAssessment" -> true, TODO - tabula API ignores this
      "fileAttachmentLimit" -> 20, // TODO - we should teach our private Tabula submission API to ignore the limit
      "academicYear" -> academicYear.toString,
      "sitsLinks" -> sitsLinks,
      // "anonymity" -> "IDOnly" TODO - tabula API doesn't support this
    )

    val req = ws.url(url)
      .withHttpHeaders("Content-Type" -> "application/json")
      .withBody(body)

    implicit def l: Logger = logger

    doRequest(url, "POST", req, description = "createAssignment").successFlatMapTo { jsValue =>
      parseAndValidate(jsValue, TabulaResponseParsers.responseAssignmentReads)
    }
  }

  override def generateAssignments(assessment: Assessment)(implicit t: TimingContext, ctx: AuditLogContext): Future[ServiceResult[Assessment]] = timing.time(TimingCategories.TabulaWrite) {
    studentAssessmentService.byAssessmentId(assessment.id)
      .successFlatMapTo { students =>
        val byYear = students.filter(_.academicYear.isDefined).groupBy(_.academicYear.get)
        ServiceResults.futureSequence(byYear.map { case (academicYear, students) =>
          createAssignment(assessment, academicYear, students.flatMap(_.occurrence).toSet)
        }.toSeq)
      }.successFlatMapTo { assignments =>
      val allAssignments = assessment.tabulaAssignments ++ assignments.map(a => UUID.fromString(a.id))
      assessmentService.update(assessment.copy(tabulaAssignments = allAssignments), Nil)
    }
  }


  private def createSubmission(assessment: Assessment, studentAssessment: StudentAssessment, assignmentId: UUID): Future[ServiceResult[Assignment]] = {
    implicit def l: Logger = logger
    val url = config.getCreateAssignmentSubmissionUrl(assignmentId)


    val deadline = studentAssessment.startTime.flatMap { startTime =>
      assessment.duration.map { d =>
        startTime.plus(d.plus(studentAssessment.extraTimeAdjustment.getOrElse(Duration.ZERO)).plus(Assessment.uploadGraceDuration))
      }
    }

    val filePart: Seq[FilePart[ByteSource]] = studentAssessment.uploadedFiles.map { file => //file -> objectStorageService.fetch(file.id.toString)
      val source: ByteSource = new ByteSource {
        override def openStream(): InputStream = objectStorageService.fetch(file.id.toString).orNull
      }

      FilePart("files", file.fileName, Some(file.contentType), source, file.contentLength)
    }

    val req = ws.url(url)
      .withHttpHeaders("Content-Type" -> "multipart/form-data")
      .withQueryStringParameters(Seq(
        Some("universityId" -> studentAssessment.studentId),
        Some("submittedDate" -> studentAssessment.submissionTime.get.toString),
        Some("submissionDeadline" -> deadline.get.toString),
      ).flatten: _*)

    doRequest(url, "POST", req, description = "createSubmission", filePart).successFlatMapTo { jsValue =>
      parseAndValidate(jsValue, TabulaResponseParsers.responseAssignmentReads)
    }
  }

  override def generateAssignmentSubmissions(assessment: Assessment, studentAssessment: Seq[StudentAssessment])(implicit t: TimingContext, ctx: AuditLogContext): Future[ServiceResult[Assessment]] =
    //TODO change this
    timing.time(TimingCategories.TabulaWrite) {
      studentAssessmentService.byAssessmentId(assessment.id)
        .successFlatMapTo { students =>
          val byYear = students.filter(_.academicYear.isDefined).groupBy(_.academicYear.get)
          ServiceResults.futureSequence(byYear.map { case (academicYear, students) =>
            createAssignment(assessment, academicYear, students.flatMap(_.occurrence).toSet)
          }.toSeq)
        }.successFlatMapTo { assignments =>
        val allAssignments = assessment.tabulaAssignments ++ assignments.map(a => UUID.fromString(a.id))
        assessmentService.update(assessment.copy(tabulaAssignments = allAssignments), Nil)
      }
    }


}

/**
  * Common behaviour for Tabula HTTP API calls.
  */
class TabulaHttp @Inject()(
  ws: WSClient,
  config: TabulaConfiguration,
  trustedApplicationsManager: TrustedApplicationsManager,
)(implicit ec: ExecutionContext) extends Logging {

  /**
    * Make a trusted request to an URL.
    *
    * @param url         Full URL
    * @param method      HTTP method
    * @param wsRequest   Request object
    * @param description Description for logging
    * @param l           Logger, for logging
    * @return JsValue if request was trusted (but use parseAndValidate to check it was successful)
    */
  def doRequest(url: String, method: String, wsRequest: WSRequest, description: String, filePart: Seq[FilePart[ByteSource]] = Seq.empty)(implicit l: Logger): Future[ServiceResult[JsValue]] = {
    val trustedHeaders: Seq[(String, String)] = TrustedApplicationUtils.getRequestHeaders(
      trustedApplicationsManager.getCurrentApplication,
      config.usercode,
      WSRequestUriBuilder.buildUri(wsRequest).toString
    ).asScala.map(h => h.getName -> h.getValue).toSeq

    val req = wsRequest.addHttpHeaders(trustedHeaders: _*)
    val res = if(filePart.isEmpty) {
      req.execute(method)
    } else {
       req.post(
         Source(
           filePart ::List()
         )
       )
    }
    res .map { r =>
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
