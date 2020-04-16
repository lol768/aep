package domain

import java.time.{Duration, LocalTime}
import java.util.UUID

import org.scalatestplus.play.PlaySpec
import warwick.core.helpers.JavaTime
import warwick.fileuploads.UploadedFile

class StudentAssessmentTest extends PlaySpec {

  "StudentAssessment.submissionTime" should {
    "be empty when no files" in {
      val sa = Fixtures.studentAssessments.storedStudentAssessment(null)
      sa.uploadedFiles must have size 0
      sa.asStudentAssessment(Map.empty).submissionTime mustBe None
    }

    "be set to the most recent upload start" in {
      val saId = UUID.randomUUID()

      val files: Seq[UploadedFile] = (1 to 10).map { i =>
        Fixtures.uploadedFiles.storedUploadedStudentAssessmentFile(saId,
          createTime = JavaTime.offsetDateTime `with` (LocalTime of (10+i, 0, 0)),
          uploadDuration = Duration.ofMinutes(12)
        ).asUploadedFile
      }
      val filesMap: Map[UUID, UploadedFile] = files.map(f => f.id -> f).toMap

      val sa = Fixtures.studentAssessments.storedStudentAssessment(null).copy(
        uploadedFiles = filesMap.keys.to(List)
      ).asStudentAssessment(filesMap)
      sa.uploadedFiles must have size 10
      sa.submissionTime mustBe Some(JavaTime.offsetDateTime.`with`(LocalTime.of(19, 48, 0)))
    }
  }

}
