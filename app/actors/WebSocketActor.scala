package actors

import java.util.UUID

import akka.actor._
import akka.cluster.pubsub.DistributedPubSubMediator.{Publish, Subscribe, SubscribeAck, Unsubscribe}
import com.google.inject.assistedinject.Assisted
import javax.inject.Inject
import play.api.libs.json._
import services.StudentAssessmentService
import warwick.core.helpers.ServiceResults.Implicits._
import warwick.core.timing.TimingContext
import warwick.sso.{LoginContext, UniversityID}

import scala.concurrent.ExecutionContext

object WebSocketActor {

  def props(
    loginContext: LoginContext,
    pubsub: ActorRef,
    out: ActorRef,
    studentAssessmentService: StudentAssessmentService,
    additionalTopics: Seq[String],
  )(implicit ec: ExecutionContext, t: TimingContext): Props =
    Props(new WebSocketActor(out, pubsub, loginContext, studentAssessmentService, additionalTopics))

  case class AssessmentAnnouncement(message: String)

  case class ClientMessage(
    `type`: String,
    data: Option[JsValue],
  )
  val readsClientMessage: Reads[ClientMessage] = Json.reads[ClientMessage]

  case class ClientNetworkInformation(
    downlink: Option[Double], // mbps
    downlinkMax: Option[Double], // mbps
    effectiveType: Option[String], // 'slow-2g', '2g', '3g', or '4g',
    rtt: Option[Int], // rounded to nearest 25ms
    `type`: Option[String], // bluetooth, cellular, ethernet, none, wifi, wimax, other, unknown
  )
  val readsClientNetworkInformation: Reads[ClientNetworkInformation] = Json.reads[ClientNetworkInformation]

  case class RequestAssessmentTiming(
    assessmentId: UUID
  )
  val readsRequestAssessmentTiming: Reads[RequestAssessmentTiming] = Json.reads[RequestAssessmentTiming]

  case class AssessmentTimingInformation(
    id: UUID,
    timeRemaining: Option[Long],
    extraTimeAdjustment: Option[Long],
    timeUntilStart: Option[Long],
    timeSinceStart: Option[Long],
    timeUntilEndOfWindow: Option[Long],
    hasStarted: Boolean,
    hasFinalised: Boolean,
  )
  implicit val writesAssessmentTimingInformation: Writes[AssessmentTimingInformation] = Json.writes[AssessmentTimingInformation]
}

/**
  * WebSocket-facing actor, wired in to the controller. It receives
  * any messages sent from the client in the form of a JsValue. Other
  * actors can also send any kind of message to it.
  *
  * @param out this output will be attached to the websocket and will send
  *            messages back to the client.
  */
class WebSocketActor @Inject() (
  out: ActorRef,
  pubsub: ActorRef,
  loginContext: LoginContext,
  @Assisted studentAssessmentService: StudentAssessmentService,
  additionalTopics: Seq[String],
)(implicit
  ec: ExecutionContext
) extends Actor with ActorLogging {

  import WebSocketActor._

  pubsubSubscribe()

  override def postStop(): Unit = {
    pubsubUnsubscribe()
  }

  override def receive: Receive = {
    case AssessmentAnnouncement(announcement) => out ! Json.obj(
      "type" -> "announcement",
      "message" -> announcement,
      "user" -> JsString(loginContext.user.map(u => u.usercode.string).getOrElse("Anonymous"))
    )

    case SubscribeAck(Subscribe(topic, _, _)) =>
      log.debug(s"WebSocket subscribed to PubSub messages on the topic of '$topic'")

    case clientMessage: JsObject if clientMessage.validate[ClientMessage](readsClientMessage).isSuccess =>
      val message = clientMessage.as[ClientMessage](readsClientMessage)

      message match {
        case m if m.`type` == "NetworkInformation" && m.data.exists(_.validate[ClientNetworkInformation](readsClientNetworkInformation).isSuccess) =>
          val networkInformation = m.data.get.as[ClientNetworkInformation](readsClientNetworkInformation)
          // TODO handle client network information

        case m if m.`type` == "RequestAssessmentTiming" && m.data.exists(_.validate[RequestAssessmentTiming](readsRequestAssessmentTiming).isSuccess) =>
          val assessmentId = m.data.get.as[RequestAssessmentTiming](readsRequestAssessmentTiming).assessmentId
          studentAssessmentService.getMetadataWithAssessment(loginContext.user.flatMap(u => u.universityId).get, assessmentId)(TimingContext.none).successMapTo { assessment =>
            out ! Json.obj(
              "type"-> "AssessmentTimingInformation",
              "assessments"-> Json.arr(Json.toJson(assessment.getTimingInfo))
            )
          }

        case m if m.`type` == "RequestAssessmentTiming" =>
          studentAssessmentService.getMetadataWithAssessment(loginContext.user.flatMap(u => u.universityId).get)(TimingContext.none).successMapTo { assessments =>
            out ! Json.obj(
              "type"-> "AssessmentTimingInformation",
              "assessments"-> JsArray(assessments.map(a => Json.toJson(a.getTimingInfo)))
            )
          }

        case m => log.error(s"Ignoring unrecognised client message: $m")
      }

    case nonsense => log.error(s"Ignoring unrecognised message: $nonsense")
  }

  private def pubsubSubscribe(): Unit = loginContext.user.foreach { user =>
    pubsub ! Subscribe(user.usercode.string, self)
    additionalTopics.foreach(pubsub ! Subscribe(_, self))
  }

  private def pubsubUnsubscribe(): Unit = loginContext.user.foreach { user =>
    // UnsubscribeAck is swallowed by pubsub, so don't expect a reply to this.
    pubsub ! Unsubscribe(user.usercode.string, self)
    additionalTopics.foreach(pubsub ! Unsubscribe(_, self))
  }
}
