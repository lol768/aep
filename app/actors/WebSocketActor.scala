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
import play.api.libs.functional.syntax._
import play.api.libs.json._
import play.twirl.api.Html
import services.{AssessmentClientNetworkActivityService, StudentAssessmentService}
import warwick.core.helpers.JavaTime
import warwick.core.helpers.ServiceResults.Implicits._
import warwick.core.helpers.ServiceResults.ServiceResult
import warwick.core.system.AuditLogContext
import warwick.core.timing.TimingContext
import warwick.sso.{LoginContext, UniversityID, Usercode}

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

  case class AssessmentAnnouncement(messageText: String, timestamp: OffsetDateTime) {
    val messageHTML: Html = Html(warwick.core.views.utils.nl2br(messageText).body)
  }

  case class AssessmentMessage(messageText: String, sender: MessageSender, client: String, timestamp: OffsetDateTime) {
    val messageHTML: Html = Html(warwick.core.views.utils.nl2br(messageText).body)
  }

  case class ClientMessage(
    `type`: String,
    data: Option[JsValue],
  )
  val readsClientMessage: Reads[ClientMessage] = Json.reads[ClientMessage]

  val readsClientNetworkInformation: Reads[ClientNetworkInformation] = (
    (__ \ "downlink").readNullable[Double] and
      (__ \ "downlinkMax").readNullable[Double] and
      (__ \ "effectiveType").readNullable[String] and
      (__ \ "rtt").readNullable[Int] and
      (__ \ "type").readNullable[String] and
      (__ \ "studentAssessmentId").readNullable[UUID] and
      (__ \ "assessmentId").readNullable[UUID] and
      (__ \ "usercode").readNullable[String].map(_.map(Usercode.apply)) and
      (__ \ "localTimezoneName").readNullable[String]
    )(ClientNetworkInformation.apply _)
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
      "messageHTML" -> aa.messageHTML.body,
      "messageText" -> aa.messageText,
      "timestamp" -> views.html.tags.localisedDatetime(aa.timestamp).toString,
      "user" -> JsString(loginContext.user.map(u => u.usercode.string).getOrElse("Anonymous"))
    )

    case am: AssessmentMessage => out ! Json.obj(
      "type" -> "assessmentMessage",
      "messageHTML" -> am.messageHTML.body,
      "messageText" -> am.messageText,
      "timestamp" -> views.html.tags.localisedDatetime(am.timestamp).toString,
      "sender" -> am.sender.entryName,
      "client" -> am.client
    )

    case SubscribeAck(Subscribe(topic, _, _)) =>
      log.debug(s"WebSocket subscribed to PubSub messages on the topic of '$topic'")

    case clientMessage: JsObject if clientMessage.validate[ClientMessage](readsClientMessage).isSuccess =>
      val message = clientMessage.as[ClientMessage](readsClientMessage)

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
              studentAssessmentId = networkInformation.studentAssessmentId,
              assessmentId = networkInformation.assessmentId,
              usercode = networkInformation.usercode,
              localTimezoneName = networkInformation.localTimezoneName.map(_.maybeZoneId),
              timestamp = JavaTime.offsetDateTime,
            )

          if (networkInformation.studentAssessmentId.nonEmpty || networkInformation.assessmentId.nonEmpty) {
            assessmentClientNetworkActivityService.record(assessmentClientNetworkActivity)(AuditLogContext.empty())
              .recover {
                case e: Exception =>
                  log.error(e, s"Error storing AssessmentClientNetworkActivity for ${if(networkInformation.studentAssessmentId.nonEmpty) s"StudentAssessment ${assessmentClientNetworkActivity.studentAssessmentId}" else s"Assessment ${assessmentClientNetworkActivity.assessmentId}"}")
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
