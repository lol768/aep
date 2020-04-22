package actors

import java.time.OffsetDateTime
import java.util.UUID

import akka.actor._
import akka.cluster.pubsub.DistributedPubSubMediator.{Subscribe, SubscribeAck, Unsubscribe}
import com.google.inject.assistedinject.Assisted
import domain.messaging.MessageSender
import domain.{Announcement, AssessmentClientNetworkActivity, ClientNetworkInformation, SittingMetadata, UploadAttempt}
import helpers.LenientTimezoneNameParsing._
import javax.inject.Inject
import play.api.libs.json._
import play.twirl.api.Html
import services.{AnnouncementService, AssessmentClientNetworkActivityService, StudentAssessmentService, UploadAttemptService}
import warwick.core.helpers.ServiceResults.Implicits._
import warwick.core.helpers.ServiceResults.ServiceResult
import warwick.core.helpers.{JavaTime, ServiceResults}
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
    announcementService: AnnouncementService,
    uploadAttemptService: UploadAttemptService,
    additionalTopics: Set[String],
  )(implicit ec: ExecutionContext, ac: AuditLogContext): Props =
    Props(new WebSocketActor(out, pubsub, loginContext, ac.ipAddress, ac.userAgent, studentAssessmentService, assessmentClientNetworkActivityService, announcementService, uploadAttemptService, additionalTopics))

  import UploadAttempt._

  case class AssessmentAnnouncement(id: String, assessmentId: String, messageText: String, timestamp: OffsetDateTime) {
    val messageHTML: Html = Html(warwick.core.views.utils.nl2br(messageText).body)
  }

  object AssessmentAnnouncement {
    def from(announcement: Announcement): AssessmentAnnouncement =
      AssessmentAnnouncement(announcement.id.toString, announcement.assessment.toString, announcement.text, announcement.created)
  }

  case class AssessmentMessage(id: String, assessmentId: String, messageText: String, sender: MessageSender, client: String, timestamp: OffsetDateTime) {
    val messageHTML: Html = Html(warwick.core.views.utils.nl2br(messageText).body)
  }

  case class ClientMessage(
    `type`: String,
    data: Option[JsValue],
  )
  val readsClientMessage: Reads[ClientMessage] = Json.reads[ClientMessage]

  object ClientMessage {
    // For pattern matching the top ClientMessage
    object fromJson {
      def unapply(in: JsValue): Option[ClientMessage] = in.asOpt(readsClientMessage)
    }
  }

  val readsClientNetworkInformation: Reads[ClientNetworkInformation] = Json.reads[ClientNetworkInformation]

  case class RequestAssessmentTiming(
    assessmentId: UUID
  )
  object RequestAssessmentTiming {
    val `type`: String = "RequestAssessmentTiming"
    implicit val reads: Reads[RequestAssessmentTiming] = Json.reads[RequestAssessmentTiming]
  }

  case class RequestAnnouncements(
    assessmentId: Option[UUID]
  )
  object RequestAnnouncements {
    val `type`: String = "RequestAnnouncements"
    implicit val reads: Reads[RequestAnnouncements] = Json.reads[RequestAnnouncements]
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
  ipAddress: Option[String],
  userAgent: Option[String],
  @Assisted studentAssessmentService: StudentAssessmentService,
  @Assisted assessmentClientNetworkActivityService: AssessmentClientNetworkActivityService,
  @Assisted announcementService: AnnouncementService,
  @Assisted uploadAttemptService: UploadAttemptService,
  additionalTopics: Set[String],
)(implicit
  ec: ExecutionContext
) extends Actor with ActorLogging {

  import WebSocketActor._

  lazy val currentUsercode: Usercode = loginContext.user.get.usercode
  lazy val currentUniversityID: UniversityID = loginContext.user.flatMap(_.universityId).get

  // Keep this as a def to get a new timingData every time, so it doesn't grow forever
  implicit def auditLogContext: AuditLogContext = AuditLogContext(
    usercode = Some(currentUsercode),
    ipAddress = ipAddress,
    userAgent = userAgent,
    timingData = new TimingContext.Data()
  )

  pubsubSubscribe()

  override def postStop(): Unit = {
    pubsubUnsubscribe()
  }

  override def receive: Receive = {
    case aa: AssessmentAnnouncement => out ! Json.obj(
      "type" -> "announcement",
      "id" -> aa.id,
      "assessmentId" -> aa.assessmentId,
      "messageHTML" -> aa.messageHTML.body,
      "messageText" -> aa.messageText,
      "timestamp" -> views.html.tags.localisedDatetime(aa.timestamp).toString,
    )

    case am: AssessmentMessage => out ! Json.obj(
      "type" -> "assessmentMessage",
      "id" -> am.id,
      "assessmentId" -> am.assessmentId,
      "messageHTML" -> am.messageHTML.body,
      "messageText" -> am.messageText,
      "timestamp" -> views.html.tags.localisedDatetime(am.timestamp).toString,
      "sender" -> am.sender.entryName,
      "client" -> am.client
    )

    case uploadAttempt: UploadAttempt =>
      uploadAttempt.source = "WebSocket"
      uploadAttemptService.logAttempt(uploadAttempt)

    case SubscribeAck(Subscribe(topic, _, _)) =>
      log.debug(s"WebSocket subscribed to PubSub messages on the topic of '$topic'")

    case ClientMessage.fromJson(message) =>
      message match {
        case m if m.`type` == "NetworkInformation" && m.data.exists(_.validate[ClientNetworkInformation](readsClientNetworkInformation).isSuccess) =>
          val networkInformation = m.data.get.as[ClientNetworkInformation](readsClientNetworkInformation)

          val assessmentClientNetworkActivity =
            AssessmentClientNetworkActivity(
              // ignore junk values over 10gbps
              downlink = networkInformation.downlink.filter(_ <= 10000.0),
              downlinkMax = networkInformation.downlinkMax.filter(_ <= 10000.0),
              effectiveType = networkInformation.effectiveType,
              // ignore some browsers that send an impossible RTT of 0ms
              rtt = networkInformation.rtt.filter(_ > 0),
              `type` = networkInformation.`type`,
              studentAssessmentId = networkInformation.studentAssessmentId,
              assessmentId = networkInformation.assessmentId,
              usercode = loginContext.user.map(_.usercode),
              localTimezoneName = networkInformation.localTimezoneName.map(_.maybeZoneId),
              timestamp = JavaTime.offsetDateTime,
            )

          if (networkInformation.studentAssessmentId.nonEmpty || networkInformation.assessmentId.nonEmpty) {
            assessmentClientNetworkActivityService.record(assessmentClientNetworkActivity)
              .recover {
                case e: Exception =>
                  log.error(e, s"Error storing AssessmentClientNetworkActivity for ${if(networkInformation.studentAssessmentId.nonEmpty) s"StudentAssessment ${assessmentClientNetworkActivity.studentAssessmentId}" else s"Assessment ${assessmentClientNetworkActivity.assessmentId}"}")
              }
          }

          out ! Json.obj(
            "type" -> "UpdateConnectivityIndicator",
            "signalStrength" -> assessmentClientNetworkActivity.signalStrength
          )

        case m if m.`type` == RequestAssessmentTiming.`type` =>
          val universityID: UniversityID = currentUniversityID
          val request: Option[RequestAssessmentTiming] = m.data.flatMap(_.asOpt[RequestAssessmentTiming])
          val requestedAssessmentId: Option[UUID] = request.map(_.assessmentId)

          // Get single assessment or all user's assessments, depending on what was requested
          val getAssessments: Future[ServiceResult[Seq[SittingMetadata]]] = requestedAssessmentId.map { id =>
            studentAssessmentService.getSittingsMetadata(universityID, id).successMapTo(a => Seq(a))
          }.getOrElse {
            studentAssessmentService.getSittingsMetadata(universityID)
          }

          getAssessments.successMapTo { assessments =>
            out ! Json.obj(
              "type"-> "AssessmentTimingInformation",
              "now"-> JavaTime.instant.toEpochMilli,
              "assessments"-> assessments.map(a => Json.toJson(a.getTimingInfo))
            )
          }

        case m if m.`type` == RequestAnnouncements.`type` =>
          val universityID: UniversityID = currentUniversityID
          m.data.getOrElse(Json.obj()).validate[RequestAnnouncements].fold(
            invalid => log.error(s"Failed to parse RequestAnnouncements message: $invalid"),
            {
              case RequestAnnouncements(Some(assessmentId)) =>
                ServiceResults.zip(
                  studentAssessmentService.get(universityID, assessmentId),
                  announcementService.getByAssessmentId(universityID, assessmentId)
                ).foreach { result =>
                  result.fold(
                    errors => log.error(s"Error fetching announcments: $errors"),
                    { case (student, announcements) =>
                      if (student.startTime.nonEmpty) {
                        announcements.foreach { announcement =>
                          self ! AssessmentAnnouncement.from(announcement)
                        }
                      }
                    }
                  )
                }

              case RequestAnnouncements(None) => // do nothing
            }
          )

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
