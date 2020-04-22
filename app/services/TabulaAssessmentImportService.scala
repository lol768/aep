package services

import java.util.UUID

import com.google.inject.ImplementedBy
import domain.AuditEvent.{Operation, Target}
import domain.tabula.{AssessmentComponent, ExamPaperSchedule}
import domain.{Assessment, JobKeys, StudentAssessment}
import helpers.ServiceResultUtils.traverseSerial
import javax.inject.{Inject, Singleton}
import org.quartz.{JobKey, Scheduler, TriggerKey}
import play.api.Configuration
import play.api.libs.json.Json
import services.TabulaAssessmentImportService.{AssessmentImportResult, DepartmentWithAssessments}
import services.tabula.TabulaAssessmentService.GetAssessmentsOptions
import services.tabula.{TabulaAssessmentService, TabulaDepartmentService}
import system.Features
import warwick.core.Logging
import warwick.core.helpers.ServiceResults
import warwick.core.helpers.ServiceResults.Implicits._
import warwick.core.helpers.ServiceResults.ServiceResult
import warwick.core.system.{AuditLogContext, AuditService}

import scala.concurrent.{ExecutionContext, Future}

object TabulaAssessmentImportService {
  type AssessmentImportResult = Seq[DepartmentWithAssessments]

  case class DepartmentWithAssessments(
    departmentCode: String,
    assessment: Seq[Assessment],
  )
}

@ImplementedBy(classOf[TabulaAssessmentImportServiceImpl])
trait TabulaAssessmentImportService {
  def getImportTriggerKey: TriggerKey
  def importAssessments()(implicit ctx: AuditLogContext): Future[ServiceResult[AssessmentImportResult]]
  def pauseImports()(implicit ctx: AuditLogContext): Future[ServiceResult[Unit]]
  def resumeImports()(implicit ctx: AuditLogContext): Future[ServiceResult[Unit]]
}

@Singleton
class TabulaAssessmentImportServiceImpl @Inject()(
  tabulaDepartmentService: TabulaDepartmentService,
  tabulaAssessmentService: TabulaAssessmentService,
  assessmentService: AssessmentService,
  studentAssessmentService: StudentAssessmentService,
  configuration: Configuration,
  features: Features,
  scheduler: Scheduler,
  auditService: AuditService,
)(implicit ec: ExecutionContext) extends TabulaAssessmentImportService with Logging {
  private[this] lazy val examProfileCodes = configuration.get[Seq[String]]("tabula.examProfileCodes")

  override def getImportTriggerKey: TriggerKey = scheduler.getTriggersOfJob(JobKeys.tabulaAssessmentImportJobKey).get(0).getKey

  override def importAssessments()(implicit ctx: AuditLogContext): Future[ServiceResult[AssessmentImportResult]] =
    tabulaDepartmentService.getDepartments().successFlatMapTo { departments =>
      logger.info(s"Import started. Total departments to process: ${departments.size}")

      traverseSerial(departments)(d => process(d.code))
        .successMapTo { departmentWithAssessments =>
          logger.info(s"Processed total departments: ${departmentWithAssessments.size}")
          departmentWithAssessments
        }
    }

  override def pauseImports()(implicit ctx: AuditLogContext): Future[ServiceResult[Unit]] =
    auditService.audit(Operation.TabulaAssessmentImport.Pause, "ImportAssessment", Target.TabulaAssessmentImports, Json.obj()) {
      Future.successful {
        ServiceResults.success {
          scheduler.pauseTrigger(getImportTriggerKey)
          logger.info("Tabula assessment imports paused")
        }
      }
    }

  override def resumeImports()(implicit ctx: AuditLogContext): Future[ServiceResult[Unit]] =
    auditService.audit(Operation.TabulaAssessmentImport.Resume, "ImportAssessment", Target.TabulaAssessmentImports, Json.obj()) {
      Future.successful {
        ServiceResults.success {
          scheduler.resumeTrigger(getImportTriggerKey)
          logger.info("Tabula assessment imports resumed")
        }
      }
    }

  private def process(departmentCode: String)(implicit ctx: AuditLogContext): Future[ServiceResult[DepartmentWithAssessments]] = {
    logger.info(s"Processing department $departmentCode")

    traverseSerial(examProfileCodes) { examProfileCode =>
      tabulaAssessmentService.getAssessments(GetAssessmentsOptions(departmentCode, withExamPapersOnly = true, inUseOnly = false, Some(examProfileCode)))
        .successFlatMapTo { assessmentComponents =>
          traverseSerial(assessmentComponents)(generateAssessment(_, examProfileCode))
        }
    }.successMapTo(components => DepartmentWithAssessments(departmentCode, components.flatten.flatten))
  }

  private def generateAssessment(ac: AssessmentComponent, examProfileCode: String)(implicit ctx: AuditLogContext): Future[ServiceResult[Option[Assessment]]] = {
    // Only return where there is a matching schedule for that exam profile
    ac.examPaper.toSeq.flatMap(_.schedule).groupBy(_.examProfileCode).get(examProfileCode)
      .map(_.sortBy(_.locationSequence))
      .map { schedules =>
        val schedule: ExamPaperSchedule =
          if (schedules.size == 1) schedules.head
          else {
            // Some information _must_ match, otherwise we need to change our approach
            require(schedules.forall(_.slotId == schedules.head.slotId))
            require(schedules.forall(_.startTime == schedules.head.startTime))

            // We allow locationSequence and location to differ, but we treat them as one assessment
            schedules.head.copy(
              locationSequence = "", // Not to be used
              locationName = schedules.flatMap(_.locationName).headOption, // Just pick the first by locationSequence
              students = schedules.flatMap(_.students)
            )
          }

        val isExcludedFromAEP = schedule.locationName.contains("Assignment") || schedule.locationName.contains("Not required in Covid-19 alternative assesssments")

        assessmentService.getByTabulaAssessmentId(ac.id, examProfileCode).successFlatMapTo {
          case Some(existingAssessment) if isExcludedFromAEP =>
            assessmentService.delete(existingAssessment).successMapTo(_ => None)

          case Some(existingAssessment) =>
            val updated = ac.asAssessment(Some(existingAssessment), schedule, features.overwriteAssessmentTypeOnImport)
            if (updated == existingAssessment)
              Future.successful(ServiceResults.success(Some(existingAssessment)))
            else
              assessmentService.update(updated, Nil).successMapTo(Some(_))

          case None if !isExcludedFromAEP =>
            val newAssessment = ac.asAssessment(None, schedule, features.overwriteAssessmentTypeOnImport)
            assessmentService.insert(newAssessment, Nil).successMapTo(Some(_))

          case _ => Future.successful(ServiceResults.success(None))
        }.successFlatMapTo {
          case None => Future.successful(ServiceResults.success(None))
          case Some(assessment) =>
            studentAssessmentService.byAssessmentId(assessment.id).successFlatMapTo { studentAssessments =>
              val deletions: Seq[StudentAssessment] =
                studentAssessments.filterNot(sa => schedule.students.exists(_.universityID == sa.studentId))

              val additions: Seq[StudentAssessment] =
                schedule.students.filterNot(s => studentAssessments.exists(_.studentId == s.universityID))
                  .map { scheduleStudent =>
                    val extraTimeAdjustment = if (features.importStudentExtraTime) scheduleStudent.extraTimePerHour else None
                    StudentAssessment(
                      id = UUID.randomUUID(),
                      assessmentId = assessment.id,
                      occurrence = Option(scheduleStudent.occurrence),
                      academicYear = Option(schedule.academicYear),
                      studentId = scheduleStudent.universityID,
                      inSeat = false,
                      startTime = None,
                      extraTimeAdjustment = extraTimeAdjustment,
                      explicitFinaliseTime = None,
                      uploadedFiles = Nil,
                      tabulaSubmissionId = None
                    )
                  }

              val modifications: Seq[StudentAssessment] =
                schedule.students.flatMap { scheduleStudent =>
                  val extraTimeAdjustment = if (features.importStudentExtraTime) scheduleStudent.extraTimePerHour else None
                  studentAssessments.find(_.studentId == scheduleStudent.universityID).flatMap { studentAssessment =>
                    val updated = studentAssessment.copy(
                      extraTimeAdjustment = extraTimeAdjustment,
                      occurrence = Option(scheduleStudent.occurrence),
                      academicYear = Option(schedule.academicYear),
                    )

                    // Don't return no-ops
                    Some(updated).filterNot(_ == studentAssessment)
                  }
                }

              ServiceResults.futureSequence(
                deletions.map(studentAssessmentService.delete) ++
                (additions ++ modifications).map(studentAssessmentService.upsert)
              ).successMapTo(_ => Some(assessment))
          }
        }
    }.getOrElse(Future.successful(ServiceResults.success(None)))
  }
}


