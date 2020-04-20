package services

import com.google.inject.{ImplementedBy, Provider}
import javax.inject.{Inject, Singleton}
import play.api.Configuration
import play.api.mvc.Call
import system.Roles
import system.Roles._
import warwick.core.timing.TimingContext
import warwick.sso.LoginContext

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext}
import scala.util.Try

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
class NavigationServiceImpl @Inject()(
  assessmentService: AssessmentService,
  config: Configuration,
  securityServiceProvider: Provider[SecurityService]
)(implicit executionContext: ExecutionContext) extends NavigationService {
  lazy val securityService: SecurityService = securityServiceProvider.get

  private lazy val masquerade = NavigationPage("Masquerade", controllers.sysadmin.routes.MasqueradeController.masquerade())
  private lazy val emailQueue = NavigationPage("Email queue", controllers.sysadmin.routes.EmailQueueController.queued())
  private lazy val sentEmails = NavigationPage("View sent emails", controllers.sysadmin.routes.ViewEmailsController.listAll())
  private lazy val myWarwickQueue = NavigationPage("My Warwick queue", controllers.sysadmin.routes.MyWarwickQueueController.queued())
  private lazy val assessments = NavigationPage("Assessments", controllers.admin.routes.AdminAssessmentsController.index())
  //private lazy val approvals = NavigationPage("Approvals", controllers.admin.routes.ApprovalsController.index())
  private lazy val reporting = NavigationPage("Reporting", controllers.admin.routes.ReportingController.index())
  private lazy val dataGeneration = NavigationPage("Data generation", controllers.sysadmin.routes.DummyDataGenerationController.showForm())
  private lazy val studentActivity = NavigationPage("View student activity", controllers.sysadmin.routes.ViewStudentActivityController.index)

  private lazy val production = config.get[Boolean]("environment.production")

  private lazy val sysadminItems = {
    val baseItems = Seq(
      emailQueue,
      sentEmails,
      myWarwickQueue,
      masquerade,
      studentActivity,
    )
    if (production) baseItems else baseItems :+ dataGeneration
  }

  private lazy val sysadmin =
    NavigationDropdown("Sysadmin", Call("GET", "/sysadmin"), sysadminItems)

  private def sysadminMenuOrMasquerade(loginContext: LoginContext): Seq[Navigation] =
    if (loginContext.actualUserHasRole(Sysadmin))
      Seq(sysadmin)
    else if (loginContext.actualUserHasRole(Masquerader))
      Seq(masquerade)
    else
      Nil

  private lazy val admin =
    NavigationDropdown("Administration", Call("GET", "/admin"), Seq(
      assessments,
      //approvals,
      reporting,
    ))

  private def adminMenu(loginContext: LoginContext): Seq[Navigation] =
    if (securityService.isAdmin(loginContext))
      Seq(admin)
    else
      Nil

  private lazy val invigilator =
    NavigationPage("Invigilation", controllers.invigilation.routes.InvigilatorListController.list())

  private def invigilatorMenu(loginContext: LoginContext): Seq[Navigation] =
    if (loginContext.user.exists(u =>
      loginContext.userHasRole(Roles.Admin) || Try(Await.result(assessmentService.isInvigilator(u.usercode)(TimingContext.none), 5.seconds)).toOption.exists(_.contains(true))
    ))
      Seq(invigilator)
    else
      Nil

  override def getNavigation(loginContext: LoginContext): Seq[Navigation] =
    invigilatorMenu(loginContext) ++
    adminMenu(loginContext) ++
    sysadminMenuOrMasquerade(loginContext)
}
