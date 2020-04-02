package controllers.sysadmin

import controllers.BaseController
import domain.tabula.UserType
import javax.inject.{Inject, Singleton}
import play.api.Configuration
import play.api.mvc.{Action, AnyContent}
import services.SecurityService
import services.tabula.TabulaStudentInformationService
import services.tabula.TabulaStudentInformationService.GetMultipleStudentInformationOptions
import warwick.sso.UniversityID

import scala.concurrent.ExecutionContext

@Singleton
class MasqueradeController @Inject()(
  securityService: SecurityService,
  studentInformationService: TabulaStudentInformationService,
  configuration: Configuration,
)(implicit ec: ExecutionContext) extends BaseController {

  import securityService._

  private[this] val testUserUniversityIds = configuration.get[Seq[String]]("app.testUsers")

  def masquerade: Action[AnyContent] = RequireMasquerader.async { implicit request =>
    studentInformationService.getMultipleStudentInformation(GetMultipleStudentInformationOptions(universityIDs = testUserUniversityIds.map(UniversityID.apply))).successMap { testStudents =>
      val testUsers =
        testStudents.values
          .groupBy(_.department).toSeq
          .map { case (department, deptProfiles) =>
            department -> deptProfiles.groupBy(_.userType).toSeq
              .map { case (userType, userTypeProfiles) =>
                userType -> userTypeProfiles.groupBy(_.course).toSeq
                  .map { case (route, routeProfiles) =>
                    route -> routeProfiles.toSeq.sortBy(_.universityID.string)
                  }
                  .sortBy { case (course, _) => course.map(_.code) }
              }
              .sortBy { case (userType, _) => userType == UserType.Student }
          }
          .sortBy { case (dept, _) => dept.code }

      Ok(views.html.sysadmin.masquerade(testUsers))
    }
  }
}
