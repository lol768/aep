import controllers.RequestContext
import helpers.Json._
import javax.inject.{Inject, Singleton}
import play.api.{Configuration, Environment, Mode}
import play.api.http.{DefaultHttpErrorHandler, Status}
import play.api.libs.json.Json
import play.api.mvc._
import system.{ImplicitRequestContext, Roles}
import warwick.core.Logging
import warwick.fileuploads.UploadedFileControllerHelper.UploadedFileConfiguration
import warwick.sso.{GroupService, RoleService}

import scala.concurrent.Future

/**
  * Serves custom error views.
  */
@Singleton
class ErrorHandler @Inject()(
  environment: Environment,
  config: Configuration,
  groupService: GroupService,
  roleService: RoleService,
) extends DefaultHttpErrorHandler with Results with Status with Logging with Rendering with AcceptExtractors with ImplicitRequestContext {

  private[this] val uploadedFileConfiguration = UploadedFileConfiguration.fromConfiguration(config)

  private def isSysAdmin(request: RequestHeader): Boolean = {
    requestContext(request).actualUser.exists { user =>
      val role = roleService.getRole(Roles.Sysadmin)
      groupService.isUserInGroup(user.usercode, role.groupName).fold (
        _ => false,
        (result: Boolean) => result
      )
    }
  }

  override def onClientError(request: RequestHeader, statusCode: Int, message: String): Future[Result] = {
    implicit val context: RequestContext = requestContext(request)

    // If we don't have a valid user, force login anyway
    // (don't give unauthenticated users information about what's a 404 and what isn't)
    if (context.user.isEmpty)
      Future.successful(Redirect(context.loginUrl))
    else
      Future.successful(
        statusCode match {
          case NOT_FOUND => render {
            case Accepts.Json() => NotFound(Json.toJson(JsonClientError(status = "not_found", errors = Seq(message))))
            case _ => NotFound(views.html.errors.notFound())
          }(request)

          // This is caught by PlayBodyParsers if the _whole_ request is over-sized before it can get to
          // UploadedFileControllerHelper so we have to replicate the correct behaviour here
          case REQUEST_ENTITY_TOO_LARGE => render {
            case Accepts.Json() => EntityTooLarge(Json.toJson(JsonClientError(status = "entity_too_large", errors = Seq(s"Sorry, the attachments are larger than the maximum attachment size (${helpers.humanReadableSize(uploadedFileConfiguration.maxTotalFileSizeBytes)})."))))
            case _ => EntityTooLarge(views.html.errors.entityTooLarge(uploadedFileConfiguration))
          }(request)

          case _ => render {
            case Accepts.Json() => Status(statusCode)(Json.toJson(JsonClientError(status = statusCode.toString, errors = Seq(message))))
            case _ => Status(statusCode)(views.html.errors.clientError(statusCode, message))
          }(request)
        }
      )
  }

  override def onServerError(request: RequestHeader, exception: Throwable): Future[Result] = {
    implicit val requestHeader: RequestHeader = request

    logger.error("Internal Server Error", exception)
    Future.successful(
      render {
        case Accepts.Json() =>
          InternalServerError(Json.toJson(JsonClientError(
            status = "internal_server_error",
            errors = {
              if (environment.mode == Mode.Dev || environment.mode == Mode.Test || isSysAdmin(requestHeader))
                Seq(exception.getMessage)
              else
                Seq("Sorry, there's been a problem and we weren't able to complete your request")
            }
          )))
        case _ => InternalServerError(views.html.errors.serverError(exception, environment.mode, isSysAdmin(requestHeader)))
      }(request)
    )
  }

}
