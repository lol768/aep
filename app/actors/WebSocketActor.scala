package actors

import java.time.OffsetDateTime
import java.util.UUID

import akka.actor._
import akka.cluster.pubsub.DistributedPubSubMediator.{Subscribe, SubscribeAck, Unsubscribe}
import com.google.inject.assistedinject.Assisted
import domain.messaging.MessageSender
import domain.{AssessmentClientNetworkActivity, ClientNetworkInformation, SittingMetadata}
import helpers.LenientTimezoneNameParsing._
import javax.inject.Inject
import play.api.libs.json._
import play.twirl.api.Html
import services.{AssessmentClientNetworkActivityService, StudentAssessmentService}
import warwick.core.helpers.JavaTime
import warwick.core.helpers.ServiceResults.Implicits._
import warwick.core.helpers.ServiceResults.ServiceResult
import warwick.core.system.AuditLogContext
import warwick.core.timing.TimingContext
import warwick.sso.{LoginContext, UniversityID}

import scala.concurrent.{ExecutionContext, Future}

object WebSocketActor {

  def props(
    loginContext: LoginContext,
    pubsub: ActorRef,
    out: ActorRef,
    studentAssessmentService: StudentAssessmentService,
    assessmentClientNetworkActivityService: AssessmentClientNetworkActivityService,
    additionalTopics: Set[String],
  )(implicit ec: ExecutionContext, t: TimingContext): Props =
    Props(new WebSocketActor(out, pubsub, loginContext, studentAssessmentService, assessmentClientNetworkActivityService, additionalTopics))

  case class AssessmentAnnouncement(id: String, messageText: String, timestamp: OffsetDateTime) {
    val messageHTML: Html = Html(warwick.core.views.utils.nl2br(messageText).body)
  }

  case class AssessmentMessage(id: String, messageText: String, sender: MessageSender, client: String, timestamp: OffsetDateTime) {
    val messageHTML: Html = Html(warwick.core.views.utils.nl2br(messageText).body)
  }

  case class ClientMessage(
    `type`: String,
    data: Option[JsValue],
  )
  val readsClientMessage: Reads[ClientMessage] = Json.reads[ClientMessage]

  object ClientMessage {
    object fromJson {
      // For pattern matching
      def unapply(in: JsValue): Option[ClientMessage] = in.asOpt(readsClientMessage)
    }
  }

  val readsClientNetworkInformation: Reads[ClientNetworkInformation] = Json.reads[ClientNetworkInformation]

  case class RequestAssessmentTiming(
    assessmentId: UUID
  )
  object RequestAssessmentTiming {
    implicit val reads: Reads[RequestAssessmentTiming] = Json.reads[RequestAssessmentTiming]
  }
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
  @Assisted assessmentClientNetworkActivityService: AssessmentClientNetworkActivityService,
  additionalTopics: Set[String],
)(implicit
  ec: ExecutionContext
) extends Actor with ActorLogging {

  import WebSocketActor._

  pubsubSubscribe()

  override def postStop(): Unit = {
    pubsubUnsubscribe()
  }

  override def receive: Receive = {
    case aa: AssessmentAnnouncement => out ! Json.obj(
      "type" -> "announcement",
      "id" -> aa.id,
      "messageHTML" -> aa.messageHTML.body,
      "messageText" -> aa.messageText,
      "timestamp" -> views.html.tags.localisedDatetime(aa.timestamp).toString,
    )

    case am: AssessmentMessage => out ! Json.obj(
      "type" -> "assessmentMessage",
      "id" -> am.id,
      "messageHTML" -> am.messageHTML.body,
      "messageText" -> am.messageText,
      "timestamp" -> views.html.tags.localisedDatetime(am.timestamp).toString,
      "sender" -> am.sender.entryName,
      "client" -> am.client
    )

    case SubscribeAck(Subscribe(topic, _, _)) =>
      log.debug(s"WebSocket subscribed to PubSub messages on the topic of '$topic'")

    case ClientMessage.fromJson(message) =>
      message match {
        case m if m.`type` == "NetworkInformation" && m.data.exists(_.validate[ClientNetworkInformation](readsClientNetworkInformation).isSuccess) =>
          val networkInformation = m.data.get.as[ClientNetworkInformation](readsClientNetworkInformation)

          val assessmentClientNetworkActivity =
            AssessmentClientNetworkActivity(
              downlink = networkInformation.downlink,
              downlinkMax = networkInformation.downlinkMax,
              effectiveType = networkInformation.effectiveType,
              rtt = networkInformation.rtt,
              `type` = networkInformation.`type`,
              studentAssessmentId = networkInformation.studentAssessmentId.orNull,
              localTimezoneName = networkInformation.localTimezoneName.map(_.maybeZoneId),
              timestamp = JavaTime.offsetDateTime,
            )

          if (networkInformation.studentAssessmentId.nonEmpty) {
            assessmentClientNetworkActivityService.record(assessmentClientNetworkActivity)(AuditLogContext.empty())
              .recover {
                case e: Exception =>
                  log.error(e, s"Error storing AssessmentClientNetworkActivity for StudentAssessment ${assessmentClientNetworkActivity.studentAssessmentId}")
              }
          }

          out ! Json.obj(
            "type" -> "UpdateConnectivityIndicator",
            "signalStrength" -> assessmentClientNetworkActivity.signalStrength
          )

        case m if m.`type` == "RequestAssessmentTiming" =>
          val universityID: UniversityID = loginContext.user.flatMap(u => u.universityId).get
          val request: Option[RequestAssessmentTiming] = m.data.flatMap(_.validate[RequestAssessmentTiming].asOpt)
          val requestedAssessmentId: Option[UUID] = request.map(_.assessmentId)

          // Get single assessment or all user's assessments, depending on what was requested
          val getAssessments: Future[ServiceResult[Seq[SittingMetadata]]] = requestedAssessmentId.map { id =>
            studentAssessmentService.getSittingsMetadata(universityID, id)(TimingContext.none).successMapTo(a => Seq(a))
          }.getOrElse {
            studentAssessmentService.getSittingsMetadata(universityID)(TimingContext.none)
          }

          getAssessments.successMapTo { assessments =>
            out ! Json.obj(
              "type"-> "AssessmentTimingInformation",
              "now"-> JavaTime.instant.toEpochMilli,
              "assessments"-> assessments.map(a => Json.toJson(a.getTimingInfo))
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
