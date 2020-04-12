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

  val uploadedFileCount = uploadedFiles.size

  /**
    * Submission time is the most recent time that an upload was _started_
    */
  def submissionTime: Option[OffsetDateTime] = uploadedFiles.view.map(_.uploadStarted).maxOption

}
