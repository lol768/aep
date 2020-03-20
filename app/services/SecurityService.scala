package services

import com.google.inject.ImplementedBy
import helpers.Json.JsonClientError
import javax.inject.{Inject, Singleton}
import play.api.libs.json.Json
import play.api.mvc._
import system.{ImplicitRequestContext, Roles}
import uk.ac.warwick.sso.client.SSOConfiguration
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

  def RequireSysadmin: AuthActionBuilder
  def RequireMasquerader: AuthActionBuilder

  /**
    * An async result that will either do what you ask (A) or fall back to an error Result.
    * Used as a handler type for websockets.
    */
  type TryAccept[A] = Future[Either[Result, A]]


  def SecureWebsocket[A](request: play.api.mvc.RequestHeader)(block: warwick.sso.LoginContext => TryAccept[A]): TryAccept[A]

  def isOriginSafe(origin: String): Boolean
}

@Singleton
class SecurityServiceImpl @Inject()(
  sso: SSOClient,
  configuration: SSOConfiguration,
  parse: PlayBodyParsers
)(implicit executionContext: ExecutionContext) extends SecurityService with Results with Rendering with AcceptExtractors with ImplicitRequestContext {

  private def defaultParser: BodyParser[AnyContent] = parse.default

  override def SigninAwareAction: AuthActionBuilder = sso.Lenient(defaultParser)
  override def SigninRequiredAction: AuthActionBuilder = sso.Strict(defaultParser)
  override def SigninRequiredAjaxAction: AuthActionBuilder = sso.Lenient(defaultParser) andThen requireCondition(_.context.user.nonEmpty, _ => unauthorizedResponse)
  override def RequiredRoleAction(role: RoleName): AuthActionBuilder = sso.RequireRole(role, forbidden)(defaultParser)
  override def RequiredActualUserRoleAction(role: RoleName): AuthActionBuilder = sso.RequireActualUserRole(role, forbidden)(defaultParser)

  val RequireSysadmin: AuthActionBuilder = RequiredActualUserRoleAction(Roles.Sysadmin)
  val RequireMasquerader: AuthActionBuilder = RequiredActualUserRoleAction(Roles.Masquerader)

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

  override def SecureWebsocket[A](request: play.api.mvc.RequestHeader)(block: warwick.sso.LoginContext => TryAccept[A]): TryAccept[A] =
    sso.withUser(request)(block)

  override def isOriginSafe(origin: String): Boolean = {
    val uri = new java.net.URI(origin)
    uri.getHost == configuration.getString("shire.sscookie.domain") && uri.getScheme == "https"
  }
}
