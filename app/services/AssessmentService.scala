package services

import java.time.{Duration, LocalDate, LocalDateTime, LocalTime}
import java.util.UUID

import com.google.inject.ImplementedBy
import domain.Assessment
import domain.Assessment.{AssessmentType, Platform}
import domain.dao.AssessmentsTables.{StoredAssessment, StoredBrief}
import domain.dao.{AssessmentDao, AssessmentsTables, DaoRunner}
import javax.inject.{Inject, Singleton}
import warwick.core.helpers.JavaTime.{timeZone => zone}
import warwick.core.helpers.ServiceResults
import warwick.core.helpers.ServiceResults.ServiceResult
import warwick.core.system.AuditLogContext
import warwick.core.timing.TimingContext

import scala.concurrent.{ExecutionContext, Future}

@ImplementedBy(classOf[AssessmentServiceImpl])
trait AssessmentService {
  def list(implicit t: TimingContext): Future[ServiceResult[Seq[Assessment]]]

  def getByIds(ids: Seq[UUID])(implicit t: TimingContext): Future[ServiceResult[Seq[Assessment]]]

  def get(id: UUID)(implicit t: TimingContext): Future[ServiceResult[Assessment]]

  def save(assessment: Assessment)(implicit ac: AuditLogContext): Future[ServiceResult[Assessment]]
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

  override def getByIds(ids: Seq[UUID])(implicit t: TimingContext): Future[ServiceResult[Seq[Assessment]]] = {
    daoRunner.run(dao.getByIds(ids)).flatMap(inflate)
  }

  override def get(id: UUID)(implicit t: TimingContext): Future[ServiceResult[Assessment]] = {
    daoRunner.run(dao.getById(id)).flatMap { storedAssessment =>
      uploadedFileService.get(storedAssessment.storedBrief.fileIds).map { uploadedFiles =>
        ServiceResults.success(storedAssessment.asAssessment(uploadedFiles.map(f => f.id -> f).toMap))
      }
    }.recover {
      case _: NoSuchElementException => ServiceResults.error(s"Could not find an Assessment with ID $id")
    }
  }

  override def save(assessment: Assessment)(implicit ac: AuditLogContext): Future[ServiceResult[Assessment]] = {

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
      created = dt.atOffset(zone.getRules.getOffset(dt)),
      version = dt.atOffset(zone.getRules.getOffset(dt))
    )

    daoRunner.run(dao.insert(stored)).map(_ => Right(assessment))
  }
}
