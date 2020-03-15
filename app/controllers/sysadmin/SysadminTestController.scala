package controllers.sysadmin

import controllers.BaseController
import javax.inject.{Inject, Singleton}
import play.api.data.Form
import play.api.data.Forms._
import play.api.i18n.Messages
import play.api.libs.mailer.{Attachment, Email}
import play.api.mvc.{Action, AnyContent}
import services._
import helpers.StringUtils._
import org.quartz.Scheduler
import uk.ac.warwick.util.mywarwick.MyWarwickService
import uk.ac.warwick.util.mywarwick.model.request.Activity
import warwick.sso.{GroupName, UserLookupService, Usercode}

import scala.jdk.CollectionConverters._
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

  case class MyWarwickFormData(
    user: Option[Usercode],
    group: Option[GroupName],
    title: String,
    url: String,
    text: String,
    activityType: String,
    alert: Boolean
  ) {
    def toActivity: Activity = new Activity(
      user.map(_.string).toSet.asJava,
      group.map(_.string).toSet.asJava,
      title,
      url,
      text,
      activityType
    )
  }

  val myWarwickForm: Form[MyWarwickFormData] = Form(mapping(
    "user" -> text.transform[Option[Usercode]](_.maybeText.map(Usercode.apply), _.map(_.string).getOrElse("")),
    "group" -> text.transform[Option[GroupName]](_.maybeText.map(GroupName.apply), _.map(_.string).getOrElse("")),
    "title" -> nonEmptyText,
    "url" -> text,
    "text" -> text,
    "activityType" -> nonEmptyText,
    "alert" -> boolean
  )(MyWarwickFormData.apply)(MyWarwickFormData.unapply))
}

@Singleton
class SysadminTestController @Inject()(
  securityService: SecurityService,
  emailService: EmailService,
  userLookupService: UserLookupService,
  myWarwickService: MyWarwickService,
  scheduler: Scheduler,
)(implicit executionContext: ExecutionContext) extends BaseController {

  import SysadminTestController._
  import securityService._

  def home: Action[AnyContent] = RequireSysadmin { implicit request =>
    Ok(views.html.sysadmin.test(emailForm, myWarwickForm))
  }

  def sendEmail: Action[AnyContent] = RequireSysadmin.async { implicit request =>
    emailForm.bindFromRequest().fold(
      _ => Future.successful(BadRequest),
      data => emailService.queue(data.email, Seq(userLookupService.getUser(data.to).get)).successMap { emails =>
        redirectHome.flashing("success" -> Messages("flash.emails.queued", emails.size))
      }
    )
  }

  def sendMyWarwick: Action[AnyContent] = RequireSysadmin { implicit request =>
    myWarwickForm.bindFromRequest().fold(
      _ => BadRequest,
      data => {
        if (data.alert) myWarwickService.queueNotification(data.toActivity, scheduler)
        else myWarwickService.queueActivity(data.toActivity, scheduler)

        redirectHome.flashing("success" -> Messages("flash.mywarwick.queued"))
      }
    )
  }

  private val redirectHome = Redirect(controllers.sysadmin.routes.SysadminTestController.home())

}
