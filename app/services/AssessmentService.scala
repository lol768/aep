package services

import java.util.UUID

import akka.Done
import com.google.inject.ImplementedBy
import domain.Assessment
import domain.Assessment.State
import domain.dao.AssessmentsTables.{StoredAssessment, StoredBrief}
import domain.dao.{AssessmentDao, AssessmentsTables, DaoRunner}
import javax.inject.{Inject, Singleton}
import warwick.core.helpers.ServiceResults
import warwick.core.helpers.ServiceResults.ServiceResult
import warwick.core.system.AuditLogContext
import warwick.core.timing.TimingContext

import scala.concurrent.{ExecutionContext, Future}

@ImplementedBy(classOf[AssessmentServiceImpl])
trait AssessmentService {
  def list(implicit t: TimingContext): Future[ServiceResult[Seq[Assessment]]]
  def findByStates(state: Seq[State])(implicit t: TimingContext): Future[ServiceResult[Seq[Assessment]]]
  def getByIds(ids: Seq[UUID])(implicit t: TimingContext): Future[ServiceResult[Seq[Assessment]]]
  def get(id: UUID)(implicit t: TimingContext): Future[ServiceResult[Assessment]]
  def update(assessment: Assessment)(implicit ac: AuditLogContext): Future[ServiceResult[Done]]
}

@Singleton
class AssessmentServiceImpl @Inject()(
  auditService: AuditService,
  daoRunner: DaoRunner,
  dao: AssessmentDao,
  uploadedFileService: UploadedFileService
)(implicit ec: ExecutionContext) extends AssessmentService {

  private def inflate(storedAssessments: Seq[AssessmentsTables.StoredAssessment])(implicit t: TimingContext) = {
    uploadedFileService.get(storedAssessments.flatMap(_.storedBrief.fileIds)).map { uploadedFiles =>
      ServiceResults.success(storedAssessments.map(_.asAssessment(uploadedFiles.map(f => f.id -> f).toMap)))
    }
  }

  override def list(implicit t: TimingContext): Future[ServiceResult[Seq[Assessment]]] = {
    daoRunner.run(dao.all).flatMap(inflate)
  }

  override def findByStates(state: Seq[State])(implicit t: TimingContext): Future[ServiceResult[Seq[Assessment]]] = {
    daoRunner.run(dao.findByStates(state)).flatMap(inflate)
  }

  override def getByIds(ids: Seq[UUID])(implicit t: TimingContext): Future[ServiceResult[Seq[Assessment]]] = {
    daoRunner.run(dao.getByIds(ids)).flatMap(inflate)
  }

  override def get(id: UUID)(implicit t: TimingContext): Future[ServiceResult[Assessment]] = {
    daoRunner.run(dao.getById(id)).flatMap { storedAssessmentOption =>
      storedAssessmentOption.map { storedAssessment =>
        uploadedFileService.get(storedAssessment.storedBrief.fileIds).map { uploadedFiles =>
          ServiceResults.success(storedAssessment.asAssessment(uploadedFiles.map(f => f.id -> f).toMap))
        }
      }.getOrElse {
        Future.successful(ServiceResults.error(s"Could not find an Assessment with ID $id"))
      }
    }.recover {
      case _: NoSuchElementException => ServiceResults.error(s"Could not find an Assessment with ID $id")
    }
  }

  override def update(assessment: Assessment)(implicit ac: AuditLogContext): Future[ServiceResult[Done]] = {
    daoRunner.run(for {
      stored <- dao.getById(assessment.id)
      result <- dao.update(stored.copy(
        code = assessment.code,
        title = assessment.title,
        startTime = assessment.startTime,
        duration = assessment.duration,
        platform = assessment.platform,
        assessmentType = assessment.assessmentType,
        storedBrief = StoredBrief(
          text = assessment.brief.text,
          fileIds = assessment.brief.files.map(_.id),
          url = assessment.brief.url
        ),
        state = assessment.state
      ))
    } yield Done).map(ServiceResults.success)
  }
}
