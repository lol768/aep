package services

import java.time.Duration
import java.util.UUID

import akka.Done
import com.google.common.io.ByteSource
import com.google.inject.ImplementedBy
import domain.Assessment.State
import domain.AuditEvent.{Operation, Target}
import domain.dao.AssessmentsTables.{StoredAssessment, StoredBrief}
import domain.dao._
import domain.{Assessment, AssessmentMetadata, UploadedFileOwner}
import javax.inject.{Inject, Singleton}
import play.api.libs.json.Json
import services.AssessmentService._
import slick.dbio.DBIO
import warwick.core.helpers.ServiceResults.ServiceResult
import warwick.core.helpers.{JavaTime, ServiceResults}
import warwick.core.system.{AuditLogContext, AuditService}
import warwick.core.timing.TimingContext
import warwick.fileuploads.UploadedFileSave
import warwick.sso.Usercode

import scala.concurrent.{ExecutionContext, Future}

@ImplementedBy(classOf[AssessmentServiceImpl])
trait AssessmentService {
  def list(implicit t: TimingContext): Future[ServiceResult[Seq[Assessment]]]

  def findByStates(state: Seq[State])(implicit t: TimingContext): Future[ServiceResult[Seq[Assessment]]]

  def isInvigilator(usercode: Usercode)(implicit t: TimingContext): Future[ServiceResult[Boolean]]

  def listForInvigilator(usercodes: Set[Usercode])(implicit t: TimingContext): Future[ServiceResult[Seq[Assessment]]]

  def listForExamProfileCode(examProfileCode: String)(implicit t: TimingContext): Future[ServiceResult[Seq[Assessment]]]

  def getTodaysAssessments(implicit t: TimingContext): Future[ServiceResult[Seq[Assessment]]]

  def getLast48HrsAssessments(implicit t: TimingContext): Future[ServiceResult[Seq[Assessment]]]

  def getFinishedWithUnsentSubmissions(implicit t: TimingContext): Future[ServiceResult[Seq[AssessmentMetadata]]]

  def getStartedAndSubmittable(implicit t: TimingContext): Future[ServiceResult[Seq[Assessment]]]

  def getByIdForInvigilator(id: UUID, usercodes: List[Usercode])(implicit t: TimingContext): Future[ServiceResult[Assessment]]

  def getByIds(ids: Seq[UUID])(implicit t: TimingContext): Future[ServiceResult[Seq[Assessment]]]

  def get(id: UUID)(implicit t: TimingContext): Future[ServiceResult[Assessment]]

  def getByTabulaAssessmentId(id: UUID, examProfileCode: String)(implicit t: TimingContext): Future[ServiceResult[Option[Assessment]]]

  def update(assessment: Assessment, files: Seq[(ByteSource, UploadedFileSave)])(implicit ac: AuditLogContext): Future[ServiceResult[Assessment]]

  def insert(assessment: Assessment, files: Seq[(ByteSource, UploadedFileSave)])(implicit ac: AuditLogContext): Future[ServiceResult[Assessment]]

  def upsert(assessment: Assessment)(implicit ctx: AuditLogContext): Future[ServiceResult[Assessment]]

  def delete(assessment: Assessment)(implicit ac: AuditLogContext): Future[ServiceResult[Done]]
}

@Singleton
class AssessmentServiceImpl @Inject()(
  auditService: AuditService,
  daoRunner: DaoRunner,
  dao: AssessmentDao,
  studentAssessmentDao: StudentAssessmentDao,
  uploadedFileService: UploadedFileService,
  assessmentClientNetworkActivityDao: AssessmentClientNetworkActivityDao
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

  override def isInvigilator(usercode: Usercode)(implicit t: TimingContext): Future[ServiceResult[Boolean]] = {
    daoRunner.run(dao.isInvigilator(usercode)).map(ServiceResults.success)
  }

  override def listForInvigilator(usercodes: Set[Usercode])(implicit t: TimingContext): Future[ServiceResult[Seq[Assessment]]] =
    daoRunner.run(dao.getByInvigilatorWithUploadedFiles(usercodes))
      .map(inflateRowsWithUploadedFiles)
      .map(ServiceResults.success)

  override def listForExamProfileCode(examProfileCode: String)(implicit t: TimingContext): Future[ServiceResult[Seq[Assessment]]] =
    daoRunner.run(dao.getByExamProfileCodeWithUploadedFiles(examProfileCode))
      .map(inflateRowsWithUploadedFiles)
      .map(ServiceResults.success)

  override def getByIdForInvigilator(id: UUID, usercodes: List[Usercode])(implicit t: TimingContext): Future[ServiceResult[Assessment]] =
    daoRunner.run(dao.getByIdAndInvigilatorWithUploadedFiles(id, usercodes))
      .map(inflateRowWithUploadedFiles)
      .map(_.fold[ServiceResult[Assessment]](ServiceResults.error(s"Could not find Assessment with ID $id with invigilators ${usercodes.map(_.string).mkString(",")}"))(ServiceResults.success))

  override def getTodaysAssessments(implicit t: TimingContext): Future[ServiceResult[Seq[Assessment]]] =
    daoRunner.run(dao.getTodayWithUploadedFiles)
      .map(inflateRowsWithUploadedFiles)
      .map(ServiceResults.success)

  override def getLast48HrsAssessments(implicit t: TimingContext): Future[ServiceResult[Seq[Assessment]]] =
    daoRunner.run(dao.getLast48HrsWithUploadedFiles)
      .map(inflateRowsWithUploadedFiles)
      .map(ServiceResults.success)

  def getFinishedWithUnsentSubmissions(implicit t: TimingContext): Future[ServiceResult[Seq[AssessmentMetadata]]] = {
    daoRunner.run(dao.getAssessmentsRequiringUpload)
      .map(_.map(_.asAssessmentMetadata))
      .map(ServiceResults.success)
  }

  // Returns all assessments where the start time has passed, and the latest possible finish time for any student is yet to come
  override def getStartedAndSubmittable(implicit t: TimingContext): Future[ServiceResult[Seq[Assessment]]] =
    getLast48HrsAssessments.flatMap { result =>
      result.toOption.map { todaysAssessments =>
        daoRunner.run(studentAssessmentDao.getByAssessmentIds(todaysAssessments.map(_.id))).map { todaysStudentAssessments =>
          val longestAdjustments = todaysAssessments.map { assessment =>
            assessment.id -> todaysStudentAssessments
              .filter(sa => sa.assessmentId == assessment.id)
              .maxByOption(_.extraTimeAdjustment.getOrElse(Duration.ZERO))
              .map(_.extraTimeAdjustment.getOrElse(Duration.ZERO))
          }.toMap
          val now = JavaTime.offsetDateTime
          ServiceResults.success {
            todaysAssessments.filter { a => longestAdjustments.get(a.id).flatten.exists { adjustment =>
              a.startTime.exists {
                st => st
                  .plus(a.duration.getOrElse(Duration.ZERO))
                  .plus(Assessment.lateSubmissionPeriod)
                  .plus(adjustment)
                  .isAfter(now)
              }
            }}
          }
        }
      }.getOrElse {
        Future.successful(ServiceResults.error("Error getting today's assessments"))
      }
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

  override def update(assessment: Assessment, files: Seq[(ByteSource, UploadedFileSave)])(implicit ac: AuditLogContext): Future[ServiceResult[Assessment]] =
    auditService.audit(Operation.Assessment.UpdateAssessment, assessment.id.toString, Target.Assessment, Json.obj("files" -> files.map(_._2.fileName))) {
      daoRunner.run(for {
        stored <- dao.getById(assessment.id).map(_.getOrElse(throw new NoSuchElementException(s"Could not find an Assessment with ID ${assessment.id}")))
        fileIds <- if (files.nonEmpty) {
          DBIO.sequence(files.toList.map { case (in, metadata) =>
            uploadedFileService.storeDBIO(
              in,
              metadata,
              ac.usercode.get,
              assessment.id,
              UploadedFileOwner.AssessmentBrief
            ).map(_.id)
          })
        } else DBIO.successful(assessment.brief.files.map(_.id))
        _ <- dao.update(StoredAssessment(
          id = assessment.id,
          paperCode = assessment.paperCode,
          section = assessment.section,
          title = assessment.title,
          startTime = assessment.startTime,
          duration = assessment.duration,
          platform = assessment.platform,
          assessmentType = assessment.assessmentType,
          durationStyle = assessment.durationStyle,
          storedBrief = StoredBrief(
            text = assessment.brief.text,
            fileIds = fileIds,
            urls = assessment.brief.urls
          ),
          invigilators = sortedInvigilators(assessment),
          state = assessment.state,
          tabulaAssessmentId = assessment.tabulaAssessmentId,
          tabulaAssignments = assessment.tabulaAssignments.map(_.toString).toList.sorted,
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

  override def insert(assessment: Assessment, files: Seq[(ByteSource, UploadedFileSave)])(implicit ac: AuditLogContext): Future[ServiceResult[Assessment]] =
    auditService.audit(Operation.Assessment.CreateAssessment, assessment.id.toString, Target.Assessment, Json.obj("files" -> files.map(_._2.fileName))) {
      daoRunner.run(for {
        fileIds <- if (files.nonEmpty) {
          DBIO.sequence(files.toList.map { case (in, metadata) =>
            uploadedFileService.storeDBIO(
              in,
              metadata,
              ac.usercode.get,
              assessment.id,
              UploadedFileOwner.AssessmentBrief
            ).map(_.id)
          })
        } else DBIO.successful(assessment.brief.files.map(_.id))
        assessment <- dao.insert(StoredAssessment(
          id = assessment.id,
          paperCode = assessment.paperCode,
          section = assessment.section,
          title = assessment.title,
          startTime = assessment.startTime,
          duration = assessment.duration,
          platform = assessment.platform,
          assessmentType = assessment.assessmentType,
          durationStyle = assessment.durationStyle,
          storedBrief = StoredBrief(
            text = assessment.brief.text,
            fileIds = fileIds,
            urls = assessment.brief.urls
          ),
          invigilators = sortedInvigilators(assessment),
          state = assessment.state,
          tabulaAssessmentId = assessment.tabulaAssessmentId,
          tabulaAssignments = assessment.tabulaAssignments.map(_.toString).toList.sorted,
          examProfileCode = assessment.examProfileCode,
          moduleCode = assessment.moduleCode,
          departmentCode = assessment.departmentCode,
          sequence = assessment.sequence,
          created = JavaTime.offsetDateTime,
          version = JavaTime.offsetDateTime,
        ))
        inserted <- dao.loadByIdWithUploadedFiles(assessment.id)
      } yield inserted).map { r =>
        inflateRowWithUploadedFiles(r).get
      }.map(ServiceResults.success)
    }

  private def sortedInvigilators(assessment: Assessment): List[String] = assessment.invigilators.toSeq.sortBy(_.string).map(_.string).toList

  def upsert(assessment: Assessment)(implicit ctx: AuditLogContext): Future[ServiceResult[Assessment]] =
    auditService.audit(Operation.Assessment.UpdateAssessment, assessment.id.toString, Target.Assessment, Json.obj()) {
      daoRunner.run(dao.getById(assessment.id)).flatMap { storedAssessmentOption =>
        storedAssessmentOption.map { existingAssessment =>
          daoRunner.run(dao.update(StoredAssessment(
              id = existingAssessment.id,
              paperCode = assessment.paperCode,
              section = assessment.section,
              title = assessment.title,
              startTime = assessment.startTime,
              duration = assessment.duration,
              durationStyle = assessment.durationStyle,
              platform = assessment.platform,
              assessmentType = assessment.assessmentType,
              storedBrief = assessment.brief.toStoredBrief,
              invigilators = assessment.invigilators.map(_.string).toList,
              state = assessment.state,
              tabulaAssessmentId = assessment.tabulaAssessmentId,
              tabulaAssignments = assessment.tabulaAssignments.map(_.toString).toList,
              examProfileCode = assessment.examProfileCode,
              moduleCode = assessment.moduleCode,
              departmentCode = assessment.departmentCode,
              sequence = assessment.sequence,
              created = existingAssessment.created,
              version = existingAssessment.version
            )))
        }.getOrElse {
          val timestamp = JavaTime.offsetDateTime
          daoRunner.run(dao.insert(StoredAssessment(
            id = assessment.id,
            paperCode = assessment.paperCode,
            section = assessment.section,
            title = assessment.title,
            startTime = assessment.startTime,
            duration = assessment.duration,
            platform = assessment.platform,
            assessmentType = assessment.assessmentType,
            durationStyle = assessment.durationStyle,
            storedBrief = assessment.brief.toStoredBrief,
            invigilators = sortedInvigilators(assessment),
            state = assessment.state,
            tabulaAssessmentId = assessment.tabulaAssessmentId,
            tabulaAssignments = assessment.tabulaAssignments.map(_.toString).toList.sorted,
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

  override def delete(assessment: Assessment)(implicit ac: AuditLogContext): Future[ServiceResult[Done]] =
    auditService.audit(Operation.Assessment.DeleteAssessment, assessment.id.toString, Target.Assessment, Json.obj()) {
      daoRunner.run(for {
        stored <- dao.getById(assessment.id)
        studentAssessments <- studentAssessmentDao.getByAssessmentId(assessment.id)
        _ <- assessmentClientNetworkActivityDao.deleteAll(studentAssessments.map(_.id))
        _ <- DBIO.sequence(studentAssessments.toList.map(s => studentAssessmentDao.delete(s.studentId, s.assessmentId)))
        done <- (stored match {
          case Some(a) => dao.delete(a)
          case _ => DBIO.successful(Done) // No-op
        })
      } yield done).map(ServiceResults.success)
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
