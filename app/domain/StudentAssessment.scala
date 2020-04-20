package domain

import java.time.{Duration, OffsetDateTime}
import java.util.UUID

import uk.ac.warwick.util.termdates.AcademicYear
import warwick.fileuploads.UploadedFile
import warwick.sso.UniversityID

sealed trait BaseStudentAssessment {
  val assessmentId: UUID
  val studentId: UniversityID
  val inSeat: Boolean
  val startTime: Option[OffsetDateTime]
  val extraTimeAdjustment: Option[Duration]
  val explicitFinaliseTime: Option[OffsetDateTime]

  val hasExplicitlyFinalised: Boolean = explicitFinaliseTime.nonEmpty

  val mostRecentFileUpload: Option[OffsetDateTime]

  /**
    * The effective time that the user submitted their answers - taken as when the most
    * recently added file started to be uploaded.
    */
  val submissionTime: Option[OffsetDateTime] = mostRecentFileUpload
}

case class StudentAssessment(
  id: UUID,
  assessmentId: UUID,
  occurrence: Option[String],
  academicYear: Option[AcademicYear],
  studentId: UniversityID,
  inSeat: Boolean,
  startTime: Option[OffsetDateTime],
  extraTimeAdjustment: Option[Duration],
  explicitFinaliseTime: Option[OffsetDateTime],
  uploadedFiles: Seq[UploadedFile],
  tabulaSubmissionId: Option[UUID]
) extends BaseStudentAssessment {

  lazy val mostRecentFileUpload: Option[OffsetDateTime] = uploadedFiles.view.map(_.uploadStarted).maxOption

}
