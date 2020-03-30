package services

import java.time.{Duration, LocalDate, LocalDateTime, LocalTime}
import java.util.UUID

import com.google.common.io.ByteSource
import com.google.inject.ImplementedBy
import domain.Assessment.State.Draft
import domain.Assessment.{AssessmentType, Platform, State}
import domain.dao.AssessmentsTables.{StoredAssessment, StoredBrief}
import domain.dao.{AssessmentDao, AssessmentsTables, DaoRunner, UploadedFilesTables}
import domain.{Assessment, UploadedFileOwner}
import javax.inject.{Inject, Singleton}
import services.AssessmentService._
import slick.dbio.DBIO
import warwick.core.helpers.ServiceResults
import warwick.core.helpers.ServiceResults.ServiceResult
import warwick.core.system.AuditLogContext
import warwick.core.timing.TimingContext
import warwick.fileuploads.UploadedFileSave
import warwick.core.helpers.JavaTime.{timeZone => zone}

import scala.concurrent.{ExecutionContext, Future}

@ImplementedBy(classOf[AssessmentServiceImpl])
trait AssessmentService {
  def list(implicit t: TimingContext): Future[ServiceResult[Seq[Assessment]]]
  def findByStates(state: Seq[State])(implicit t: TimingContext): Future[ServiceResult[Seq[Assessment]]]
  def getByIds(ids: Seq[UUID])(implicit t: TimingContext): Future[ServiceResult[Seq[Assessment]]]
  def get(id: UUID)(implicit t: TimingContext): Future[ServiceResult[Assessment]]
  def update(assessment: Assessment, files: Seq[(ByteSource, UploadedFileSave)])(implicit ac: AuditLogContext): Future[ServiceResult[Assessment]]
  def insert(assessment: Assessment)(implicit ac: AuditLogContext): Future[ServiceResult[Assessment]]

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

  override def insert(assessment: Assessment)(implicit ac: AuditLogContext): Future[ServiceResult[Assessment]] = {

    val dt = LocalDateTime.of(LocalDate.now(), LocalTime.now())


    val stored = StoredAssessment(
      id = assessment.id,
      code = assessment.code,
      title = assessment.title,
      startTime = Some(dt.atOffset(zone.getRules.getOffset(dt))), //TODO This needs to be set
      duration = Duration.ofHours(3), //TODO - This would be populated from API
      platform = Platform.OnlineExams,
      assessmentType = AssessmentType.OpenBook,
      storedBrief = StoredBrief(None, Nil, None),
      state = Draft,
      created = dt.atOffset(zone.getRules.getOffset(dt)),
      version = dt.atOffset(zone.getRules.getOffset(dt))
    )

    daoRunner.run(dao.insert(stored)).map(_ => Right(assessment))
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
