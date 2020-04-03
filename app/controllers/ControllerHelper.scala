package controllers

import helpers.Json._
import play.api.libs.json.Json
import play.api.mvc._
import system.{ImplicitRequestContext, Roles}
import warwick.core.Logging
import warwick.core.helpers.ServiceResults.Implicits._
import warwick.core.helpers.ServiceResults.{ServiceError, ServiceResult}
import warwick.sso.{AuthenticatedRequest, UniversityID, User}

import scala.concurrent.{ExecutionContext, Future}
import scala.language.implicitConversions

trait ControllerHelper extends ServiceResultErrorRendering with Logging {
  def currentUser()(implicit request: AuthenticatedRequest[_]): User =
    request.context.user.getOrElse(noUserException)
  def currentUniversityId()(implicit request: AuthenticatedRequest[_]): UniversityID =
    currentUser().universityId.getOrElse(noUniversityIdException)
  def currentUserRoles()(implicit request: AuthenticatedRequest[_]): UserRoles = UserRoles(currentUser(), request)

  implicit class FutureServiceResultControllerOps[A](val future: Future[ServiceResult[A]]) {
    def successMap(fn: A => Result)(implicit r: RequestHeader, ec: ExecutionContext): Future[Result] =
      future.map { result =>
        result.fold(showErrors, fn)
      }

    def successFlatMap(fn: A => Future[Result])(implicit r: RequestHeader, ec: ExecutionContext): Future[Result] =
      future.flatMap { result =>
        result.fold(
          e => Future.successful(showErrors(e)),
          fn
        )
      }
  }

  implicit def futureServiceResultOps[A](future: Future[ServiceResult[A]]): FutureServiceResultOps[A] =
    new FutureServiceResultOps[A](future)

  private def noUserException = throw new NoSuchElementException("No user associated with this request")
  private def noUniversityIdException = throw new NoSuchElementException("User associated with this request has no University ID")
}

trait ServiceResultErrorRendering extends Results with Rendering with AcceptExtractors with ImplicitRequestContext {
  def showErrors(errors: Seq[_ <: ServiceError])(implicit request: RequestHeader): Result =
    render {
      case Accepts.Json() => BadRequest(Json.toJson(JsonClientError(status = "bad_request", errors = errors.map(_.message))))
      case _ => BadRequest(views.html.errors.multiple(errors))
    }
}

case class UserRoles(
  user: User,
  request: AuthenticatedRequest[_]
) {
  lazy val isAdmin: Boolean = request.context.userHasRole(Roles.Admin)
  lazy val isSysAdmin: Boolean = request.context.userHasRole(Roles.Sysadmin)
  lazy val isMasquerader: Boolean = request.context.userHasRole(Roles.Masquerader)
  lazy val isApprover: Boolean = request.context.userHasRole(Roles.Approver)
  lazy val isStaffOrPGR: Boolean = user.isStaffOrPGR
  lazy val isStaffNotPGR: Boolean = user.isStaffNotPGR
  lazy val isStudent: Boolean = user.isStudent
  lazy val isPGR: Boolean = user.isPGR
  lazy val isPGT: Boolean = user.isPGT
  lazy val isAlumni: Boolean = user.isAlumni
  lazy val isUndergraduate: Boolean = user.isUndergraduate
}
