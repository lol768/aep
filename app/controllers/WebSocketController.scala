package controllers

import actors.{PubSubActor, WebSocketActor}
import akka.actor.ActorSystem
import akka.stream.Materializer
import javax.inject.{Inject, Singleton}
import play.api.libs.json.JsValue
import play.api.libs.streams.ActorFlow
import play.api.mvc.{Action, AnyContent, Results, WebSocket}
import services._
import warwick.sso.LoginContext

import scala.concurrent.{ExecutionContext, Future}


@Singleton
class WebSocketController @Inject()(implicit
  securityService: SecurityService,
  system: ActorSystem,
  mat: Materializer,
) extends BaseController {

  import securityService._

  // Adapted from My Warwick

  // This actor lives as long as the controller
  private val pubSubActor = system.actorOf(PubSubActor.props())

  def socket: WebSocket = WebSocket.acceptOrResult[JsValue, JsValue] { request =>
    if (securityService.isOriginSafe(request.headers("Origin"))) {
      SecureWebsocket(request) { loginContext: LoginContext =>
        loginContext.user.map(_.usercode) match {
          case Some(usercode) =>
            logger.info(s"WebSocket opening for ${usercode.string}")
            val flow = ActorFlow.actorRef(out => WebSocketActor.props(loginContext, pubSubActor, out))
            Future.successful(Right(flow))
          case None =>
            Future.successful(Left(Forbidden("Only logged-in users can connect for live data using a WebSocket")))
        }
      }
    } else {
      Future.successful(Left(Results.BadRequest("Bad Origin")))
    }
  }

  def websocketTest: Action[AnyContent] = RequireSysadmin { implicit request =>
    Ok(views.html.sysadmin.websocketTest())
  }

}
