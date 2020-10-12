package services

import java.time.{Duration, LocalDate, LocalDateTime, LocalTime}
import java.util.UUID

import com.google.inject.ImplementedBy
import domain.Assessment.Platform.OnlineExams
import domain.Assessment.{DurationStyle, Platform}
import domain.dao.AssessmentsTables.{StoredAssessment, StoredBrief}
import domain.dao.DaoRunner
import domain.dao.StudentAssessmentsTables.StoredStudentAssessment
import domain.{Assessment, DepartmentCode, StudentAssessment}
import javax.inject.{Inject, Singleton}
import play.api.Logging
import services.sandbox.DataGeneration
import warwick.core.helpers.ServiceResults
import warwick.core.helpers.ServiceResults.ServiceResult
import warwick.core.system.AuditLogContext
import warwick.sso.UniversityID

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Random

@ImplementedBy(classOf[DataGenerationServiceImpl])
trait DataGenerationService {
  def putRandomAssessmentsInDatabase(howMany: Int)(implicit ctx: AuditLogContext): Future[ServiceResult[Seq[Assessment]]]
  def putRandomAssessmentsWithStudentAssessmentsInDatabase(howManyAssessments: Int)(implicit ctx: AuditLogContext): Future[ServiceResult[Seq[Assessment]]]
  val numberOfIds: Int
}

@Singleton
class DataGenerationServiceImpl @Inject()(
  assessmentService: AssessmentService,
  studentAssessmentService: StudentAssessmentService,
  daoRunner: DaoRunner,
)(
  implicit
  val ec: ExecutionContext,
  dataGeneration: DataGeneration,
) extends DataGenerationService with Logging {

  import DataGenerationService._

  private lazy val webdevIds =
    Seq("0970148", "0672089", "0672088", "0770884", "1673477", "9872987", "1171795", "1574999", "1574595", "0270954", "0380083", "1572165", "1170836", "9876004")
      .map(UniversityID)

  // Just for testing purposes
  override lazy val numberOfIds: Int = webdevIds.length

  override def putRandomAssessmentsInDatabase(howMany: Int = 1)(implicit ctx: AuditLogContext): Future[ServiceResult[Seq[Assessment]]] = {
    ServiceResults.futureSequence {
      (1 to howMany).map { _ =>
        assessmentService.upsert(makeStoredAssessment().asAssessment(Map.empty))
      }
    }
  }

  override def putRandomAssessmentsWithStudentAssessmentsInDatabase(howManyAssessments: Int)(implicit ctx: AuditLogContext): Future[ServiceResult[Seq[Assessment]]] = {
    ServiceResults.futureSequence {
      (1 to howManyAssessments).map { _ =>
        assessmentService.upsert(makeStoredAssessment().asAssessment(Map.empty)).flatMap { result =>
          result.toOption.map { insertedAssessment =>
            if (insertedAssessment.platform.contains(OnlineExams)) { // Guard against calling this for other platforms
              addRandomStudentAssessmentsToAssessment(insertedAssessment.id)
            }
          }.getOrElse {
            throw new Exception("Problem creating random assessment")
          }
          Future.successful(result)
        }
      }
    }
  }

  private def addRandomStudentAssessmentsToAssessment(assessmentId: UUID)(implicit ctx: AuditLogContext): Future[ServiceResult[Seq[StudentAssessment]]] = {
    ServiceResults.futureSequence {
      webdevIds.map { webdevId =>
        studentAssessmentService.upsert(makeStoredStudentAssessment(assessmentId, webdevId)
          .asStudentAssessment(Map.empty))
      }
    }
  }

}

object DataGenerationService {

  import helpers.DateConversion._
  import warwick.core.helpers.JavaTime.{timeZone => zone}

  private val invigilator1 = "Mary"
  private val invigilator2 = "Bob"

  private val extraTimeAdjustmentDurations = Seq(10, 15, 20, 30, 45, 60).map(_.toLong)

  def makeStoredBrief(platforms: Set[Platform])(implicit dataGeneration: DataGeneration): StoredBrief =
    StoredBrief(
      Some(dataGeneration.dummyWords(dataGeneration.random.between(6,30))),
      Seq.empty,
      platforms.filter(_.requiresUrl).map(p => p -> dataGeneration.fakePath).toMap
    )

  def makeStoredAssessment(
    uuid: UUID = UUID.randomUUID,
    platformOption: Option[Platform] = None,
    duration: Option[Duration] = Some(Duration.ofHours(3)),
    durationStyle: DurationStyle = DurationStyle.DayWindow,
  )(implicit dataGeneration: DataGeneration): StoredAssessment = {
    val deptCode = dataGeneration.fakeDept
    val stemModuleCode =  f"$deptCode${dataGeneration.random.between(101, 999)}%03d"
    val cats =   f"${dataGeneration.random.between(1, 99)}%02d"

    val date = LocalDate.of(2018, 1, 1)
    val localCreateTime = LocalDateTime.of(date, LocalTime.of(8, 0, 0, 0))
    val localStartTime = LocalDateTime.of(date, LocalTime.of(dataGeneration.random.between(9, 15), 0, 0, 0))
    val createTime = localCreateTime.atOffset(zone.getRules.getOffset(localCreateTime))
    val startTime = localStartTime.atOffset(zone.getRules.getOffset(localStartTime))
    val paperCode = s"$stemModuleCode${dataGeneration.random.between(1, 9)}"
    val platform = platformOption.getOrElse(Platform.values(dataGeneration.random.nextInt(Platform.values.size)))
    val moduleCode =  s"$stemModuleCode-$cats"
    val sequence = f"E${dataGeneration.random.between(1, 9)}%02d"

    StoredAssessment(
      id = uuid,
      paperCode = paperCode,
      section = None,
      title = dataGeneration.fakeTitle,
      startTime = Some(startTime),
      duration = duration,
      platform = Set(platform),
      durationStyle = durationStyle,
      storedBrief = makeStoredBrief(Set(platform)),
      invigilators = List(invigilator1, invigilator2),
      state = Assessment.State.Draft,
      tabulaAssessmentId = None,
      tabulaAssignments = Nil,
      examProfileCode = "EXSUM20",
      moduleCode = moduleCode,
      departmentCode = DepartmentCode(deptCode),
      sequence = sequence,
      created = createTime,
      version = createTime
    )
  }

  def makeStoredStudentAssessment(
    assessmentId: UUID,
    studentId: UniversityID,
    studentAssessmentId: UUID = UUID.randomUUID
  ): StoredStudentAssessment = {

    val createTime = LocalDateTime.of(2016, 1, 1, 8, 0, 0, 0)
    // Random but deterministic for a given student
    val r = new Random(studentId.string.toLong)
    val twentyPercentChance = r.nextInt(5) == 0
    val extraTimeAdjustmentPerHour = Option.when(twentyPercentChance)(Duration.ofMinutes(extraTimeAdjustmentDurations(r.nextInt(extraTimeAdjustmentDurations.length))))

    StoredStudentAssessment(
      id = studentAssessmentId,
      assessmentId = assessmentId,
      occurrence = None,
      academicYear = None,
      studentId = studentId,
      inSeat = false,
      startTime = None,
      extraTimeAdjustmentPerHour = extraTimeAdjustmentPerHour,
      finaliseTime = None,
      uploadedFiles = List.empty,
      tabulaSubmissionId = None,
      created = createTime.asOffsetDateTime,
      version = createTime.asOffsetDateTime,
    )
  }
}
