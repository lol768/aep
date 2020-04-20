package services

import java.time.OffsetDateTime
import java.util.UUID

import actors.WebSocketActor.AssessmentAnnouncement
import akka.Done
import com.google.inject.ImplementedBy
import domain.Announcement
import domain.AuditEvent.{Operation, Target}
import domain.dao.AnnouncementsTables.StoredAnnouncement
import domain.dao.{AnnouncementDao, DaoRunner}
import javax.inject.{Inject, Singleton}
import play.api.libs.json.Json
import system.routes.Types.UniversityID
import warwick.core.helpers.ServiceResults
import warwick.core.helpers.ServiceResults.ServiceResult
import warwick.core.system.{AuditLogContext, AuditService}
import warwick.core.timing.TimingContext
import warwick.sso.UserLookupService

import scala.concurrent.{ExecutionContext, Future}

@ImplementedBy(classOf[AnnouncementServiceImpl])
trait AnnouncementService {
  def list(implicit t: TimingContext): Future[ServiceResult[Seq[Announcement]]]
  def get(id: UUID)(implicit t: TimingContext): Future[ServiceResult[Announcement]]
  def getByAssessmentId(id: UUID)(implicit t: TimingContext): Future[ServiceResult[Seq[Announcement]]]
  def getByAssessmentId(student: UniversityID, id: UUID)(implicit t: TimingContext): Future[ServiceResult[Seq[Announcement]]]
  def save(announcement: Announcement)(implicit ac: AuditLogContext): Future[ServiceResult[Done]]
}

@Singleton
class AnnouncementServiceImpl @Inject()(
  auditService: AuditService,
  daoRunner: DaoRunner,
  dao: AnnouncementDao,
  pubSubService: PubSubService,
  notificationService: NotificationService,
  userLookupService: UserLookupService,
)(implicit ec: ExecutionContext) extends AnnouncementService {

  override def save(announcement: Announcement)(implicit ac: AuditLogContext): Future[ServiceResult[Done]] =
    auditService.audit(Operation.Assessment.MakeAnnouncement, announcement.assessment.toString, Target.Assessment, Json.obj("text" -> announcement.text)) {
      val stored = StoredAnnouncement(
        id = announcement.id,
        sender = announcement.sender,
        assessmentId = announcement.assessment,
        text = announcement.text,
        created = announcement.created,
        version = OffsetDateTime.now()
      )

      // publish announcement to students
      pubSubService.publish(
        topic = s"studentAssessment:${announcement.assessment.toString}",
        AssessmentAnnouncement.from(announcement)
      )

      // publish announcement to invigilators
      announcement.sender.foreach { sender =>
        userLookupService.getUsers(Seq(sender)).toOption.flatMap(userMap => userMap.headOption.map(_._2)).foreach { user =>
          val name = user.name.full.map(name => s"${name}: ").getOrElse("")
          pubSubService.publish(
            topic = s"invigilatorAssessment:${announcement.assessment.toString}",
            AssessmentAnnouncement(announcement.id.toString, announcement.assessment.toString, s"${name}${announcement.text.trim}", announcement.created)
          )
        }
      }

      // Intentionally fire-and-forget to send the announcement via My Warwick as well
      notificationService.newAnnouncement(announcement)

      daoRunner.run(dao.insert(stored)).map(_ => ServiceResults.success(Done))
    }

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

  override def getByAssessmentId(student: UniversityID, id: UUID)(implicit t: TimingContext): Future[ServiceResult[Seq[Announcement]]] =
    daoRunner.run(dao.getByAssessmentId(student, id)).map(_.map(_.asAnnouncement)).map(ServiceResults.success)
}
