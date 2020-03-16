package controllers.sysadmin

import java.util.UUID

import controllers.BaseController
import domain.Pagination
import javax.inject.{Inject, Singleton}
import play.api.Configuration
import play.api.data.Form
import play.api.data.Forms._
import play.api.mvc.{Action, AnyContent}
import services.{EmailFilter, EmailService, SecurityService}
import warwick.core.helpers.JavaTime
import warwick.sso.{UniversityID, UserLookupService, Usercode}

import scala.concurrent.ExecutionContext

@Singleton
class ViewEmailsController @Inject() (
  security: SecurityService,
  emailService: EmailService,
  configuration: Configuration,
  userLookupService: UserLookupService,
)(implicit ec: ExecutionContext) extends BaseController {

  import security._

  val emailFilterForm: Form[EmailFilter] = Form(
    mapping(
      "query" -> optional(text),
      "startDate" -> optional(JavaTime.offsetDateTimeFormField),
      "endDate" -> optional(JavaTime.offsetDateTimeFormField)
    )(EmailFilter.apply)(EmailFilter.unapply)
  )

  def emailsPerPage: Int = 50

  def listAll(page: Int = 0): Action[AnyContent] = RequireSysadmin.async { implicit req =>
    val form = emailFilterForm.bindFromRequest()
    val emailFilter = form.value.getOrElse(EmailFilter())

    def userByUserId(query: String) = userLookupService.getUser(Usercode(query)).toOption
    def userByUniversityId(query: String) = userLookupService.getUsers(Seq(UniversityID(query)), includeDisabled = true).toOption.flatMap(_.headOption.map(_._2))
    def getUserEmails(query: String) = (userByUserId(query) ++ userByUniversityId(query)).flatMap(u => u.email).toSeq ++ Seq(query)
    def ssoEmailAddresses: Seq[String] = emailFilter.query.map(q => getUserEmails(q.trim)).getOrElse(Seq.empty)

    def showResults(emails: Seq[String]) = emailService.getEmailsSentTo(emails, emailFilter.startDate, emailFilter.endDate, Pagination.asPage(page, emailsPerPage)).successMap { case (total, emails) =>
      val pagination = Pagination(total, page, controllers.sysadmin.routes.ViewEmailsController.listAll(), emailsPerPage)
      Ok(views.html.sysadmin.emails.list(emails, pagination, form))
    }

    showResults(ssoEmailAddresses)
  }

  def viewEmail(id: UUID): Action[AnyContent] = RequireSysadmin.async { implicit req =>
    val referer = req.request.headers.get("referer")
      .getOrElse(controllers.sysadmin.routes.ViewEmailsController.listAll().toString)

    emailService.get(id).successMap{ email =>
      Ok(views.html.sysadmin.emails.view(email, referer))
    }
  }
}
