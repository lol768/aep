package services

import java.time.{Duration, OffsetDateTime}
import java.util.UUID

import com.google.inject.ImplementedBy
import domain.AuditEvent.{Operation, Target}
import domain.tabula.{AssessmentComponent, ExamPaperSchedule}
import domain.{Assessment, JobKeys, StudentAssessment}
import helpers.ServiceResultUtils.traverseSerial
import javax.inject.{Inject, Singleton}
import org.quartz.{Scheduler, TriggerKey}
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
  timingInfo: TimingInfoService,
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
      ServiceResults.zip(
        assessmentService.listForExamProfileCodeWithStudentCount(examProfileCode)
          .successMapTo(_.map(_._1).filter(a => a.departmentCode.lowerCase == departmentCode.toLowerCase && a.tabulaAssessmentId.nonEmpty)),
        tabulaAssessmentService.getAssessments(GetAssessmentsOptions(departmentCode, withExamPapersOnly = true, inUseOnly = false, Some(examProfileCode)))
      ).successFlatMapTo { case (existing, assessmentComponents) =>
        val updates = traverseSerial(assessmentComponents)(generateAssessment(_, examProfileCode)).successMapTo(_.flatten)

        val assessmentIdsToDelete =
          existing.filterNot(_.tabulaAssessmentId.exists(assessmentComponents.map(_.id).contains))

        if (assessmentIdsToDelete.isEmpty) updates
        else {
          val deletions = assessmentService.getByIds(assessmentIdsToDelete.map(_.id)).successFlatMapTo { assessments =>
            traverseSerial(assessments)(assessmentService.delete)
          }

          ServiceResults.zip(updates, deletions).successMapTo(_._1)
        }
      }
    }.successMapTo(assessments => DepartmentWithAssessments(departmentCode, assessments.flatten))
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
            require(schedules.forall(_.slotId == schedules.head.slotId), s"Multiple schedules for $ac but slot ID doesn't match")
            require(schedules.forall(_.startTime == schedules.head.startTime))

            // We allow locationSequence and location to differ, but we treat them as one assessment
            schedules.head.copy(
              locationSequence = "", // Not to be used
              locationName = schedules.flatMap(_.locationName).headOption, // Just pick the first by locationSequence
              students = schedules.flatMap(_.students)
            )
          }

        val academicYearLookup = (for(schedule <- schedules; student <- schedule.students)
          yield student.universityID -> schedule.academicYear).toMap

        val isExcludedFromAEP = schedule.locationName.contains("Assignment") || schedule.locationName.contains("Not required in Covid-19 alternative assesssments")

        assessmentService.getByTabulaAssessmentId(ac.id, examProfileCode).successFlatMapTo {
          case Some(existingAssessment) if isExcludedFromAEP =>
            assessmentService.delete(existingAssessment).successMapTo(_ => None)

          case Some(existingAssessment) =>
            val updated = ac.asAssessment(Some(existingAssessment), schedule)
            if (updated == existingAssessment)
              Future.successful(ServiceResults.success(Some(existingAssessment)))
            else
              assessmentService.update(updated, Nil).successMapTo(Some(_))

          case None if !isExcludedFromAEP =>
            val newAssessment = ac.asAssessment(None, schedule)
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
                    val extraTimeAdjustmentPerHour = if (features.importStudentExtraTime) scheduleStudent.totalExtraTimePerHour else None
                    StudentAssessment(
                      id = UUID.randomUUID(),
                      assessmentId = assessment.id,
                      occurrence = Option(scheduleStudent.occurrence),
                      academicYear = academicYearLookup.get(scheduleStudent.universityID),
                      studentId = scheduleStudent.universityID,
                      inSeat = false,
                      startTime = None,
                      extraTimeAdjustmentPerHour = extraTimeAdjustmentPerHour,
                      explicitFinaliseTime = None,
                      uploadedFiles = Nil,
                      tabulaSubmissionId = None
                    )
                  }

              val modifications: Seq[StudentAssessment] =
                schedule.students.flatMap { scheduleStudent =>
                  val extraTimeAdjustmentPerHour = if (features.importStudentExtraTime) scheduleStudent.totalExtraTimePerHour else None
                  studentAssessments.find(_.studentId == scheduleStudent.universityID).flatMap { studentAssessment =>
                    val updated = studentAssessment.copy(
                      extraTimeAdjustmentPerHour = extraTimeAdjustmentPerHour,
                      occurrence = Option(scheduleStudent.occurrence),
                      academicYear = academicYearLookup.get(scheduleStudent.universityID),
                    )

                    // Don't return no-ops
                    Some(updated)
                      .filterNot(_ == studentAssessment)
                      .filter(sa => shouldBeUpdated(assessment, sa))
                  }
                }

              val mockUpdates: Future[Seq[StudentAssessment]] =
                studentAssessmentService.byUniversityIds(schedule.students.map(_.universityID)).map {
                  _.toOption.map { studentAssessments =>
                    studentAssessments.filter(sa => sa.tabulaSubmissionId.isEmpty && !modifications.map(_.id).contains(sa.id)).map { original =>
                      val scheduleStudent = schedule.students.find(_.universityID == original.studentId).getOrElse(
                        throw new IllegalStateException(s"Could not find schedule student with ID ${original.studentId}")
                      )
                      val extraTimeAdjustmentPerHour = if (features.importStudentExtraTime) scheduleStudent.totalExtraTimePerHour else None
                      original.copy(
                        extraTimeAdjustmentPerHour = extraTimeAdjustmentPerHour
                      )
                    }
                  }.getOrElse {
                    Seq.empty
                  }.filter(sa => shouldBeUpdated(assessment, sa))
                }

              mockUpdates.flatMap { mocks =>
                ServiceResults.futureSequence(
                  deletions.map(studentAssessmentService.delete) ++
                    (additions ++ modifications ++ mocks).map(studentAssessmentService.upsert)
                ).successMapTo(_ => Some(assessment))
              }
          }
        }
    }.getOrElse(Future.successful(ServiceResults.success(None)))
  }

  private def shouldBeUpdated(assessment: Assessment, studentAssessment: StudentAssessment): Boolean = {
    val lastStartTime = if (features.importStudentExtraTime) {
      assessment.defaultLastAllowedStartTime(timingInfo.lateSubmissionPeriod)
        .map(_.plus(
          studentAssessment.extraTimeAdjustment(assessment.duration.getOrElse(Duration.ZERO)).getOrElse(Duration.ZERO)
        ))
    } else {
      assessment.defaultLastAllowedStartTime(timingInfo.lateSubmissionPeriod)
    }
    (studentAssessment.startTime.isEmpty || studentAssessment.startTime.exists(_.isAfter(OffsetDateTime.now))) &&
      (lastStartTime.isEmpty || lastStartTime.exists(_.isAfter(OffsetDateTime.now)))
  }
}


