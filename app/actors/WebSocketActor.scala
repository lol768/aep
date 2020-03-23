package actors

import akka.actor._
import akka.cluster.pubsub.DistributedPubSubMediator.{Subscribe, SubscribeAck, Unsubscribe}
import play.api.libs.json._
import warwick.sso.LoginContext

object WebSocketActor {

  def props(loginContext: LoginContext, pubsub: ActorRef, out: ActorRef) =
    Props(new WebSocketActor(out, pubsub, loginContext))

  case class AssessmentAnnouncement(message: String)

}

/**
  * WebSocket-facing actor, wired in to the controller. It receives
  * any messages sent from the client in the form of a JsValue. Other
  * actors can also send any kind of message to it.
  *
  * @param out this output will be attached to the websocket and will send
  *            messages back to the client.
  */
class WebSocketActor(out: ActorRef, pubsub: ActorRef, loginContext: LoginContext) extends Actor with ActorLogging {

  import WebSocketActor._

  pubsubSubscribe()

  override def postStop(): Unit = {
    pubsubUnsubscribe()
  }

  override def receive = {
    case AssessmentAnnouncement(announcement) => out ! Json.obj(
      "type" -> "announcement",
      "message" -> announcement,
      "user" -> JsString(loginContext.user.map(u => u.usercode.string).getOrElse("Anonymous"))
    )
    case SubscribeAck(Subscribe(topic, group, ref)) =>
      log.debug(s"WebSocket subscribed to PubSub messages on the topic of '$topic'")
    case nonsense => log.error(s"Ignoring unrecognised message: $nonsense")
  }

  private def pubsubSubscribe() = loginContext.user.foreach { user =>
    pubsub ! Subscribe(user.usercode.string, self)
  }

  private def pubsubUnsubscribe() = loginContext.user.foreach { user =>
    // UnsubscribeAck is swallowed by pubsub, so don't expect a reply to this.
    pubsub ! Unsubscribe(user.usercode.string, self)
  }

}
