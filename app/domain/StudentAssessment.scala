package domain

import java.time.OffsetDateTime
import java.util.UUID

import warwick.fileuploads.UploadedFile
import warwick.sso.UniversityID

case class StudentAssessment(
  assessmentId: UUID,
  studentId: UniversityID,
  inSeat: Boolean,
  startTime: Option[OffsetDateTime],
  uploadedFiles: Seq[UploadedFile]
)
