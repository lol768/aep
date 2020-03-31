package services

import java.time.{Duration, LocalDate, LocalDateTime, LocalTime}
import java.util.UUID

import com.google.inject.ImplementedBy
import domain.Assessment.Platform.OnlineExams
import domain.{Assessment, StudentAssessment}
import domain.Assessment.{AssessmentType, Platform}
import domain.dao.DaoRunner
import domain.dao.AssessmentsTables.{StoredAssessment, StoredBrief}
import domain.dao.StudentAssessmentsTables.StoredStudentAssessment
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
  implicit val ec: ExecutionContext,
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
            if (insertedAssessment.platform == OnlineExams) { // Guard against calling this for other platforms
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

  import warwick.core.helpers.JavaTime.{timeZone => zone}
  import helpers.DateConversion


  val invigilator1 = "Mary"
  val invigilator2 = "Bob"

  def makeStoredBrief: StoredBrief =
    StoredBrief(
      Some(DataGeneration.dummyWords(Random.between(6,30))),
      Seq.empty,
      Some(DataGeneration.fakePath)
    )

  def makeStoredAssessment(uuid: UUID = UUID.randomUUID, platformOption: Option[Platform] = None): StoredAssessment = {
    val date = LocalDate.of(2018, 1, 1)
    val localCreateTime = LocalDateTime.of(date, LocalTime.of(8, 0, 0, 0))
    val localStartTime = LocalDateTime.of(date, LocalTime.of(Random.between(9, 15), 0, 0, 0))
    val createTime = localCreateTime.atOffset(zone.getRules.getOffset(localCreateTime))
    val startTime = localStartTime.atOffset(zone.getRules.getOffset(localStartTime))
    val code = f"${DataGeneration.fakeDept}${Random.between(101, 999)}%03d-${Random.between(1, 99)}%02d"
    val platform = platformOption.getOrElse(Platform.values(Random.nextInt(Platform.values.size)))
    val assType = AssessmentType.values(Random.nextInt(AssessmentType.values.size))

    StoredAssessment(
      id = uuid,
      code = code,
      title = DataGeneration.fakeTitle,
      startTime = Some(startTime),
      duration = Duration.ofHours(3),
      platform = platform,
      assessmentType = assType,
      storedBrief = makeStoredBrief,
      invigilators = List(invigilator1, invigilator2),
      state = Assessment.State.Draft,
      created = createTime,
      version = createTime
    )
  }

  def makeStoredStudentAssessment(
    assessmentId: UUID,
    studentId: UniversityID,
    studentAssessmentId: UUID = UUID.randomUUID
  ): StoredStudentAssessment = {
    import DateConversion._
    val createTime = LocalDateTime.of(2016, 1, 1, 8, 0, 0, 0)
    StoredStudentAssessment(
      id = studentAssessmentId,
      assessmentId = assessmentId,
      studentId = studentId,
      inSeat = false,
      startTime = None,
      finaliseTime = None,
      uploadedFiles = List.empty,
      created = createTime.asOffsetDateTime,
      version = createTime.asOffsetDateTime,
    )
  }
}