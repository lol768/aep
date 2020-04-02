package services

import java.util.UUID

import akka.Done
import domain.AssessmentClientNetworkActivity
import domain.dao.AbstractDaoTest
import helpers.CleanUpDatabaseAfterEachTest
import warwick.core.system.AuditLogContext
import warwick.sso.Usercode

class AssessmentClientNetworkActivityServiceTest extends AbstractDaoTest with CleanUpDatabaseAfterEachTest {

  override implicit val auditLogContext: AuditLogContext = AuditLogContext.empty(timingContext.timingData)
    .copy(usercode = Some(Usercode("12345678")))

  private lazy val service = get[AssessmentClientNetworkActivityService]

  "AssessmentClientNetworkActivityService" should {
    "save an assessmentClientNetworkActivity in the database" in {
      val studentAssessmentId = UUID.randomUUID

      val activity = AssessmentClientNetworkActivity(
        downlink = Some(10.0000),
        downlinkMax = Some(10.0000),
        effectiveType = Some("Horse"),
        rtt = Some(50),
        `type` = Some("Tin-can"),
        studentAssessmentId = studentAssessmentId
      )
      val uploadedFile = service.record(activity)
      uploadedFile.serviceValue mustBe Done

      service.findByStudentAssessmentId(studentAssessmentId).serviceValue.head.effectiveType mustBe Some("Horse")
    }
  }
}
