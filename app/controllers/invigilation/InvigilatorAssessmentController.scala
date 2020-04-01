package controllers.invigilation

import java.util.UUID

import controllers.BaseController
import javax.inject.Inject
import play.api.mvc.{Action, AnyContent}
import services.refiners.ActionRefiners
import services.{AssessmentService, ReportingService, SecurityService, StudentAssessmentService}
import warwick.sso.{Name, UserLookupService, Usercode}

import scala.concurrent.ExecutionContext

class InvigilatorAssessmentController @Inject()(
  security: SecurityService,
  actionRefiners: ActionRefiners,
  assessmentService: AssessmentService,
  reportingService: ReportingService,
  userLookup: UserLookupService,
  studentAssessmentService: StudentAssessmentService,
)(implicit ec: ExecutionContext) extends BaseController {

  import security._

  def view(assessmentId: UUID): Action[AnyContent] = InvigilatorAsseessmentAction(assessmentId).async { implicit req =>

    val assessment = req.assessment
    // inflate dept code

    studentAssessmentService.byAssessmentId(assessmentId).successMap { students =>
      Ok(views.html.invigilation.assessment(
        assessment = assessment,
        userNames = assessment.invigilators
          .map(userLookup.getUser)
          .flatMap(_.fold(
            _ => None,
            user => Some(user)
          ))
          .filter(_.isFound)
          .filter(_.name.full.nonEmpty)
          .map(user => user.usercode -> user.name.full.get)
          .toMap,
        students = userLookup
          .getUsers(students.map(_.studentId))
          .getOrElse(Map.empty)
          .map {
            case (_, user) => user.usercode -> user.name.full.getOrElse(user.usercode.string)
          }
      ))

    }
  }

}

