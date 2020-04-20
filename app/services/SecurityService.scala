package services

import java.util.UUID

import com.google.inject.ImplementedBy
import helpers.Json.JsonClientError
import javax.inject.{Inject, Singleton}
import play.api.Configuration
import play.api.libs.json.Json
import play.api.mvc._
import services.refiners.{ActionRefiners, AssessmentSpecificRequest, DepartmentAdminAssessmentRequest, DepartmentAdminRequest, StudentAssessmentSpecificRequest}
import system.Roles._
import system.{ImplicitRequestContext, Roles}
import warwick.sso._

import scala.concurrent.{ExecutionContext, Future}

@ImplementedBy(classOf[SecurityServiceImpl])
trait SecurityService {
  type AuthActionBuilder = ActionBuilder[AuthenticatedRequest, AnyContent]

  def SigninAwareAction: AuthActionBuilder
  def SigninRequiredAction: AuthActionBuilder
  def SigninRequiredAjaxAction: AuthActionBuilder
  def RequiredRoleAction(role: RoleName): AuthActionBuilder
  def RequiredActualUserRoleAction(role: RoleName): AuthActionBuilder

  def RequireAdmin: AuthActionBuilder
  def RequireSysadmin: AuthActionBuilder
  def RequireApprover: AuthActionBuilder
  def RequireMasquerader: AuthActionBuilder
  def RequireDepartmentAssessmentManager: AuthActionBuilder


  /**
    * An async result that will either do what you ask (A) or fall back to an error Result.
    * Used as a handler type for websockets.
    */
  type TryAccept[A] = Future[Either[Result, A]]

  def StudentAssessmentAction(id: UUID): ActionBuilder[StudentAssessmentSpecificRequest, AnyContent]
  def StudentAssessmentIsStartedAction(id: UUID): ActionBuilder[StudentAssessmentSpecificRequest, AnyContent]
  def StudentAssessmentInProgressAction(id: UUID, allowWhereNoDuration: Boolean = false): ActionBuilder[StudentAssessmentSpecificRequest, AnyContent]

  def GeneralDepartmentAdminAction: ActionBuilder[DepartmentAdminRequest, AnyContent]
  def SpecificDepartmentAdminAction(deptCode: String): ActionBuilder[DepartmentAdminRequest, AnyContent]
  def AssessmentDepartmentAdminAction(assessmentId: UUID): ActionBuilder[DepartmentAdminAssessmentRequest, AnyContent]

  def InvigilatorAssessmentAction(id: UUID): ActionBuilder[AssessmentSpecificRequest, AnyContent]

  def SecureWebsocket[A](request: play.api.mvc.RequestHeader)(block: warwick.sso.LoginContext => TryAccept[A]): TryAccept[A]

  def isOriginSafe(origin: String): Boolean
  def isAdmin(context: LoginContext): Boolean
}

@Singleton
class SecurityServiceImpl @Inject()(
  sso: SSOClient,
  configuration: Configuration,
  parse: PlayBodyParsers,
  actionRefiners: ActionRefiners,
  groupService: GroupService
)(implicit executionContext: ExecutionContext) extends SecurityService with Results with Rendering with AcceptExtractors with ImplicitRequestContext {

  import actionRefiners._

  private val assessmentManagerGroup: String = configuration.get[String]("app.assessmentManagerGroup")

  private def defaultParser: BodyParser[AnyContent] = parse.default

  override def SigninAwareAction: AuthActionBuilder = sso.Lenient(defaultParser)
  override def SigninRequiredAction: AuthActionBuilder = sso.Strict(defaultParser)
  override def SigninRequiredAjaxAction: AuthActionBuilder = sso.Lenient(defaultParser) andThen requireCondition(_.context.user.nonEmpty, _ => unauthorizedResponse)
  override def RequiredRoleAction(role: RoleName): AuthActionBuilder = sso.RequireRole(role, forbidden)(defaultParser)
  override def RequiredActualUserRoleAction(role: RoleName): AuthActionBuilder = sso.RequireActualUserRole(role, forbidden)(defaultParser)

  val RequireAdmin: AuthActionBuilder = RequiredRoleAction(Admin)
  val RequireSysadmin: AuthActionBuilder = RequiredActualUserRoleAction(Sysadmin)
  val RequireApprover: AuthActionBuilder = RequiredRoleAction(Approver)
  val RequireMasquerader: AuthActionBuilder = RequiredActualUserRoleAction(Masquerader)
  val RequireDepartmentAssessmentManager: AuthActionBuilder = RequireDeptWebGroup(assessmentManagerGroup, forbidden)(defaultParser)

  def RequireDeptWebGroup[C](group: String, otherwise: AuthenticatedRequest[_] => Result)(parser: BodyParser[C]): ActionBuilder[AuthenticatedRequest, C] =
    (sso.Strict(parser) andThen requireCondition(requireGroup(_, group), otherwise))


  private def requireGroup[C](r: AuthenticatedRequest[_], group: String): Boolean = {
    r.context.user.exists(u => u.department.flatMap(d => d.code).fold(false)(deptCode => groupService.isUserInGroup(r.context.user.get.usercode, GroupName(s"${deptCode.toLowerCase()}-$group")).getOrElse(false)))
  }

  class RequireConditionActionFilter(block: AuthenticatedRequest[_] => Boolean, otherwise: AuthenticatedRequest[_] => Result)(implicit val executionContext: ExecutionContext) extends ActionFilter[AuthenticatedRequest] {
    override protected def filter[A](request: AuthenticatedRequest[A]): Future[Option[Result]] =
      Future.successful {
        if (block(request)) None
        else Some(otherwise(request))
      }
  }

  private def requireCondition(block: AuthenticatedRequest[_] => Boolean, otherwise: AuthenticatedRequest[_] => Result) =
    new RequireConditionActionFilter(block, otherwise)

  private def forbidden(request: AuthenticatedRequest[_]) = {
    render {
      case Accepts.Json() =>
        request.context.user
          .map(user => forbiddenResponse(user, request.path))
          .getOrElse(unauthorizedResponse)
      case _ =>
        val userName = getUserDisplayName(request.context)
        Forbidden(views.html.errors.forbidden(userName)(requestContext(request)))
    }(request)
  }

  private def getUserDisplayName(context: LoginContext): Option[String] = {
    import context._

    for {
      name <- user.flatMap(_.name.first)
      actualName <- actualUser.flatMap(_.name.first)
    } yield {
      if (isMasquerading)
        s"$name (really $actualName)"
      else
        name
    }
  }

  private def forbiddenResponse(user: User, path: String) =
    Forbidden(Json.toJson(JsonClientError(status = "forbidden", errors = Seq(s"User ${user.usercode.string} does not have permission to access $path"))))

  private val unauthorizedResponse =
    Unauthorized(Json.toJson(JsonClientError(status = "unauthorized", errors = Seq("You are not signed in.  You may authenticate through Web Sign-On."))))

  override def StudentAssessmentAction(assessmentId: UUID): ActionBuilder[StudentAssessmentSpecificRequest, AnyContent] =
    SigninRequiredAction andThen WithSitting(assessmentId)

  override def StudentAssessmentIsStartedAction(assessmentId: UUID): ActionBuilder[StudentAssessmentSpecificRequest, AnyContent] =
    StudentAssessmentAction(assessmentId) andThen IsStudentAssessmentStarted

  override def StudentAssessmentInProgressAction(assessmentId: UUID, allowWhereNoDuration: Boolean = false): ActionBuilder[StudentAssessmentSpecificRequest, AnyContent] =
    StudentAssessmentAction(assessmentId) andThen IsStudentAssessmentStarted andThen IsStudentAssessmentNotFinalised andThen StudentCanModifySubmission(allowWhereNoDuration)

  override def InvigilatorAssessmentAction(id: UUID): ActionBuilder[AssessmentSpecificRequest, AnyContent] =
    SigninRequiredAction andThen WithAssessment(id) andThen IsInvigilatorOrAdmin

  // User needs to be a departmental admin for at least one department
  override def GeneralDepartmentAdminAction: ActionBuilder[DepartmentAdminRequest, AnyContent] =
    SigninRequiredAction andThen WithDepartmentsUserIsAdminFor

  // User needs to be a departmental admin for the specific department supplied
  override def SpecificDepartmentAdminAction(deptCode: String): ActionBuilder[DepartmentAdminRequest, AnyContent] =
    SigninRequiredAction andThen WithDepartmentsUserIsAdminFor andThen IsAdminForDepartment(deptCode)

  // User needs to be a departmental admin for the department associated with the assessment supplied
  override def AssessmentDepartmentAdminAction(assessmentId: UUID): ActionBuilder[DepartmentAdminAssessmentRequest, AnyContent] =
    SigninRequiredAction andThen WithDepartmentsUserIsAdminFor andThen WithAssessmentToAdminister(assessmentId)

  override def SecureWebsocket[A](request: play.api.mvc.RequestHeader)(block: warwick.sso.LoginContext => TryAccept[A]): TryAccept[A] =
    sso.withUser(request)(block)

  override def isOriginSafe(origin: String): Boolean = {
    val uri = new java.net.URI(origin)
    uri.getHost == configuration.get[String]("domain") && uri.getScheme == "https"
  }

  def isAdmin(ctx: LoginContext): Boolean =
    ctx.user.exists { user =>
      if (ctx.userHasRole(Admin) || ctx.userHasRole(Sysadmin)) {
        true
      } else {
        groupService.getGroupsForUser(user.usercode).map { groups =>
          groups.exists(_.name.string.endsWith(assessmentManagerGroup))
        }.getOrElse(false)
      }
    }
}
