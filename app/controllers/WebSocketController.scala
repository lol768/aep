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
        val who = loginContext.user.map(_.usercode).getOrElse("nobody")
        logger.info(s"WebSocket opening for $who")
        val flow = ActorFlow.actorRef(out => WebSocketActor.props(loginContext, pubSubActor, out))
        Future.successful(Right(flow))
      }
    } else {
      Future.successful(Left(Results.BadRequest("Bad Origin")))
    }
  }

  def websocketTest: Action[AnyContent] = RequireSysadmin { implicit request =>
    Ok(views.html.sysadmin.websocketTest())
  }

}
