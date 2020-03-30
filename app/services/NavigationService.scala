package services

import javax.inject.Singleton
import com.google.inject.ImplementedBy
import play.api.mvc.Call
import system.Roles.{Admin, Sysadmin}
import warwick.sso.LoginContext

sealed trait Navigation {
  def label: String

  def route: Call

  def children: Seq[Navigation]

  def dropdown: Boolean

  /**
    * Either this is the current page, or the current page is a child of this page
    */
  def isActive(path: String): Boolean = path.startsWith(route.url) || children.exists(_.isActive(path))

  def deepestActive(path: String): Option[Navigation] =
    if (path.startsWith(route.url) && !children.exists(_.isActive(path))) Some(this)
    else children.flatMap(_.deepestActive(path)).headOption
}

case class NavigationPage(
  label: String,
  route: Call,
  children: Seq[Navigation] = Nil
) extends Navigation {
  val dropdown = false
}

case class NavigationDropdown(
  label: String,
  route: Call,
  children: Seq[Navigation]
) extends Navigation {
  val dropdown = true
}

@ImplementedBy(classOf[NavigationServiceImpl])
trait NavigationService {
  def getNavigation(loginContext: LoginContext): Seq[Navigation]
}

@Singleton
class NavigationServiceImpl extends NavigationService {

  private lazy val masquerade = NavigationPage("Masquerade", controllers.sysadmin.routes.MasqueradeController.masquerade())
  private lazy val emailQueue = NavigationPage("Email queue", controllers.sysadmin.routes.EmailQueueController.queued())
  private lazy val sentEmails = NavigationPage("View sent emails", controllers.sysadmin.routes.ViewEmailsController.listAll())
  private lazy val myWarwickQueue = NavigationPage("My Warwick queue", controllers.sysadmin.routes.MyWarwickQueueController.queued())
  private lazy val assessments = NavigationPage("Assessments", controllers.admin.routes.AssessmentsController.index())
  private lazy val approvals = NavigationPage("Approvals", controllers.admin.routes.ApprovalsController.index())
  private lazy val reporting = NavigationPage("Reporting", controllers.admin.routes.ReportingController.index())

  private lazy val sysadmin =
    NavigationDropdown("Sysadmin", Call("GET", "/sysadmin"), Seq(
      emailQueue,
      sentEmails,
      myWarwickQueue,
      masquerade,
    ))

  private def sysadminMenu(loginContext: LoginContext): Seq[Navigation] =
    if (loginContext.actualUserHasRole(Sysadmin))
      Seq(sysadmin)
    else
      Nil

  private lazy val admin =
    NavigationDropdown("Administration", Call("GET", "/admin"), Seq(
      assessments,
      approvals,
      reporting,
    ))

  private def adminMenu(loginContext: LoginContext): Seq[Navigation] =
    if (loginContext.actualUserHasRole(Admin))
      Seq(admin)
    else
      Nil

  override def getNavigation(loginContext: LoginContext): Seq[Navigation] =
    adminMenu(loginContext) ++
    sysadminMenu(loginContext)
}
