package services

import java.time.OffsetDateTime
import java.util.UUID

import actors.WebSocketActor.AssessmentAnnouncementForUniversityIds
import akka.Done
import com.google.inject.ImplementedBy
import domain.Announcement
import domain.dao.AnnouncementsTables.StoredAnnouncement
import domain.dao.{AnnouncementDao, DaoRunner}
import javax.inject.{Inject, Singleton}
import warwick.core.helpers.ServiceResults
import warwick.core.helpers.ServiceResults.ServiceResult
import warwick.core.system.AuditLogContext
import warwick.core.timing.TimingContext
import warwick.core.helpers.ServiceResults.Implicits._
import warwick.sso.UniversityID

import scala.concurrent.{ExecutionContext, Future}

@ImplementedBy(classOf[AnnouncementServiceImpl])
trait AnnouncementService {
  def list(implicit t: TimingContext): Future[ServiceResult[Seq[Announcement]]]
  def get(id: UUID)(implicit t: TimingContext): Future[ServiceResult[Announcement]]
  def getByAssessmentId(id: UUID)(implicit t: TimingContext): Future[ServiceResult[Seq[Announcement]]]
  def save(announcement: Announcement)(implicit ac: AuditLogContext): Future[ServiceResult[Done]]
  def delete(id: UUID)(implicit t: TimingContext): Future[ServiceResult[Done]]
}

@Singleton
class AnnouncementServiceImpl @Inject()(
  auditService: AuditService,
  daoRunner: DaoRunner,
  dao: AnnouncementDao,
  pubSubService: PubSubService,
  studentAssessmentService: StudentAssessmentService,
)(implicit ec: ExecutionContext) extends AnnouncementService {

  override def save(announcement: Announcement)(implicit ac: AuditLogContext): Future[ServiceResult[Done]] = {
    val stored = StoredAnnouncement(
      id = announcement.id,
      assessmentId = announcement.assessment,
      text = announcement.text,
      created = OffsetDateTime.now(),
      version = OffsetDateTime.now()
    )

    daoRunner.run(dao.insert(stored)).map(_ => ServiceResults.success(Done))

    pubSubService.subscribe(
      topic = announcement.id.toString,
      group = None,
    )

    for {
      affectedStudents <- studentAssessmentService
        .byAssessmentId(announcement.id)
        .successMapTo(_.map(_.studentId))
        .map(_.getOrElse(Nil))
    } yield {

      pubSubService.publish(
        topic = announcement.id.toString,
        AssessmentAnnouncementForUniversityIds(
          message = announcement.text,
          universityIds = affectedStudents,
        )
      )
      ServiceResults.success(Done)
    }
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
