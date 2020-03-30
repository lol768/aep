package controllers.sysadmin

import controllers.BaseController
import javax.inject.{Inject, Singleton}
import play.api.data.Form
import play.api.data.Forms._
import play.api.i18n.Messages
import play.api.mvc.{Action, AnyContent}
import services.{DataGenerationService, SecurityService}

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class DummyDataGenerationController @Inject()(
  securityService: SecurityService,
  dataGenerationService: DataGenerationService,
) (implicit ec: ExecutionContext) extends BaseController {

  import securityService._

  private val form: Form[DataGenerationFormData] = Form(mapping(
    "howMany" -> number.verifying("Asking for 0 or fewer is stupid", i => i > 0),
    "withStudentAssessments" -> boolean
  )(DataGenerationFormData.apply)(DataGenerationFormData.unapply))

  private val defaultFormData = DataGenerationFormData(
    howMany = 5,
    withStudentAssessments = true
  )

  def showForm: Action[AnyContent] = RequireSysadmin { implicit request =>
    Ok(views.html.sysadmin.dummyDataGenerator(form.fill(defaultFormData)))
  }

  def submitForm: Action[AnyContent] = RequireSysadmin.async { implicit request =>
    def success(data: DataGenerationFormData) = {
      if (data.withStudentAssessments) {
        dataGenerationService.putRandomAssessmentsWithStudentAssessmentsInDatabase(data.howMany).successMap { _ =>
          Redirect(controllers.sysadmin.routes.DummyDataGenerationController.showForm())
            .flashing("success" -> Messages("flash.dataGeneration.randomAssessmentsWithStudentAssessments", data.howMany))
        }
      } else {
        dataGenerationService.putRandomAssessmentsInDatabase(data.howMany).successMap { _ =>
          Redirect(controllers.sysadmin.routes.DummyDataGenerationController.showForm())
            .flashing("success" -> Messages("flash.dataGeneration.randomAssessments", data.howMany))
        }
      }
    }

    def failure(formWithErrors: Form[DataGenerationFormData]) =
      Future.successful{
        BadRequest(views.html.sysadmin.dummyDataGenerator(formWithErrors))
      }

    form.bindFromRequest().fold(failure, success)
  }
}

case class DataGenerationFormData(
  howMany: Int,
  withStudentAssessments: Boolean,
)
