package domain.dao

import java.time.{OffsetDateTime, ZoneId}
import java.util.UUID

import domain.{AssessmentClientNetworkActivity, Fixtures}
import helpers.CleanUpDatabaseAfterEachTest
import warwick.core.helpers.JavaTime
import warwick.sso.Usercode

import scala.concurrent.Future

class AssessmentClientNetworkActivityDaoTest extends AbstractDaoTest with CleanUpDatabaseAfterEachTest {

  private val assessmentDao = get[AssessmentDao]
  private val saDao = get[StudentAssessmentDao]
  private val dao = get[AssessmentClientNetworkActivityDao]

  "AssessmentClientNetworkActivityDao" should {
    "fetch the latest network activity by student assessment ID" in {
      def networkActivity(studentAssessmentId: UUID, timezone: ZoneId, timestamp: OffsetDateTime): AssessmentClientNetworkActivity =
        AssessmentClientNetworkActivity(
          studentAssessmentId = Some(studentAssessmentId),
          localTimezoneName = Some(Right(timezone)),
          timestamp = timestamp,
          downlink = None,
          downlinkMax = None,
          effectiveType = None,
          rtt = None,
          `type` = None,
        )

      // Oldest to newest
      val odt1 = JavaTime.offsetDateTime.minusMinutes(5)
      val odt2 = JavaTime.offsetDateTime.minusMinutes(4)
      val odt3 = JavaTime.offsetDateTime.minusMinutes(3)
      val odt4 = JavaTime.offsetDateTime.minusMinutes(2)
      val odt5 = JavaTime.offsetDateTime.minusMinutes(1)

      val test = for {
        ass <- assessmentDao.insert(Fixtures.assessments.storedAssessment())

        sa1 <- saDao.insert(Fixtures.studentAssessments.storedStudentAssessment(assId = ass.id, studentId = Fixtures.users.student1.universityId.get))
        sa2 <- saDao.insert(Fixtures.studentAssessments.storedStudentAssessment(assId = ass.id, studentId = Fixtures.users.student2.universityId.get))

        _ <- dao.insert(networkActivity(sa1.id, JavaTime.timeZone, odt1))
        _ <- dao.insert(networkActivity(sa1.id, JavaTime.timeZone, odt2))
        latestSA1 <- dao.insert(networkActivity(sa1.id, JavaTime.timeZone, odt3))
        _ <- dao.insert(networkActivity(sa2.id, JavaTime.timeZone, odt4))
        latestSA2 <- dao.insert(networkActivity(sa2.id, JavaTime.timeZone, odt5))

        result <- dao.getLatestActivityFor(Seq(sa1.id, sa2.id))

        _ <- DBIO.from(Future.successful {
          result.size mustBe 2
          result.toSet mustBe Set(latestSA1, latestSA2)
        })
      } yield result

      exec(test)
    }

    "fetch the latest invigilator activity by assessment ID" in {
      def networkActivity(assessmentId: UUID, usercode: Usercode, timezone: ZoneId, timestamp: OffsetDateTime): AssessmentClientNetworkActivity =
        AssessmentClientNetworkActivity(
          assessmentId = Some(assessmentId),
          usercode = Some(usercode),
          localTimezoneName = Some(Right(timezone)),
          timestamp = timestamp,
          downlink = None,
          downlinkMax = None,
          effectiveType = None,
          rtt = None,
          `type` = None,
          studentAssessmentId = None,
        )

      // Oldest to newest
      val odt1 = JavaTime.offsetDateTime.minusMinutes(5)
      val odt2 = JavaTime.offsetDateTime.minusMinutes(4)
      val odt3 = JavaTime.offsetDateTime.minusMinutes(3)
      val odt4 = JavaTime.offsetDateTime.minusMinutes(2)
      val odt5 = JavaTime.offsetDateTime.minusMinutes(1)

      val user1 = Fixtures.users.staff1.usercode
      val user2 = Fixtures.users.staff2.usercode

      val test = for {
        ass <- assessmentDao.insert(Fixtures.assessments.storedAssessment())

        _ <- dao.insert(networkActivity(ass.id, user1, JavaTime.timeZone, odt1))
        _ <- dao.insert(networkActivity(ass.id, user1, JavaTime.timeZone, odt2))
        latestUser1 <- dao.insert(networkActivity(ass.id, user1, JavaTime.timeZone, odt3))
        _ <- dao.insert(networkActivity(ass.id, user2, JavaTime.timeZone, odt4))
        latestUser2 <- dao.insert(networkActivity(ass.id, user2, JavaTime.timeZone, odt5))

        result <- dao.getLatestInvigilatorActivityFor(ass.id)

        _ <- DBIO.from(Future.successful {
          result.size mustBe 2
          result.toSet mustBe Set(latestUser1, latestUser2)
        })
      } yield result

      exec(test)
    }
  }

}
