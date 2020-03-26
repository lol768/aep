package domain

import java.time.OffsetDateTime
import java.util.UUID

import warwick.fileuploads.UploadedFile
import warwick.sso.UniversityID

sealed trait BaseStudentAssessment {
  def assessmentId: UUID
  def studentId: UniversityID
  def inSeat: Boolean
  def startTime: Option[OffsetDateTime]
  def finaliseTime: Option[OffsetDateTime]
}

case class StudentAssessment(
  id: UUID,
  assessmentId: UUID,
  studentId: UniversityID,
  inSeat: Boolean,
  startTime: Option[OffsetDateTime],
  finaliseTime: Option[OffsetDateTime],
  uploadedFiles: Seq[UploadedFile]
) extends BaseStudentAssessment

case class StudentAssessmentMetadata(
  assessmentId: UUID,
  studentId: UniversityID,
  inSeat: Boolean,
  startTime: Option[OffsetDateTime],
  finaliseTime: Option[OffsetDateTime],
  uploadedFileCount: Int
) extends BaseStudentAssessment
