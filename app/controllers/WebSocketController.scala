package controllers

import java.util.UUID

import actors.WebSocketActor.AssessmentAnnouncement
import actors.{PubSubActor, WebSocketActor}
import akka.actor.ActorSystem
import akka.stream.Materializer
import controllers.WebSocketController._
import javax.inject.{Inject, Singleton}
import play.api.data.Form
import play.api.data.Forms._
import play.api.i18n.Messages
import play.api.libs.json.JsValue
import play.api.libs.streams.ActorFlow
import play.api.mvc.{Action, AnyContent, Results, WebSocket}
import services._
import system.Features
import warwick.core.helpers.JavaTime
import warwick.sso.{LoginContext, UniversityID, Usercode}

import scala.concurrent.{ExecutionContext, Future}

object WebSocketController {
  case class SendBroadcastForm(
    user: Usercode,
    assessmentId: UUID,
    message: String
  ) {
    /** Just for testing as it uses an ephemeral ID */
    def toTestAnnouncement: AssessmentAnnouncement = {
      AssessmentAnnouncement(UUID.randomUUID.toString, assessmentId.toString, None, message, JavaTime.offsetDateTime)
    }
  }

  val broadcastForm: Form[SendBroadcastForm] = Form(mapping(
    "user" -> nonEmptyText.transform[Usercode](s => Usercode(s), u => u.string),
    "assessmentId" -> uuid,
    "message" -> nonEmptyText,
  )(SendBroadcastForm.apply)(SendBroadcastForm.unapply))

  case class SendAssessmentAnnouncementForm(
    assessment: UUID,
    message: String
  )

  val assessmentAnnouncementForm: Form[SendAssessmentAnnouncementForm] = Form(mapping(
    "assessment" -> uuid,
    "message" -> nonEmptyText,
  )(SendAssessmentAnnouncementForm.apply)(SendAssessmentAnnouncementForm.unapply))

  object Topics {
    def allStudentsAssessment(assessmentId: UUID): String = s"studentAssessment:${assessmentId.toString}"
    def studentAssessment(universityId: UniversityID)(assessmentId: UUID): String = s"studentAssessment:${assessmentId.toString}:${universityId.string}"
    def allInvigilatorsAssessment(assessmentId: UUID): String = s"invigilatorAssessment:${assessmentId.toString}"
  }
}

@Singleton
class WebSocketController @Inject()(
  securityService: SecurityService,
  system: ActorSystem,
  pubSub: PubSubService,
  studentAssessmentService: StudentAssessmentService,
  assessmentClientNetworkActivityService: AssessmentClientNetworkActivityService,
  assessmentService: AssessmentService,
  announcementService: AnnouncementService,
  uploadAttemptService: UploadAuditingService,
  features: Features,
  timingInfo: TimingInfoService,
)(implicit
  mat: Materializer,
  actorSystem: ActorSystem,
  ec: ExecutionContext
) extends BaseController {

  import securityService._

  // Adapted from My Warwick

  // This actor lives as long as the controller
  private val pubSubActor = system.actorOf(PubSubActor.props())

  def socket: WebSocket = WebSocket.acceptOrResult[JsValue, JsValue] { implicit request =>
    if (securityService.isOriginSafe(request.headers("Origin"))) {
      SecureWebsocket(request) { loginContext: LoginContext =>
        loginContext.user match {
          case Some(user) =>
            logger.info(s"WebSocket opening for ${user.usercode.string}")
            studentAssessmentService.byUniversityId(user.universityId.get)
                .successMapTo(_.filter(_.inProgress(timingInfo.lateSubmissionPeriod)).map(_.assessment.id))
                .map(_.getOrElse(Nil)).zip(
                  assessmentService.listForInvigilator(Set(user.usercode))
                    .successMapTo(_.map(_.id))
                    .map(_.getOrElse(Nil))
            ).map { case (relatedStudentAssessmentIds, relatedInvigilatorAssessmentIds) =>
              ActorFlow.actorRef(out => WebSocketActor.props(
                loginContext = loginContext,
                pubsub = pubSubActor,
                out = out,
                studentAssessmentService = studentAssessmentService,
                assessmentClientNetworkActivityService = assessmentClientNetworkActivityService,
                announcementService = announcementService,
                uploadAttemptService = uploadAttemptService,
                features = features,
                messages = request2Messages,
                timingInfo = timingInfo,
                additionalTopics = (
                  // Things relevant ao ALL student on the assessment e.g. announcements
                  relatedStudentAssessmentIds.map(Topics.allStudentsAssessment) ++
                  // Things relevant to this specific student on each assessment e.g. messages from invigilators
                  relatedStudentAssessmentIds.map(Topics.studentAssessment(user.universityId.get)) ++
                  // Things relevant to all invigilators e.g. announcements and messages
                  relatedInvigilatorAssessmentIds.map(Topics.allInvigilatorsAssessment)
                  ).toSet
              ))
            }.map(Right.apply)
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

  def broadcastTest: Action[AnyContent] = RequireSysadmin { implicit request =>
    Ok(views.html.sysadmin.broadcastTest(broadcastForm, assessmentAnnouncementForm))
  }

  def sendTestToUser: Action[AnyContent] = RequireSysadmin { implicit request =>
    broadcastForm.bindFromRequest().fold(
      _ => BadRequest,
      data => {
        pubSub.publish(data.user.string, data.toTestAnnouncement)
        Redirect(controllers.routes.WebSocketController.broadcastTest())
          .flashing("success" -> Messages("flash.websocket.published"))
      }
    )
  }

  def sendTestToAssessment: Action[AnyContent] = RequireSysadmin { implicit request =>
    assessmentAnnouncementForm.bindFromRequest().fold(
      _ => BadRequest,
      data => {
        pubSub.publish(
          Topics.allInvigilatorsAssessment(data.assessment),
          AssessmentAnnouncement(UUID.randomUUID.toString, data.assessment.toString, None, data.message, JavaTime.offsetDateTime),
        )
        pubSub.publish(
          Topics.allStudentsAssessment(data.assessment),
          AssessmentAnnouncement(UUID.randomUUID.toString, data.assessment.toString, None, data.message, JavaTime.offsetDateTime),
        )
        Redirect(controllers.routes.WebSocketController.broadcastTest())
          .flashing("success" -> Messages("flash.websocket.published"))

      }
    )
  }
}
