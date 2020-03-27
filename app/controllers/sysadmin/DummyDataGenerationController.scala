package controllers.sysadmin

import controllers.BaseController
import javax.inject.{Inject, Singleton}
import play.api.data.Form
import play.api.data.Forms._
import play.api.mvc.{Action, AnyContent}
import services.{DataGenerationService, SecurityService}

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class DummyDataGenerationController @Inject()(
  securityService: SecurityService,
  dataGenerationService: DataGenerationService,
) (implicit ec: ExecutionContext) extends BaseController {

  import securityService._

  val form: Form[DataGenerationFormData] = Form(mapping(
    "howMany" -> number.verifying("Asking for 0 or fewer is stupid", i => i > 0)
  )(DataGenerationFormData.apply)(DataGenerationFormData.unapply))

  def showForm: Action[AnyContent] = RequireSysadmin { implicit request =>
    Ok(views.html.sysadmin.dummyDataGenerator(form))
  }

  def submitForm: Action[AnyContent] = RequireSysadmin.async { implicit request =>
    def success(data: DataGenerationFormData) = {
      dataGenerationService.putRandomAssessmentsInDatabase(data.howMany).successMap { _ =>
        Redirect(controllers.sysadmin.routes.DummyDataGenerationController.showForm())
          .flashing("success" -> s"Added ${data.howMany} random assessments to the database")
      }
    }

    def failure(formWithErrors: Form[DataGenerationFormData]) =
      Future.successful{
        Redirect(controllers.sysadmin.routes.DummyDataGenerationController.showForm())
          .flashing("error" -> "Oh noes!")
      }

    form.bindFromRequest().fold(failure, success)
  }
}

case class DataGenerationFormData(
  howMany: Int
)
