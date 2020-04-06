package services

import javax.inject.{Inject, Singleton}
import com.google.inject.ImplementedBy
import play.api.Configuration
import play.api.mvc.Call
import system.Roles.{Admin, Sysadmin}
import warwick.core.timing.TimingContext
import warwick.sso.{GroupService, LoginContext, User}

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
  groupService: GroupService,
  config: Configuration,
)(implicit executionContext: ExecutionContext) extends NavigationService {

  private lazy val masquerade = NavigationPage("Masquerade", controllers.sysadmin.routes.MasqueradeController.masquerade())
  private lazy val emailQueue = NavigationPage("Email queue", controllers.sysadmin.routes.EmailQueueController.queued())
  private lazy val sentEmails = NavigationPage("View sent emails", controllers.sysadmin.routes.ViewEmailsController.listAll())
  private lazy val myWarwickQueue = NavigationPage("My Warwick queue", controllers.sysadmin.routes.MyWarwickQueueController.queued())
  private lazy val assessments = NavigationPage("Assessments", controllers.admin.routes.AssessmentsController.index())
  private lazy val approvals = NavigationPage("Approvals", controllers.admin.routes.ApprovalsController.index())
  private lazy val reporting = NavigationPage("Reporting", controllers.admin.routes.ReportingController.index())
  private lazy val dataGeneration = NavigationPage("Data generation", controllers.sysadmin.routes.DummyDataGenerationController.showForm())
  private lazy val studentActivity = NavigationPage("View student activity", controllers.admin.routes.ViewStudentActivityController.index)

  private lazy val production = config.get[Boolean]("environment.production")

  private lazy val navigationItems = {
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
    NavigationDropdown("Sysadmin", Call("GET", "/sysadmin"), navigationItems)

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
    if (isAdmin(loginContext))
      Seq(admin)
    else
      Nil

  private lazy val invigilator =
    NavigationPage("Invigilation", controllers.invigilation.routes.InvigilatorListController.list())

  private def invigilatorMenu(loginContext: LoginContext): Seq[Navigation] =
    if (loginContext.user.exists(u =>
      Try(Await.result(assessmentService.isInvigilator(u.usercode)(TimingContext.none), 5.seconds)).toOption.exists(_.contains(true))
    ))
      Seq(invigilator)
    else
      Nil

  override def getNavigation(loginContext: LoginContext): Seq[Navigation] =
    invigilatorMenu(loginContext) ++
    adminMenu(loginContext) ++
    sysadminMenu(loginContext)

  private def isAdmin(ctx: LoginContext): Boolean =
    ctx.user.exists { user =>
      if (ctx.userHasRole(Admin) || ctx.userHasRole(Sysadmin)) {
        true
      } else {
        groupService.getGroupsForUser(user.usercode).map { groups =>
          groups.exists(_.name.string.contains(config.get[String]("app.assessmentManagerGroup")))
        }.getOrElse(false)
      }
    }
}
