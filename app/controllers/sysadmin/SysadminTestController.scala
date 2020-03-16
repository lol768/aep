package controllers.sysadmin

import controllers.BaseController
import javax.inject.{Inject, Singleton}
import play.api.data.Form
import play.api.data.Forms._
import play.api.i18n.Messages
import play.api.libs.mailer.{Attachment, Email}
import play.api.mvc.{Action, AnyContent}
import services._
import warwick.sso.{UserLookupService, Usercode}

import scala.concurrent.{ExecutionContext, Future}

object SysadminTestController {
  case class EmailFormData(to: Usercode, email: Email)

  val emailForm: Form[EmailFormData] = Form(mapping(
    "to" -> nonEmptyText.transform[Usercode](Usercode.apply, _.string),
    "email" -> mapping(
      "subject" -> nonEmptyText,
      "from" -> email,
      "to" -> ignored[Seq[String]](Nil),
      "bodyText" -> optional(nonEmptyText),
      "bodyHtml" -> ignored[Option[String]](None),
      "charset" -> ignored[Option[String]](None),
      "cc" -> ignored[Seq[String]](Nil),
      "bcc" -> ignored[Seq[String]](Nil),
      "replyTo" -> ignored[Seq[String]](Nil),
      "bounceAddress" -> ignored[Option[String]](None),
      "attachments" -> ignored[Seq[Attachment]](Nil),
      "headers" -> ignored[Seq[(String, String)]](Nil),
    )(Email.apply)(Email.unapply)
  )(EmailFormData.apply)(EmailFormData.unapply))
}

@Singleton
class SysadminTestController @Inject()(
  securityService: SecurityService,
  emailService: EmailService,
  userLookupService: UserLookupService,
)(implicit executionContext: ExecutionContext) extends BaseController {

  import SysadminTestController._
  import securityService._

  def home: Action[AnyContent] = RequireSysadmin { implicit request =>
    Ok(views.html.sysadmin.test(emailForm))
  }

  def sendEmail: Action[AnyContent] = RequireSysadmin.async { implicit request =>
    emailForm.bindFromRequest().fold(
      _ => Future.successful(BadRequest),
      data => emailService.queue(data.email, Seq(userLookupService.getUser(data.to).get)).successMap { emails =>
        redirectHome.flashing("success" -> Messages("flash.emails.queued", emails.size))
      }
    )
  }

  private val redirectHome = Redirect(controllers.sysadmin.routes.SysadminTestController.home())

}
