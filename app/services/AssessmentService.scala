package services

import java.util.UUID

import com.google.common.io.ByteSource
import com.google.inject.ImplementedBy
import domain.Assessment.State
import domain.dao.AssessmentsTables.{StoredAssessment, StoredBrief}
import domain.dao.{AssessmentDao, AssessmentsTables, DaoRunner, UploadedFilesTables}
import domain.{Assessment, UploadedFileOwner}
import domain.Assessment
import domain.dao.AssessmentsTables.StoredAssessment
import domain.dao.{AssessmentDao, AssessmentsTables, DaoRunner}
import javax.inject.{Inject, Singleton}
import services.AssessmentService._
import slick.dbio.DBIO
import warwick.core.helpers.ServiceResults
import warwick.core.helpers.{JavaTime, ServiceResults}
import warwick.core.helpers.ServiceResults.ServiceResult
import warwick.core.system.AuditLogContext
import warwick.core.timing.TimingContext
import warwick.fileuploads.UploadedFileSave

import scala.concurrent.{ExecutionContext, Future}

@ImplementedBy(classOf[AssessmentServiceImpl])
trait AssessmentService {
  def list(implicit t: TimingContext): Future[ServiceResult[Seq[Assessment]]]
  def findByStates(state: Seq[State])(implicit t: TimingContext): Future[ServiceResult[Seq[Assessment]]]
  def getByIds(ids: Seq[UUID])(implicit t: TimingContext): Future[ServiceResult[Seq[Assessment]]]
  def get(id: UUID)(implicit t: TimingContext): Future[ServiceResult[Assessment]]
  def update(assessment: Assessment, files: Seq[(ByteSource, UploadedFileSave)])(implicit ac: AuditLogContext): Future[ServiceResult[Assessment]]
  def upsert(assessment: Assessment)(implicit ctx: AuditLogContext): Future[ServiceResult[Assessment]]
}

@Singleton
class AssessmentServiceImpl @Inject()(
  auditService: AuditService,
  daoRunner: DaoRunner,
  dao: AssessmentDao,
  uploadedFileService: UploadedFileService,
)(implicit ec: ExecutionContext) extends AssessmentService {
  override def list(implicit t: TimingContext): Future[ServiceResult[Seq[Assessment]]] = {
    daoRunner.run(dao.loadAllWithUploadedFiles)
      .map(inflateRowsWithUploadedFiles)
      .map(ServiceResults.success)
  }

  override def findByStates(state: Seq[State])(implicit t: TimingContext): Future[ServiceResult[Seq[Assessment]]] =
    daoRunner.run(dao.findByStatesWithUploadedFiles(state))
      .map(inflateRowsWithUploadedFiles)
      .map(ServiceResults.success)

  override def getByIds(ids: Seq[UUID])(implicit t: TimingContext): Future[ServiceResult[Seq[Assessment]]] =
    daoRunner.run(dao.loadByIdsWithUploadedFiles(ids))
      .map(inflateRowsWithUploadedFiles)
      .map(ServiceResults.success)

  override def get(id: UUID)(implicit t: TimingContext): Future[ServiceResult[Assessment]] =
    daoRunner.run(dao.loadByIdWithUploadedFiles(id))
      .map(inflateRowWithUploadedFiles)
      .map(_.fold[ServiceResult[Assessment]](ServiceResults.error(s"Could not find an Assessment with ID $id"))(ServiceResults.success))

  override def update(assessment: Assessment, files: Seq[(ByteSource, UploadedFileSave)])(implicit ac: AuditLogContext): Future[ServiceResult[Assessment]] = {
    daoRunner.run(for {
      stored <- dao.getById(assessment.id).map(_.getOrElse(throw new NoSuchElementException(s"Could not find an Assessment with ID ${assessment.id}")))
      fileIds <- if (files.nonEmpty) {
        DBIO.sequence(files.toList.map { case (in, metadata) =>
          uploadedFileService.storeDBIO(
            in,
            metadata,
            ac.usercode.get,
            assessment.id,
            UploadedFileOwner.Assessment
          ).map(_.id)
        })
      } else DBIO.successful(assessment.brief.files.map(_.id))
      _ <- dao.update(StoredAssessment(
        id = assessment.id,
        code = assessment.code,
        title = assessment.title,
        startTime = assessment.startTime,
        duration = assessment.duration,
        platform = assessment.platform,
        assessmentType = assessment.assessmentType,
        storedBrief = StoredBrief(
          text = assessment.brief.text,
          fileIds = fileIds,
          url = assessment.brief.url
        ),
        state = assessment.state,
        created = stored.created,
        version = stored.version
      ))
      updated <- dao.loadByIdWithUploadedFiles(assessment.id)
    } yield updated).map(inflateRowWithUploadedFiles(_).get).map(ServiceResults.success)
  }

  def upsert(assessment: Assessment)(implicit ctx: AuditLogContext): Future[ServiceResult[Assessment]] = {
    daoRunner.run(dao.getById(assessment.id)).flatMap { storedAssessmentOption =>
      storedAssessmentOption.map { existingAssessment =>
        daoRunner.run(dao.update(existingAssessment.copy(
          code = assessment.code,
          title = assessment.title,
          startTime = assessment.startTime,
          duration = assessment.duration,
          platform = assessment.platform,
          assessmentType = assessment.assessmentType,
          storedBrief = assessment.brief.toStoredBrief,
        )))
      }.getOrElse {
        val timestamp = JavaTime.offsetDateTime
        daoRunner.run(dao.insert(StoredAssessment(
          id = assessment.id,
          code = assessment.code,
          title = assessment.title,
          startTime = assessment.startTime,
          duration = assessment.duration,
          platform = assessment.platform,
          assessmentType = assessment.assessmentType,
          storedBrief = assessment.brief.toStoredBrief,
          state = assessment.state,
          created = timestamp,
          version = timestamp
        )))
      }.flatMap { result =>
        uploadedFileService.get(result.storedBrief.fileIds).map { files =>
          ServiceResults.success(result.asAssessment(files.map(f => f.id -> f).toMap))
        }.recoverWith {
          case e:Exception => Future.successful(ServiceResults.error(e.getMessage))
        }
      }
    }
  }
}

object AssessmentService {
  def inflateRowsWithUploadedFiles(rows: Seq[(AssessmentsTables.StoredAssessment, Set[UploadedFilesTables.StoredUploadedFile])]): Seq[Assessment] =
    rows.map { case (assessment, storedUploadedFiles) =>
      assessment.asAssessment(
        storedUploadedFiles.map(f => f.id -> f.asUploadedFile).toMap
      )
    }


  def inflateRowWithUploadedFiles(row: Option[(AssessmentsTables.StoredAssessment, Set[UploadedFilesTables.StoredUploadedFile])]): Option[Assessment] =
    inflateRowsWithUploadedFiles(row.toSeq).headOption
}
