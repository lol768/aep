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
import warwick.core.helpers.ServiceResults
import warwick.core.helpers.ServiceResults.ServiceResult
import warwick.core.system.AuditLogContext
import warwick.core.timing.TimingContext

import scala.concurrent.{ExecutionContext, Future}

@ImplementedBy(classOf[AnnouncementServiceImpl])
trait AnnouncementService {
  def list(implicit t: TimingContext): Future[ServiceResult[Seq[Announcement]]]
  def get(id: UUID)(implicit t: TimingContext): Future[ServiceResult[Announcement]]
  def getByAssessmentId(id: UUID)(implicit t: TimingContext): Future[ServiceResult[Seq[Announcement]]]
  def save(announcement: Announcement)(implicit ac: AuditLogContext): Future[ServiceResult[Done]]
}

@Singleton
class AnnouncementServiceImpl @Inject()(
  auditService: AuditService,
  daoRunner: DaoRunner,
  dao: AnnouncementDao,
  pubSubService: PubSubService,
  studentAssessmentService: StudentAssessmentService,
)(implicit ec: ExecutionContext) extends AnnouncementService {

  override def save(announcement: Announcement)(implicit ac: AuditLogContext): Future[ServiceResult[Done]] =
    auditService.audit(Operation.Assessment.MakeAnnouncement, announcement.assessment.toString, Target.Assessment, Json.obj("text" -> announcement.text)) {
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
}
