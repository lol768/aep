package domain

import java.time.OffsetDateTime
import java.util.UUID

import warwick.fileuploads.UploadedFile
import warwick.sso.UniversityID

sealed trait BaseStudentAssessment {
  def assessment: UUID
  def studentId: UniversityID
  def inSeat: Boolean
  def startTime: Option[OffsetDateTime]
}

case class StudentAssessment(
  assessmentId: UUID,
  studentId: UniversityID,
  inSeat: Boolean,
  startTime: Option[OffsetDateTime],
  uploadedFiles: Seq[UploadedFile]
) extends BaseStudentAssessment

case class StudentAssessmentMetadata(
  assessment: UUID,
  studentId: UniversityID,
  inSeat: Boolean,
  startTime: Option[OffsetDateTime],
  uploadedFileCount: Int
) extends BaseStudentAssessment