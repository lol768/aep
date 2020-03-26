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
import warwick.sso.Usercode

import scala.concurrent.{ExecutionContext, Future}

@ImplementedBy(classOf[AssessmentServiceImpl])
trait AssessmentService {
  def list(implicit t: TimingContext): Future[ServiceResult[Seq[Assessment]]]
  def listForInvigilator(usercodes: List[Usercode])(implicit t: TimingContext): Future[ServiceResult[Seq[Assessment]]]
  def getByIdForInvigilator(id: UUID, usercodes: List[Usercode])(implicit t: TimingContext): Future[ServiceResult[Assessment]]
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
    daoRunner.run(dao.all).flatMap(inflate)
  }

  override def listForInvigilator(usercodes: List[Usercode])(implicit t: TimingContext): Future[ServiceResult[Seq[Assessment]]] = {
    daoRunner.run(dao.getByInvigilator(usercodes)).flatMap(inflate)
  }

  def getByIdForInvigilator(id: UUID, usercodes: List[Usercode])(implicit t: TimingContext): Future[ServiceResult[Assessment]] = {
    withFiles(
      dao.getByIdAndInvigilator(id, usercodes),
      s"Could not find Assessment with ID ${id} with invigilators ${usercodes.map(_.string).mkString(",")}"
    )
  }

  override def getByIds(ids: Seq[UUID])(implicit t: TimingContext): Future[ServiceResult[Seq[Assessment]]] = {
    daoRunner.run(dao.getByIds(ids)).flatMap(inflate)
  }

  override def get(id: UUID)(implicit t: TimingContext): Future[ServiceResult[Assessment]] = {
    withFiles(
      dao.getById(id),
      s"Could not find Assessment with ID ${id}"
    )
  }
}
