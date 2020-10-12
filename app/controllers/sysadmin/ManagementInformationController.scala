package controllers.sysadmin

import controllers.BaseController
import domain.Assessment.Platform
import domain.BaseSitting.SubmissionState
import domain.{Assessment, AssessmentMetadata, DepartmentCode, Sitting}
import helpers.StringUtils._
import javax.inject.{Inject, Singleton}
import play.api.Configuration
import play.api.mvc.{Action, AnyContent}
import services.tabula.TabulaDepartmentService
import services.{AssessmentService, SecurityService}
import warwick.core.helpers.{JavaTime, ServiceResults}

import scala.concurrent.ExecutionContext

object ManagementInformationController {
  case class AssessmentSetupMetricsValues(
    assessmentCount: Int,
    hasStudents: Int,
    hasPlatform: Int,
    hasDuration: Int,
    hasURLOrIsAEP: Int,
    hasFiles: Int,
    hasDescription: Int,
    hasInvigilators: Int,
  )
  object AssessmentSetupMetricsValues {
    def apply(assessments: Seq[(AssessmentMetadata, Int)]): AssessmentSetupMetricsValues =
      AssessmentSetupMetricsValues(
        assessmentCount = assessments.size,
        hasStudents = assessments.count { case (_, students) => students > 0 },
        hasPlatform = assessments.count { case (a, _) => a.platform.nonEmpty },
        hasDuration = assessments.count { case (a, _) => a.duration.nonEmpty },
        hasURLOrIsAEP = assessments.count { case (a, _) => a.platform.nonEmpty && a.briefWithoutFiles.urls.view.filterKeys(_.requiresUrl).values.forall(_.hasText) },
        hasFiles = assessments.count { case (a, _) => a.briefWithoutFiles.files.nonEmpty },
        hasDescription = assessments.count { case (a, _) => a.briefWithoutFiles.text.exists(_.hasText) },
        hasInvigilators = assessments.count { case (a, _) => a.invigilators.nonEmpty }
      )
  }

  case class AssessmentSetupMetrics(
    examProfileCode: String,
    overall: AssessmentSetupMetricsValues,
    byDepartmentCode: Seq[(DepartmentCode, AssessmentSetupMetricsValues)],
  )

  def metrics(examProfileCode: String, assessments: Seq[(AssessmentMetadata, Int)]): AssessmentSetupMetrics =
    AssessmentSetupMetrics(
      examProfileCode,
      overall = AssessmentSetupMetricsValues(assessments),
      byDepartmentCode =
        assessments.groupBy(_._1.departmentCode)
          .map { case (d, a) => d -> AssessmentSetupMetricsValues(a) }
          .toSeq
          .sortBy(_._1.lowerCase),
    )

  case class AssessmentParticipationMetricValues(
    total: Int,
    started: Int,
    submitted: Int,
    wasLate: Int,
    explicitlyFinalised: Int,
  )
  object AssessmentParticipationMetricValues {
    def apply(sittings: Iterable[Sitting]): AssessmentParticipationMetricValues =
      AssessmentParticipationMetricValues(
        total = sittings.size,
        started = sittings.count(_.started),
        submitted = sittings.count(_.studentAssessment.uploadedFiles.nonEmpty),
        wasLate = sittings.count(_.getSubmissionState == SubmissionState.Late),
        explicitlyFinalised = sittings.count(_.explicitlyFinalised)
      )
  }

  case class AssessmentParticipationMetrics(
    overall: AssessmentParticipationMetricValues,
    byDepartmentCode: Seq[(DepartmentCode, AssessmentParticipationMetricValues)],
    byExamProfileCode: Seq[(String, AssessmentParticipationMetricValues)],
  )

  def participationMetrics(assessments: Seq[(Assessment, Set[Sitting])]): AssessmentParticipationMetrics =
    AssessmentParticipationMetrics(
      overall = AssessmentParticipationMetricValues(assessments.flatMap(_._2)),
      byDepartmentCode =
        assessments.groupBy(_._1.departmentCode)
          .map { case (d, a) => d -> AssessmentParticipationMetricValues(a.flatMap(_._2)) }
          .toSeq
          .sortBy(_._1.lowerCase),
      byExamProfileCode =
        assessments.groupBy(_._1.examProfileCode)
          .map { case (d, a) => d -> AssessmentParticipationMetricValues(a.flatMap(_._2)) }
          .toSeq,
    )
}

@Singleton
class ManagementInformationController @Inject()(
  security: SecurityService,
  assessmentService: AssessmentService,
  departmentService: TabulaDepartmentService,
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
      assessmentService.getFinishedAssessmentsWithSittings(department = None, importedOnly = true).successMapTo { assessments =>
        // Group AEP assessments and non-AEP assessments separately for student participation metrics
        val (aep, nonAEP) = assessments.partition(_._1.platform.contains(Platform.OnlineExams))

        (ManagementInformationController.participationMetrics(aep), ManagementInformationController.participationMetrics(nonAEP))
      },

      departmentService.getDepartments(),
    ).successMap { case (startingToday, startingTomorrow, startingInTwoDays, metricsByExamProfile, (aepParticipationMetrics, nonAEPParticipationMetrics), departments) =>
      Ok(views.html.sysadmin.managementInformation(
        startingToday,
        startingTomorrow,
        startingInTwoDays,
        metricsByExamProfile,
        aepParticipationMetrics,
        nonAEPParticipationMetrics,
        departments.map(d => DepartmentCode(d.code) -> d).toMap
      ))
    }
  }

}
