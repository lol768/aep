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
import warwick.core.helpers.JavaTime
import warwick.sso.{LoginContext, Usercode}

import scala.concurrent.{ExecutionContext, Future}

object WebSocketController {
  case class SendBroadcastForm(
    user: Usercode,
    message: String
  ) {
    def toAnnouncement: AssessmentAnnouncement = {
      AssessmentAnnouncement(message, JavaTime.offsetDateTime)
    }
  }

  val broadcastForm: Form[SendBroadcastForm] = Form(mapping(
    "user" -> nonEmptyText.transform[Usercode](s => Usercode(s), u => u.string),
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
}

@Singleton
class WebSocketController @Inject()(
  securityService: SecurityService,
  system: ActorSystem,
  pubSub: PubSubService,
  studentAssessmentService: StudentAssessmentService,
  assessmentClientNetworkActivityService: AssessmentClientNetworkActivityService,
  assessmentService: AssessmentService,
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
                .successMapTo(_.map(_.assessment.id.toString))
                .map(_.getOrElse(Nil)).zip(
                  assessmentService.listForInvigilator(Set(user.usercode))
                    .successMapTo(_.map(_.id.toString))
                    .map(_.getOrElse(Nil))
            ).map { case (relatedStudentAssessmentIds, relatedInvigilatorAssessmentIds) =>
              ActorFlow.actorRef(out => WebSocketActor.props(
                loginContext = loginContext,
                pubsub = pubSubActor,
                out = out,
                studentAssessmentService = studentAssessmentService,
                assessmentClientNetworkActivityService = assessmentClientNetworkActivityService,
                additionalTopics =
                  (relatedStudentAssessmentIds.map(id => s"studentAssessment:$id") ++
                    relatedInvigilatorAssessmentIds.map(id => s"invigilatorAssessment:$id")).toSet
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

  def sendBroadcast: Action[AnyContent] = RequireSysadmin { implicit request =>
    broadcastForm.bindFromRequest().fold(
      _ => BadRequest,
      data => {
        pubSub.publish(data.user.string, data.toAnnouncement)
        Redirect(controllers.routes.WebSocketController.sendBroadcast())
          .flashing("success" -> Messages("flash.websocket.published"))
      }
    )
  }

  def sendAnnouncement: Action[AnyContent] = RequireSysadmin { implicit request =>
    assessmentAnnouncementForm.bindFromRequest().fold(
      _ => BadRequest,
      data => {
        pubSub.publish(
          data.assessment.toString,
          AssessmentAnnouncement(data.message, JavaTime.offsetDateTime),
        )
        Redirect(controllers.routes.WebSocketController.sendBroadcast())
          .flashing("success" -> Messages("flash.websocket.published"))

      }
    )
  }
}
