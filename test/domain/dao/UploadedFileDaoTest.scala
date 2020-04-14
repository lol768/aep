package domain.dao

import java.time.{Clock, Duration, OffsetDateTime, ZonedDateTime}
import java.util.UUID

import domain.Fixtures
import domain.Fixtures.{assessments, studentAssessments, uploadedFiles}
import helpers.CleanUpDatabaseAfterEachTest
import uk.ac.warwick.util.core.DateTimeUtils
import warwick.core.helpers.JavaTime

import scala.concurrent.Future

class UploadedFileDaoTest extends AbstractDaoTest with CleanUpDatabaseAfterEachTest {

  private val dao = get[UploadedFileDao]

  "StoredUploadedFile" should {
    "convert to UploadedFile" in {
      val stored = Fixtures.uploadedFiles.storedUploadedStudentAssessmentFile(
        studentAssessmentId = UUID.randomUUID()
      ).copy(
        created = OffsetDateTime.parse("2020-04-13T13:00:00+01:00"),
        uploadStarted = OffsetDateTime.parse("2020-04-13T12:55:00+01:00"),
      )

      val file = stored.asUploadedFile
      file.created mustBe stored.created
      file.uploadStarted mustBe stored.uploadStarted
    }
  }

  "UploadedFileDao" should {
    val now = ZonedDateTime.of(2019, 1, 1, 10, 0, 0, 0, JavaTime.timeZone).toInstant

    "save and retrieve an uploaded file by ID" in {
      DateTimeUtils.useMockDateTime(now, () => {
        val file = uploadedFiles.storedUploadedAssessmentFile()

        val test = for {
          result <- dao.insert(file)
          existsAfter <- dao.find(file.id)
          _ <- DBIO.from(Future.successful {
            result.created.toInstant mustBe file.created.toInstant
            result.version.toInstant mustBe now
            result.id mustBe file.id
            result.fileName mustBe file.fileName
            result.contentType mustBe file.contentType
            result.contentLength mustBe file.contentLength
            result.uploadedBy mustBe file.uploadedBy
            result.uploadStarted.toInstant mustBe file.uploadStarted.toInstant
            result.ownerId mustBe file.ownerId
            result.ownerType mustBe file.ownerType

            existsAfter must contain(result)
          })
        } yield result

        exec(test)
      })
    }

    "fetch all, find multiple IDs, and delete a file" in {
      val earlier = now.minusSeconds(600)

      val test = for {
        _ <- DBIO.from(Future.successful {
          DateTimeUtils.CLOCK_IMPLEMENTATION = Clock.fixed(earlier, JavaTime.timeZone)
        })

        files = (1 to 10).map(_ => uploadedFiles.storedUploadedAssessmentFile())
        _ <- DBIO.sequence(files.map(dao.insert))

        _ <- DBIO.from(Future.successful {
          DateTimeUtils.CLOCK_IMPLEMENTATION = Clock.fixed(now, JavaTime.timeZone)
        })

        all <- dao.allWithoutOwner

        _ <- DBIO.from(Future.successful {
          all.length mustEqual 10
          all.map(_.id).distinct.length mustEqual 10
        })

        firstTwo = Seq(files(0).id, files(1).id)
        findFirstTwo <- dao.find(firstTwo)

        _ <- DBIO.from(Future.successful {
          findFirstTwo.length mustEqual 2
          findFirstTwo.map(_.id) mustEqual firstTwo
        })

        afterDelete <- dao.delete(files(0).id) andThen dao.allWithoutOwner

        _ <- DBIO.from(Future.successful {
          afterDelete.length mustEqual 9
          afterDelete.map(_.id).distinct.length mustEqual 9
          afterDelete.count(_.id == files(0).id) mustEqual 0
        })
      } yield afterDelete

      exec(test)
      DateTimeUtils.CLOCK_IMPLEMENTATION = Clock.systemDefaultZone
    }
  }
}
