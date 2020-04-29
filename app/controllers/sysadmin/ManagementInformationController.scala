package controllers.sysadmin

import controllers.BaseController
import domain.Assessment.Platform
import domain.BaseSitting.SubmissionState
import domain.{Assessment, AssessmentMetadata, DepartmentCode, Sitting}
import javax.inject.{Inject, Singleton}
import play.api.Configuration
import play.api.mvc.{Action, AnyContent}
import services.{AssessmentService, SecurityService, StudentAssessmentService}
import warwick.core.helpers.{JavaTime, ServiceResults}
import helpers.StringUtils._

import scala.concurrent.ExecutionContext

object ManagementInformationController {
  case class AssessmentSetupMetrics(
    examProfileCode: String,
    assessmentCount: Int,
    hasStudents: Int,
    hasPlatform: Int,
    hasDuration: Int,
    hasURLOrIsAEP: Int,
    hasDescription: Int,
    hasInvigilators: Int,
  )

  def metrics(examProfileCode: String, assessments: Seq[(AssessmentMetadata, Int)]): AssessmentSetupMetrics =
    AssessmentSetupMetrics(
      examProfileCode,
      assessmentCount = assessments.size,
      hasStudents = assessments.count { case (_, students) => students > 0 },
      hasPlatform = assessments.count { case (a, _) => a.platform.nonEmpty },
      hasDuration = assessments.count { case (a, _) => a.platform.nonEmpty },
      hasURLOrIsAEP = assessments.count { case (a, _) => a.briefWithoutFiles.urls.view.filterKeys(_.requiresUrl).values.forall(_.hasText) },
      hasDescription = assessments.count { case (a, _) => a.briefWithoutFiles.text.exists(_.hasText) },
      hasInvigilators = assessments.count { case (a, _) => a.invigilators.nonEmpty }
    )

  case class AssessmentParticipationMetricValues(
    total: Int,
    started: Int,
    submitted: Int,
    wasLate: Int,
    explicitlyFinalised: Int,
  )

  def participationMetricValues(sittings: Iterable[Sitting]): AssessmentParticipationMetricValues =
    AssessmentParticipationMetricValues(
      total = sittings.size,
      started = sittings.count(_.started),
      submitted = sittings.count(_.studentAssessment.uploadedFiles.nonEmpty),
      wasLate = sittings.count(_.getSubmissionState == SubmissionState.Late),
      explicitlyFinalised = sittings.count(_.explicitlyFinalised)
    )

  case class AssessmentParticipationMetrics(
    overall: AssessmentParticipationMetricValues,
    byDepartmentCode: Seq[(DepartmentCode, AssessmentParticipationMetricValues)],
    byExamProfileCode: Seq[(String, AssessmentParticipationMetricValues)],
  )

  def participationMetrics(assessments: Seq[(Assessment, Set[Sitting])]): AssessmentParticipationMetrics =
    AssessmentParticipationMetrics(
      overall = participationMetricValues(assessments.flatMap(_._2)),
      byDepartmentCode =
        assessments.groupBy(_._1.departmentCode)
          .map { case (d, a) => d -> participationMetricValues(a.flatMap(_._2)) }
          .toSeq
          .sortBy(_._1.lowerCase),
      byExamProfileCode =
        assessments.groupBy(_._1.examProfileCode)
          .map { case (d, a) => d -> participationMetricValues(a.flatMap(_._2)) }
          .toSeq,
    )
}

@Singleton
class ManagementInformationController @Inject()(
  security: SecurityService,
  assessmentService: AssessmentService,
  configuration: Configuration,
)(implicit ec: ExecutionContext) extends BaseController {

  import security._

  private[this] lazy val examProfileCodes = configuration.get[Seq[String]]("tabula.examProfileCodes")

  def home(): Action[AnyContent] = RequireSysadmin.async { implicit request =>
    ServiceResults.zip(
      // Assessments starting today
      assessmentService.getAssessmentsStartingWithStudentCount(JavaTime.localDate),

      // Assessments starting tomorrow
      assessmentService.getAssessmentsStartingWithStudentCount(JavaTime.localDate.plusDays(1)),

      // Assessments starting the day after that
      assessmentService.getAssessmentsStartingWithStudentCount(JavaTime.localDate.plusDays(2)),

      // All assessments for each exam profile, so we can see how "set up" they are
      ServiceResults.futureSequence(examProfileCodes.map(assessmentService.listForExamProfileCodeWithStudentCount)).successMapTo { assessments =>
        examProfileCodes.zipWithIndex.map { case (examProfileCode, index) =>
          ManagementInformationController.metrics(examProfileCode, assessments(index))
        }
      },

      // All assessments that completed before now (so we can get information about how many started, uploaded, finalised)
      assessmentService.getFinishedAssessmentsWithSittings().successMapTo { assessments =>
        // Group AEP assessments and non-AEP assessments separately for student participation metrics
        val (aep, nonAEP) = assessments.partition(_._1.platform.contains(Platform.OnlineExams))

        (ManagementInformationController.participationMetrics(aep), ManagementInformationController.participationMetrics(nonAEP))
      }
    ).successMap { case (startingToday, startingTomorrow, startingInTwoDays, metricsByExamProfile, (aepParticipationMetrics, nonAEPParticipationMetrics)) =>
      Ok(views.html.sysadmin.managementInformation(
        startingToday,
        startingTomorrow,
        startingInTwoDays,
        metricsByExamProfile,
        aepParticipationMetrics,
        nonAEPParticipationMetrics,
      ))
    }
  }

}
