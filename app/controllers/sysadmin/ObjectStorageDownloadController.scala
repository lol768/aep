package controllers.sysadmin

import java.util.UUID

import controllers.BaseController
import controllers.sysadmin.ObjectStorageDownloadController._
import helpers.StringUtils._
import javax.inject.{Inject, Singleton}
import play.api.data.Form
import play.api.data.Forms._
import play.api.mvc.{Action, AnyContent}
import services.{SecurityService, UploadedFileService}
import warwick.fileuploads.UploadedFileControllerHelper

import scala.concurrent.{ExecutionContext, Future}

object ObjectStorageDownloadController {
  val idForm: Form[Seq[UUID]] = Form(single(
    "ids" -> text.transform[Seq[UUID]](_.split("\\s+").toSeq.filter(_.hasText).map(UUID.fromString), _.map(_.toString).mkString("\n"))
  ))
}

@Singleton
class ObjectStorageDownloadController @Inject()(
  security: SecurityService,
  uploadedFileService: UploadedFileService,
  uploadedFileControllerHelper: UploadedFileControllerHelper,
)(implicit ec: ExecutionContext) extends BaseController {
  import security._

  def form(): Action[AnyContent] = RequireSysadmin { implicit request =>
    Ok(views.html.sysadmin.objectStorage(Nil, idForm))
  }

  def results(): Action[AnyContent] = RequireSysadmin.async { implicit request =>
    idForm.bindFromRequest().fold(
      formWithErrors => Future.successful(Ok(views.html.sysadmin.objectStorage(Nil, formWithErrors))),
      ids => uploadedFileService.get(ids).successMap { files =>
        Ok(views.html.sysadmin.objectStorage(files, idForm))
      }
    )
  }

  def downloadFile(id: UUID): Action[AnyContent] = RequireSysadmin.async { implicit request =>
    uploadedFileService.get(id).successFlatMap(uploadedFileControllerHelper.serveFile)
  }
}
