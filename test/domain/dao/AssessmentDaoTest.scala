package domain.dao

import java.time.ZonedDateTime
import java.util.UUID

import domain._
import uk.ac.warwick.util.core.DateTimeUtils
import warwick.core.helpers.JavaTime

import scala.concurrent.Future

class AssessmentDaoTest extends AbstractDaoTest {

  private val dao = get[AssessmentDao]

  "AssessmentDao" should {
    "save an assessment" in {
      val now = ZonedDateTime.of(2019, 1, 1, 10, 0, 0, 0, JavaTime.timeZone).toInstant

      DateTimeUtils.useMockDateTime(now, () => {
        val id = UUID.randomUUID
        val ass = Fixtures.assessments.storedAssessment(id)

        val test = for {
          result <- dao.insert(ass)
          existsAfter <- dao.getById(id)
          _ <- DBIO.from(Future {
            result.version.toInstant.equals(now) mustBe true

            result.code mustBe "true"
            result.code mustBe ass.code
            result.title mustBe ass.title
            result.assessmentType mustBe ass.assessmentType
            result.platform mustBe ass.platform
            result.startTime mustBe ass.startTime
            result.duration mustBe ass.duration

            existsAfter mustBe result
          })
        } yield result

        exec(test)
      })
    }
  }
}
