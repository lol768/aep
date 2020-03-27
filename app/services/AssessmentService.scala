package services

import java.util.UUID

import com.google.common.io.ByteSource
import com.google.inject.ImplementedBy
import domain.Assessment.State
import domain.dao.AssessmentsTables.{StoredAssessment, StoredBrief}
import domain.dao.{AssessmentDao, AssessmentsTables, DaoRunner, UploadedFilesTables}
import domain.{Assessment, OneToMany, UploadedFileOwner}
import javax.inject.{Inject, Singleton}
import services.AssessmentService._
import slick.dbio.DBIO
import warwick.core.helpers.ServiceResults
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
      .map(inflateRowsWithUploadedFiles(_).headOption)
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
    } yield updated).map { rows =>
      ServiceResults.success(inflateRowsWithUploadedFiles(rows).head)
    }
  }
}

object AssessmentService {
  def inflateRowsWithUploadedFiles(rows: Seq[(AssessmentsTables.StoredAssessment, Option[UploadedFilesTables.StoredUploadedFile])]): Seq[Assessment] =
    OneToMany.leftJoinUnordered(rows)
      .map { case (storedAssessment, storedUploadedFiles) =>
        storedAssessment.asAssessment(
          storedUploadedFiles.map(f => f.id -> f.asUploadedFile).toMap
        )
      }
}
