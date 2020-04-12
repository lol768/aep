package services

import java.util.UUID

import akka.Done
import domain.Fixtures.assessments
import domain.{AssessmentClientNetworkActivity, Fixtures, Page}
import domain.dao.{AbstractDaoTest, AssessmentDao, StudentAssessmentDao}
import helpers.CleanUpDatabaseAfterEachTest
import warwick.core.helpers.JavaTime
import warwick.core.system.AuditLogContext
import warwick.sso.{UniversityID, Usercode}

class AssessmentClientNetworkActivityServiceTest extends AbstractDaoTest with CleanUpDatabaseAfterEachTest {

  override implicit val auditLogContext: AuditLogContext = AuditLogContext.empty(timingContext.timingData)
    .copy(usercode = Some(Usercode("12345678")))

  private lazy val activityService = get[AssessmentClientNetworkActivityService]
  private lazy val studentAssessmentService = get[StudentAssessmentService]

  private trait Fixture {
    private val assessmentDao = get[AssessmentDao]
    private val studentAssessmentDao = get[StudentAssessmentDao]

    // Set up some test assessments
    val storedAssessment = Fixtures.assessments.storedAssessment().copy(startTime = Some(JavaTime.offsetDateTime.minusHours(1)))
    execWithCommit(DBIO.sequence(Seq(storedAssessment).map(assessmentDao.insert)))

    val storedStudentAssessment = Fixtures.studentAssessments.storedStudentAssessment(storedAssessment.id, UniversityID("1234567"))
    execWithCommit(DBIO.sequence(Seq(storedStudentAssessment).map(studentAssessmentDao.insert)))
  }

  "AssessmentClientNetworkActivityService" should {
    "save an assessmentClientNetworkActivity in the database" in new Fixture {

      val base = studentAssessmentService.getSitting(storedStudentAssessment.studentId, storedStudentAssessment.assessmentId).serviceValue.get

      val studentAssessmentId = base.studentAssessment.id

      val activity = createActivity(studentAssessmentId)
      val uploadedFile = activityService.record(activity)
      uploadedFile.serviceValue mustBe Done

      activityService.findByStudentAssessmentId(studentAssessmentId).serviceValue.head.effectiveType mustBe Some("Horse")
    }

    "get client activity for" in new Fixture {
      val base = studentAssessmentService.getSitting(storedStudentAssessment.studentId, storedStudentAssessment.assessmentId).serviceValue.get

      val studentAssessmentId = base.studentAssessment.id
      val activity = createActivity(studentAssessmentId)
      for( w <- 0 to 100)  {
        activityService.record(activity).serviceValue
      }
      val studentAssessments = Seq(storedStudentAssessment.asStudentAssessment(Map.empty))
      val pageOffset = Page(10,15)
      val (count,results) = activityService.getClientActivityFor(studentAssessments, None, None, pageOffset).serviceValue
      count mustBe 101
      results.size mustBe 15
    }
  }

  private def createActivity(studentAssessmentId: UUID) = AssessmentClientNetworkActivity(
    downlink = Some(10.0000),
    downlinkMax = Some(10.0000),
    effectiveType = Some("Horse"),
    rtt = Some(50),
    `type` = Some("Tin-can"),
    studentAssessmentId = studentAssessmentId,
    localTimezoneName = Some(Right(JavaTime.timeZone)),
  )
}
