package actors

import java.util.UUID

import akka.actor._
import akka.cluster.pubsub.DistributedPubSubMediator.{Publish, Subscribe, SubscribeAck, Unsubscribe}
import com.google.inject.assistedinject.Assisted
import javax.inject.Inject
import play.api.libs.json._
import services.{AssessmentClientNetworkActivityService, StudentAssessmentService}
import warwick.core.helpers.ServiceResults.Implicits._
import warwick.core.timing.TimingContext
import warwick.sso.{LoginContext, UniversityID}
import domain.{AssessmentClientNetworkActivity, ClientNetworkInformation, SittingMetadata}
import warwick.core.helpers.JavaTime
import warwick.core.helpers.ServiceResults.ServiceResult

import scala.concurrent.{ExecutionContext, Future}

object WebSocketActor {

  def props(
    loginContext: LoginContext,
    pubsub: ActorRef,
    out: ActorRef,
    studentAssessmentService: StudentAssessmentService,
    assessmentClientNetworkActivityService: AssessmentClientNetworkActivityService,
    additionalTopics: Seq[String],
  )(implicit ec: ExecutionContext, t: TimingContext): Props =
    Props(new WebSocketActor(out, pubsub, loginContext, studentAssessmentService, assessmentClientNetworkActivityService, additionalTopics))

  case class AssessmentAnnouncement(message: String)

  case class ClientMessage(
    `type`: String,
    data: Option[JsValue],
  )
  val readsClientMessage: Reads[ClientMessage] = Json.reads[ClientMessage]

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
          networkInformation.studentAssessmentId.map(assessmentId => {
            val assessmentClientNetworkActivity =
              AssessmentClientNetworkActivity(
                downlink = networkInformation.downlink,
                downlinkMax = networkInformation.downlinkMax,
                effectiveType = networkInformation.effectiveType,
                rtt = networkInformation.rtt,
                `type` = networkInformation.`type`,
                studentAssessmentId = assessmentId,
                JavaTime.offsetDateTime)
            assessmentClientNetworkActivityService.record(assessmentClientNetworkActivity)(TimingContext.none)
              .recover {
                case e: Exception =>
                  log.error(e, s"Error storing AssessmentClientNetworkActivity for StudentAssessment $assessmentId")
              }
          })

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
