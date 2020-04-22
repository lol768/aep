package services.tabula

import java.io.ByteArrayInputStream
import java.util.UUID

import akka.stream.IOResult
import akka.stream.scaladsl.{Source, StreamConverters}
import akka.util.ByteString
import com.google.inject.ImplementedBy
import com.google.inject.name.Named
import domain.tabula.{Submission, TabulaAssignment}
import domain.{Assessment, SittingMetadata, StudentAssessment, tabula}
import helpers.{TrustedAppsHelper, WSRequestUriBuilder}
import javax.inject.{Inject, Singleton}
import play.api.Logger
import play.api.cache.AsyncCacheApi
import play.api.libs.json._
import play.api.libs.ws.{WSClient, WSRequest}
import play.api.mvc.MultipartFormData.FilePart
import services.tabula.TabulaAssessmentService._
import services.{AssessmentService, StudentAssessmentService}
import system.TimingCategories
import uk.ac.warwick.sso.client.trusted.{TrustedApplicationUtils, TrustedApplicationsManager}
import uk.ac.warwick.util.termdates.AcademicYear
import views.tags.formatDate
import warwick.caching._
import warwick.core.Logging
import warwick.core.helpers.ServiceResults.Implicits._
import warwick.core.helpers.ServiceResults.{ServiceError, ServiceResult}
import warwick.core.helpers.{JavaTime, ServiceResults}
import warwick.core.system.AuditLogContext
import warwick.core.timing.{TimingContext, TimingService}
import warwick.objectstore.ObjectStorageService

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}
import scala.jdk.CollectionConverters._

@ImplementedBy(classOf[CachingTabulaAssessmentService])
trait TabulaAssessmentService {
  def getAssessments(options: GetAssessmentsOptions)(implicit t: TimingContext): Future[AssessmentComponentsReturn]

  def getAssessmentGroupMembers(options: GetAssessmentGroupMembersOptions)(implicit t: TimingContext): Future[ServiceResult[Map[String, tabula.ExamMembership]]]

  def generateAssignments(assessment: Assessment)(implicit t: TimingContext, ctx: AuditLogContext): Future[ServiceResult[Assessment]]

  def generateAssignmentSubmissions(assessment: Assessment, studentAssessment: Option[StudentAssessment])(implicit t: TimingContext, ctx: AuditLogContext): Future[ServiceResult[Seq[StudentAssessment]]]

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

  override def generateAssignmentSubmissions(assessment: Assessment, studentAssessment: Option[StudentAssessment])(implicit t: TimingContext, ctx: AuditLogContext): Future[ServiceResult[Seq[StudentAssessment]]] =
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
  tabulaAssignmentService: TabulaAssignmentService,
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

  private def createAssignment(assessment: Assessment, academicYear: AcademicYear, occurrences: Set[String])(implicit ctx: AuditLogContext): Future[ServiceResult[TabulaAssignment]] = {
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
      "hiddenFromStudents" -> true,
      "publishFeedback" -> false,
      "automaticallySubmitToTurnitin" -> true,
      "resitAssessment" -> isPreviousAcademicYear,
      "fileAttachmentLimit" -> 20, // TODO - TAB-8285 we should teach our private Tabula submission API to ignore the limit
      "academicYear" -> academicYear.toString,
      "sitsLinks" -> sitsLinks,
      "anonymity" -> "IDOnly"
    )

    val req = ws.url(url)
      .withHttpHeaders("Content-Type" -> "application/json")
      .withBody(body)

    implicit def l: Logger = logger

    doRequest(url, "POST", req, description = "createAssignment").successFlatMapTo { jsValue =>
      parseAndValidate(jsValue, TabulaResponseParsers.responseAssignmentReads)
    }.successFlatMapTo { assignment =>
      tabulaAssignmentService.save(TabulaAssignment(
        UUID.fromString(assignment.id),
        assignment.name,
        assignment.academicYear
      ))
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
        val allAssignments = assessment.tabulaAssignments ++ assignments.map(a => a.id)
        assessmentService.update(assessment.copy(tabulaAssignments = allAssignments), Nil)
      }
  }

  private def createSubmission(sitting: SittingMetadata, assignmentId: UUID): Future[ServiceResult[Submission]] = {
    implicit def l: Logger = logger

    //Point this url to the requestbin in case want to cross check what curl request was generated(Handy)
    val url = config.getCreateAssignmentSubmissionUrl(assignmentId)

    val fileParts = sitting.studentAssessment.uploadedFiles.map { file =>
      FilePart("attachments", file.fileName, Some(file.contentType), StreamConverters.fromInputStream(() => objectStorageService.fetch(file.id.toString).orNull))
    }
    //Required for API though it is blank data
    val emptyJsonInputStream = new ByteArrayInputStream(Json.obj().toString().getBytes("UTF-8"))
    val submissionJsonFile = FilePart("submission", "submission.json", Some("application/json"), StreamConverters.fromInputStream(() => emptyJsonInputStream))
    val data: Seq[FilePart[Source[ByteString, Future[IOResult]]]] = fileParts :+ submissionJsonFile
    val req = ws.url(url)
      .withQueryStringParameters(Seq(
        Some("universityId" -> sitting.studentAssessment.studentId.string),
        Some("submittedDate" -> formatDate.tabulaISODateTime(sitting.studentAssessment.submissionTime.get)), //Explicitly decided against using Sitting.finalisedTime as some may not finalise
        Some("submissionDeadline" -> formatDate.tabulaISODateTime(sitting.onTimeEnd.get)),
      ).flatten: _*)
      .withBody(Source(data))
    doRequest(url, "POST", req, description = "createSubmission").successFlatMapTo { jsValue =>
      parseAndValidate(jsValue, TabulaResponseParsers.responseSubmissionReads)
    }
  }

  override def generateAssignmentSubmissions(assessment: Assessment, studentAssessment: Option[StudentAssessment])(implicit t: TimingContext, ctx: AuditLogContext): Future[ServiceResult[Seq[StudentAssessment]]] =
    timing.time(TimingCategories.TabulaWrite) {
      def createSubmissions(studentAssessments: Seq[StudentAssessment]): Future[ServiceResult[Seq[StudentAssessment]]] = {
        tabulaAssignmentService.getByAssessment(assessment)
          .successFlatMapTo { tabulaAssignments =>
            ServiceResults.futureSequence(studentAssessments.filter(s => s.tabulaSubmissionId.isEmpty && !s.uploadedFiles.isEmpty && s.academicYear.isDefined).map { sa =>
              createSubmission(SittingMetadata(sa, assessment.asAssessmentMetadata), tabulaAssignments.filter(_.academicYear == sa.academicYear.get).head.id).successFlatMapTo { submission =>
                studentAssessmentService.upsert(sa.copy(tabulaSubmissionId = Some(UUID.fromString(submission.id))))
              }
            })
          }
      }

      //Only allow tabula upload submission if not already done in the past and check we pick student assessments who actually sat for exams (would have uploaded some file in case some didn't finalise)
      studentAssessment match {
        case Some(studentAssessment) => createSubmissions(Seq(studentAssessment))
        case _ => studentAssessmentService.byAssessmentId(assessment.id).successFlatMapTo { studentAssessments => createSubmissions(studentAssessments) }
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
  def doRequest(url: String, method: String, wsRequest: WSRequest, description: String)(implicit l: Logger): Future[ServiceResult[JsValue]] = {
    val trustedHeaders: Seq[(String, String)] = TrustedApplicationUtils.getRequestHeaders(
      trustedApplicationsManager.getCurrentApplication,
      config.usercode,
      WSRequestUriBuilder.buildUri(wsRequest).toString
    ).asScala.map(h => h.getName -> h.getValue).toSeq

    wsRequest.addHttpHeaders(trustedHeaders: _*)
      .execute(method)
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
