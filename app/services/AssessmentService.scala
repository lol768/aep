package services

import java.util.UUID

import com.google.inject.ImplementedBy
import domain.Assessment
import domain.dao.AssessmentsTables.StoredAssessment
import domain.dao.{AssessmentDao, AssessmentsTables, DaoRunner}
import javax.inject.{Inject, Singleton}
import slick.dbio.DBIO
import warwick.core.helpers.ServiceResults
import warwick.core.helpers.ServiceResults.ServiceResult
import warwick.core.timing.TimingContext

import scala.concurrent.{ExecutionContext, Future}

@ImplementedBy(classOf[AssessmentServiceImpl])
trait AssessmentService {
  def list(implicit t: TimingContext): Future[ServiceResult[Seq[Assessment]]]
  def listForInvigilator(usercodes: List[String])(implicit t: TimingContext): Future[ServiceResult[Seq[Assessment]]]
  def getByIdForInvigilator(id: UUID, usercodes: List[String])(implicit t: TimingContext): Future[ServiceResult[Assessment]]
  def getByIds(ids: Seq[UUID])(implicit t: TimingContext): Future[ServiceResult[Seq[Assessment]]]
  def get(id: UUID)(implicit t: TimingContext): Future[ServiceResult[Assessment]]
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

  private def withFiles(query: DBIO[StoredAssessment])(implicit t: TimingContext) =
    daoRunner.run(query).flatMap { storedAssessment =>
      uploadedFileService.get(storedAssessment.storedBrief.fileIds).map { uploadedFiles =>
        ServiceResults.success(storedAssessment.asAssessment(uploadedFiles.map(f => f.id -> f).toMap))
      }
    }

  override def list(implicit t: TimingContext): Future[ServiceResult[Seq[Assessment]]] = {
    daoRunner.run(dao.all).flatMap(inflate)
  }

  override def listForInvigilator(usercodes: List[String])(implicit t: TimingContext): Future[ServiceResult[Seq[Assessment]]] = {
    daoRunner.run(dao.getByInvigilator(usercodes)).flatMap(inflate)
  }

  def getByIdForInvigilator(id: UUID, usercodes: List[String])(implicit t: TimingContext): Future[ServiceResult[Assessment]] = {
    withFiles(dao.getByIdAndInvigilator(id, usercodes)).recover {
      case _: NoSuchElementException => ServiceResults.error(s"Could not find an Assessment with ID $id for invigilators: ${usercodes.mkString(",")}")
    }
  }

  override def getByIds(ids: Seq[UUID])(implicit t: TimingContext): Future[ServiceResult[Seq[Assessment]]] = {
    daoRunner.run(dao.getByIds(ids)).flatMap(inflate)
  }

  override def get(id: UUID)(implicit t: TimingContext): Future[ServiceResult[Assessment]] = {
    withFiles(dao.getById(id)).recover {
      case _: NoSuchElementException => ServiceResults.error(s"Could not find an Assessment with ID $id")
    }
  }
}
