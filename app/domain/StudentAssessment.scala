package domain

import java.time.{Duration, OffsetDateTime}
import java.util.UUID

import warwick.fileuploads.UploadedFile
import warwick.sso.UniversityID

sealed trait BaseStudentAssessment {
  val assessmentId: UUID
  val studentId: UniversityID
  val inSeat: Boolean
  val startTime: Option[OffsetDateTime]
  val extraTimeAdjustment: Option[Duration]
  val finaliseTime: Option[OffsetDateTime]

  val hasFinalised: Boolean = finaliseTime.nonEmpty

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
  studentId: UniversityID,
  inSeat: Boolean,
  startTime: Option[OffsetDateTime],
  extraTimeAdjustment: Option[Duration],
  finaliseTime: Option[OffsetDateTime],
  uploadedFiles: Seq[UploadedFile]
) extends BaseStudentAssessment {

  lazy val mostRecentFileUpload: Option[OffsetDateTime] = uploadedFiles.view.map(_.uploadStarted).maxOption

}
