package domain

import enumeratum.{EnumEntry, PlayEnum}

sealed trait UploadedFileOwner extends EnumEntry
object UploadedFileOwner extends PlayEnum[UploadedFileOwner] {
  // These are cheating as they're both really Assessment
  case object AssessmentSubmissions extends UploadedFileOwner
  case object AssessmentBrief extends UploadedFileOwner

  case object StudentAssessment extends UploadedFileOwner

  val values: IndexedSeq[UploadedFileOwner] = findValues
}
