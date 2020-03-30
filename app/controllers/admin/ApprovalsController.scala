package controllers.admin

import java.util.UUID

import controllers.BaseController
import domain.Assessment.State
import javax.inject.{Inject, Singleton}
import play.api.data.Form
import play.api.data.Forms._
import play.api.i18n.Messages
import play.api.mvc.{Action, AnyContent}
import services.{AssessmentService, SecurityService, UploadedFileService}
import warwick.fileuploads.UploadedFileControllerHelper

import scala.concurrent.{ExecutionContext, Future}

object ApprovalsController {

  case class ApprovalFormData(
    approved: Boolean
  )

  val form: Form[ApprovalFormData] = Form(mapping(
    "approved" -> boolean.verifying("error.approval.must-check-box", _ == true),
  )(ApprovalFormData.apply)(ApprovalFormData.unapply))
}

@Singleton
class ApprovalsController @Inject()(
  security: SecurityService,
  assessmentService: AssessmentService,
  uploadedFileControllerHelper: UploadedFileControllerHelper,
  uploadedFileService: UploadedFileService
)(implicit ec: ExecutionContext) extends BaseController {

  import ApprovalsController._
  import security._

  def index: Action[AnyContent] = RequireApprover.async { implicit request =>
    assessmentService.findByStates(Seq(State.Submitted)).successMap { assessments =>
      Ok(views.html.admin.approvals.index(assessments))
    }
  }

  def show(id: UUID): Action[AnyContent] = RequireApprover.async { implicit request =>
    assessmentService.get(id).successMap { assessment =>
      Ok(views.html.admin.approvals.show(assessment, form))
    }
  }

  def update(id: UUID): Action[AnyContent] = RequireApprover.async { implicit request =>
    assessmentService.get(id).successFlatMap { assessment =>
      form.bindFromRequest().fold(
        formWithErrors => Future.successful(Ok(views.html.admin.approvals.show(assessment, formWithErrors))),
        data => {
          assessmentService.update(assessment.copy(state = State.Approved), files = Nil).successMap { _ =>
            Redirect(routes.ApprovalsController.index()).flashing("success" -> Messages("flash.assessment.approved"))
          }
        })
    }
  }

  def getFile(assessmentId: UUID, fileId: UUID): Action[AnyContent] = RequireApprover.async { implicit request =>
    assessmentService.get(assessmentId).successFlatMap { assessment =>
      assessment.brief.files.find(_.id == fileId)
        .map(uploadedFileControllerHelper.serveFile)
        .getOrElse(Future.successful(NotFound("File not found")))
    }
  }
}
