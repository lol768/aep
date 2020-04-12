package services

import java.time.OffsetDateTime
import java.util.UUID

import actors.WebSocketActor.AssessmentAnnouncement
import akka.Done
import com.google.inject.ImplementedBy
import domain.Announcement
import domain.dao.AnnouncementsTables.StoredAnnouncement
import domain.dao.{AnnouncementDao, DaoRunner}
import javax.inject.{Inject, Singleton}
import org.quartz.Scheduler
import play.api.mvc.RequestHeader
import uk.ac.warwick.util.mywarwick.MyWarwickService
import uk.ac.warwick.util.mywarwick.model.request.Activity
import warwick.core.helpers.ServiceResults
import warwick.core.helpers.ServiceResults.Implicits._
import warwick.core.helpers.ServiceResults.ServiceResult
import warwick.core.system.AuditLogContext
import warwick.core.timing.TimingContext
import warwick.sso.UserLookupService

import scala.concurrent.{ExecutionContext, Future}
import scala.jdk.CollectionConverters._

@ImplementedBy(classOf[AnnouncementServiceImpl])
trait AnnouncementService {
  def list(implicit t: TimingContext): Future[ServiceResult[Seq[Announcement]]]
  def get(id: UUID)(implicit t: TimingContext): Future[ServiceResult[Announcement]]
  def getByAssessmentId(id: UUID)(implicit t: TimingContext): Future[ServiceResult[Seq[Announcement]]]
  def save(announcement: Announcement)(implicit ac: AuditLogContext, request: RequestHeader): Future[ServiceResult[Done]]
  def delete(id: UUID)(implicit t: TimingContext): Future[ServiceResult[Done]]
}

@Singleton
class AnnouncementServiceImpl @Inject()(
  auditService: AuditService,
  daoRunner: DaoRunner,
  dao: AnnouncementDao,
  pubSubService: PubSubService,
  assessmentService: AssessmentService,
  studentAssessmentService: StudentAssessmentService,
  myWarwickService: MyWarwickService,
  scheduler: Scheduler,
  userLookupService: UserLookupService,
)(implicit ec: ExecutionContext) extends AnnouncementService {

  override def save(announcement: Announcement)(implicit ac: AuditLogContext, request: RequestHeader): Future[ServiceResult[Done]] = {
    val stored = StoredAnnouncement(
      id = announcement.id,
      assessmentId = announcement.assessment,
      text = announcement.text,
      created = announcement.created,
      version = OffsetDateTime.now()
    )

    pubSubService.publish(
      topic = announcement.assessment.toString,
      AssessmentAnnouncement(announcement.text, announcement.created)
    )

    // Intentionally fire-and-forget to send the announcement via My Warwick as well
    ServiceResults.zip(
      assessmentService.get(announcement.assessment),
      studentAssessmentService.byAssessmentId(announcement.assessment),
    ).successMapTo { case (assessment, students) =>
      val universityIds = students.map(_.studentId)
      userLookupService.getUsers(universityIds).toOption.foreach { users =>
        val usercodes = users.values.map(_.usercode.string).toSet

        myWarwickService.queueNotification(
          new Activity(
            usercodes.asJava,
            s"${assessment.paperCode} ${assessment.title}: Announcement",
            controllers.routes.AssessmentController.view(assessment.id).absoluteURL(),
            announcement.text,
            "assessment-announcement"
          ),
          scheduler
        )
      }
    }

    daoRunner.run(dao.insert(stored)).map(_ => ServiceResults.success(Done))
  }

  override def delete(id: UUID)(implicit t: TimingContext): Future[ServiceResult[Done]] =
    daoRunner.run(dao.delete(id)).map(_ => ServiceResults.success(Done))

  override def list(implicit t: TimingContext): Future[ServiceResult[Seq[Announcement]]] =
    daoRunner.run(dao.all).map(_.map(_.asAnnouncement)).map(ServiceResults.success)

  override def get(id: UUID)(implicit t: TimingContext): Future[ServiceResult[Announcement]] =
    daoRunner.run(dao.getById(id).map{ _.map { a =>
      ServiceResults.success(a.asAnnouncement)
    }.getOrElse {
      ServiceResults.error(s"Could not find announcement with id ${id.toString}")
    }})

  override def getByAssessmentId(id: UUID)(implicit t: TimingContext): Future[ServiceResult[Seq[Announcement]]] =
  daoRunner.run(dao.getByAssessmentId(id)).map(_.map(_.asAnnouncement)).map(ServiceResults.success)
}
