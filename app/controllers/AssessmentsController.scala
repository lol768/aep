package controllers

import java.time.Duration

import domain.Sitting
import javax.inject.{Inject, Singleton}
import play.api.libs.json.{JsValue, Json, Writes}
import play.api.mvc.{Action, AnyContent, RequestHeader}
import services.{SecurityService, StudentAssessmentService, TimingInfoService}

import scala.concurrent.{ExecutionContext, Future}

object AssessmentsController {
  def toJson(assessments: Seq[Sitting], latePeriodAllowance: Duration)(implicit request: RequestHeader): JsValue = {
    // We only want to include basic information here
    val sittingWrites: Writes[Sitting] = sitting => Json.obj(
      "id" -> sitting.studentAssessment.id,
      "url" -> controllers.routes.AssessmentController.view(sitting.assessment.id).absoluteURL(),
      "title" -> sitting.assessment.title,
      "paperCode" -> sitting.assessment.paperCode,
      "section" -> sitting.assessment.section,
      "description" -> sitting.assessment.brief.text,
      "durationStyle" -> sitting.assessment.durationStyle,
      "duration" -> sitting.assessment.duration,
      "startTime" -> sitting.assessment.startTime,
      "lastAllowedStartTime" -> sitting.lastAllowedStartTimeForStudent(latePeriodAllowance),
      "startedTime" -> sitting.studentAssessment.startTime,
      "finalisedTime" -> sitting.finalisedTime(latePeriodAllowance),
      "finalised" -> sitting.finalised(latePeriodAllowance)
    )

    implicit val seqSittingWrites: Writes[Seq[Sitting]] = Writes.seq(sittingWrites)
    implicit val responseWrites: Writes[API.Response[Seq[Sitting]]] = API.Response.writes[Seq[Sitting]]

    Json.toJson(API.Success(data = assessments))
  }
}

@Singleton
class AssessmentsController @Inject()(
  security: SecurityService,
  studentAssessmentService: StudentAssessmentService,
  timingInfo: TimingInfoService,
)(implicit ec: ExecutionContext) extends BaseController {
  import security._

  def index: Action[AnyContent] = SigninRequiredAction.async { implicit request =>
    request.user.flatMap(_.universityId).map { universityId =>
      studentAssessmentService.byUniversityId(universityId).successMap { assessments =>
        render {
          case Accepts.Json() => Ok(AssessmentsController.toJson(assessments, timingInfo.lateSubmissionPeriod))
          case _ => Ok(views.html.exams.index(assessments, timingInfo))
        }
      }
    }.getOrElse {
      Future.successful(Ok(views.html.exams.noUniversityId()))
    }
  }
}
