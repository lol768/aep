package services

import java.time.OffsetDateTime
import java.util.UUID

import com.google.common.io.ByteSource
import com.google.inject.ImplementedBy
import domain.Assessment.State
import domain.dao.AssessmentsTables.{StoredAssessment, StoredBrief}
import domain.dao.{AssessmentDao, AssessmentsTables, DaoRunner, UploadedFilesTables}
import domain.{Assessment, UploadedFileOwner}
import javax.inject.{Inject, Singleton}
import services.AssessmentService._
import slick.dbio.DBIO
import warwick.core.helpers.ServiceResults.ServiceResult
import warwick.core.helpers.{JavaTime, ServiceResults}
import warwick.core.system.AuditLogContext
import warwick.core.timing.TimingContext
import warwick.fileuploads.UploadedFileSave
import warwick.sso.Usercode

import scala.concurrent.{ExecutionContext, Future}

@ImplementedBy(classOf[AssessmentServiceImpl])
trait AssessmentService {
  def list(implicit t: TimingContext): Future[ServiceResult[Seq[Assessment]]]

  def findByStates(state: Seq[State])(implicit t: TimingContext): Future[ServiceResult[Seq[Assessment]]]

  def listForInvigilator(usercodes: List[Usercode])(implicit t: TimingContext): Future[ServiceResult[Seq[Assessment]]]

  def getByIdForInvigilator(id: UUID, usercodes: List[Usercode])(implicit t: TimingContext): Future[ServiceResult[Assessment]]

  def getByIds(ids: Seq[UUID])(implicit t: TimingContext): Future[ServiceResult[Seq[Assessment]]]

  def get(id: UUID)(implicit t: TimingContext): Future[ServiceResult[Assessment]]

  def getByTabulaAssessmentId(id: UUID, examProfileCode: String)(implicit t: TimingContext): Future[ServiceResult[Option[Assessment]]]

  def update(assessment: Assessment, files: Seq[(ByteSource, UploadedFileSave)])(implicit ac: AuditLogContext): Future[ServiceResult[Assessment]]

  def insert(assessment: Assessment, files: Seq[(ByteSource, UploadedFileSave)])(implicit ac: AuditLogContext): Future[ServiceResult[Assessment]]

  def upsert(assessment: Assessment)(implicit ctx: AuditLogContext): Future[ServiceResult[Assessment]]
}

@Singleton
class AssessmentServiceImpl @Inject()(
  auditService: AuditService,
  daoRunner: DaoRunner,
  dao: AssessmentDao,
  uploadedFileService: UploadedFileService,
)(implicit ec: ExecutionContext) extends AssessmentService {

  private def inflate(storedAssessments: Seq[AssessmentsTables.StoredAssessment])(implicit t: TimingContext) = {
    uploadedFileService.get(storedAssessments.flatMap(_.storedBrief.fileIds)).map { uploadedFiles =>
      ServiceResults.success(storedAssessments.map(_.asAssessment(uploadedFiles.map(f => f.id -> f).toMap)))
    }
  }

  private def withFiles(
    query: DBIO[Option[StoredAssessment]],
    errorMsg: String = "Could not find results for query"
  )(implicit t: TimingContext) =
    daoRunner.run(query).flatMap { storedAssessmentOption =>
      storedAssessmentOption.map { storedAssessment =>
        uploadedFileService.get(storedAssessment.storedBrief.fileIds).map { uploadedFiles =>
          ServiceResults.success(storedAssessment.asAssessment(uploadedFiles.map(f => f.id -> f).toMap))
        }
      }.getOrElse {
        Future.successful(ServiceResults.error(errorMsg))
      }
    }

  override def list(implicit t: TimingContext): Future[ServiceResult[Seq[Assessment]]] = {
    daoRunner.run(dao.loadAllWithUploadedFiles)
      .map(inflateRowsWithUploadedFiles)
      .map(ServiceResults.success)
  }

  override def findByStates(state: Seq[State])(implicit t: TimingContext): Future[ServiceResult[Seq[Assessment]]] =
    daoRunner.run(dao.findByStatesWithUploadedFiles(state))
      .map(inflateRowsWithUploadedFiles)
      .map(ServiceResults.success)

  override def listForInvigilator(usercodes: List[Usercode])(implicit t: TimingContext): Future[ServiceResult[Seq[Assessment]]] = {
    daoRunner.run(dao.getByInvigilator(usercodes)).flatMap(inflate)
  }

  def getByIdForInvigilator(id: UUID, usercodes: List[Usercode])(implicit t: TimingContext): Future[ServiceResult[Assessment]] = {
    withFiles(
      dao.getByIdAndInvigilator(id, usercodes),
      s"Could not find Assessment with ID ${id} with invigilators ${usercodes.map(_.string).mkString(",")}"
    )
  }

  override def getByIds(ids: Seq[UUID])(implicit t: TimingContext): Future[ServiceResult[Seq[Assessment]]] =
    daoRunner.run(dao.loadByIdsWithUploadedFiles(ids))
      .map(inflateRowsWithUploadedFiles)
      .map(ServiceResults.success)

  override def get(id: UUID)(implicit t: TimingContext): Future[ServiceResult[Assessment]] =
    daoRunner.run(dao.loadByIdWithUploadedFiles(id))
      .map(inflateRowWithUploadedFiles)
      .map(_.fold[ServiceResult[Assessment]](ServiceResults.error(s"Could not find an Assessment with ID $id"))(ServiceResults.success))

  override def getByTabulaAssessmentId(id: UUID, examProfileCode: String)(implicit t: TimingContext): Future[ServiceResult[Option[Assessment]]] = {
    daoRunner.run(dao.loadByTabulaAssessmentIdWithUploadedFiles(id, examProfileCode))
      .map(inflateRowWithUploadedFiles)
      .map(ServiceResults.success)
  }

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
        paperCode = assessment.paperCode,
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
        invigilators = sortedInvigilators(assessment),
        state = assessment.state,
        tabulaAssessmentId = assessment.tabulaAssessmentId,
        examProfileCode = assessment.examProfileCode,
        moduleCode = assessment.moduleCode,
        departmentCode = assessment.departmentCode,
        sequence = assessment.sequence,
        created = stored.created,
        version = stored.version
      ))
      updated <- dao.loadByIdWithUploadedFiles(assessment.id)
    } yield updated).map(inflateRowWithUploadedFiles(_).get).map(ServiceResults.success)
  }

  override def insert(assessment: Assessment, files: Seq[(ByteSource, UploadedFileSave)])(implicit ac: AuditLogContext): Future[ServiceResult[Assessment]] = {
    daoRunner.run(for {
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
      assessment <- dao.insert(StoredAssessment(
        id = assessment.id,
        paperCode = assessment.paperCode,
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
        invigilators = sortedInvigilators(assessment),
        state = assessment.state,
        tabulaAssessmentId = assessment.tabulaAssessmentId,
        examProfileCode = assessment.examProfileCode,
        moduleCode = assessment.moduleCode,
        departmentCode = assessment.departmentCode,
        sequence = assessment.sequence,
        created = OffsetDateTime.now,
        version = OffsetDateTime.now,
      ))
      inserted <- dao.loadByIdWithUploadedFiles(assessment.id)
    } yield inserted).map { r =>
      inflateRowWithUploadedFiles(r).get
    }.map(ServiceResults.success)
  }

  private def sortedInvigilators(assessment: Assessment): List[String] = assessment.invigilators.toSeq.sortBy(_.string).map(_.string).toList

  def upsert(assessment: Assessment)(implicit ctx: AuditLogContext): Future[ServiceResult[Assessment]] = {
    daoRunner.run(dao.getById(assessment.id)).flatMap { storedAssessmentOption =>
      storedAssessmentOption.map { existingAssessment =>
        daoRunner.run(dao.update(existingAssessment.copy(
          paperCode = assessment.paperCode,
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
          paperCode = assessment.paperCode,
          title = assessment.title,
          startTime = assessment.startTime,
          duration = assessment.duration,
          platform = assessment.platform,
          assessmentType = assessment.assessmentType,
          storedBrief = assessment.brief.toStoredBrief,
          invigilators = sortedInvigilators(assessment),
          state = assessment.state,
          tabulaAssessmentId = assessment.tabulaAssessmentId,
          examProfileCode = assessment.examProfileCode,
          moduleCode = assessment.moduleCode,
          departmentCode = assessment.departmentCode,
          sequence = assessment.sequence,
          created = timestamp,
          version = timestamp
        )))
      }.flatMap { result =>
        uploadedFileService.get(result.storedBrief.fileIds).map { files =>
          ServiceResults.success(result.asAssessment(files.map(f => f.id -> f).toMap))
        }.recoverWith {
          case e: Exception => Future.successful(ServiceResults.error(e.getMessage))
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
