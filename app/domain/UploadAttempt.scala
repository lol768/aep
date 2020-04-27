package domain

import java.util.UUID

import enumeratum.{Enum, EnumEntry}
import play.api.libs.json.{Json, OWrites, Reads}
import warwick.fileuploads.UploadedFileSave

case class UploadAttempt(var source: String, proposedFiles: Seq[SelectedFile], studentAssessmentId: UUID, message: Option[String] = None)

object UploadAttempt {
  implicit val writesInnerFile: OWrites[SelectedFile] = Json.writes[SelectedFile]
  implicit val readsInnerFile: Reads[SelectedFile] = Json.reads[SelectedFile]
  implicit val writesEnclosing: OWrites[UploadAttempt] = Json.writes[UploadAttempt]
  implicit val readsEnclosing: Reads[UploadAttempt] = Json.reads[UploadAttempt]
  val websocketType = "UploadAttempt"
}

case class SelectedFile(lastModified: Long, name: String, size: Long, mimeType: String, headerHex: String) {
  def nameAndSize: String = s"$name ($size)"
}

object SelectedFile {
  def fromUploadedFileSave(ufs: UploadedFileSave): SelectedFile = SelectedFile(
    ufs.uploadStarted.toEpochSecond,
    ufs.fileName,
    ufs.contentLength,
    "application/octet-stream",
    ""
  )
}

case class UploadCancellation(id: UUID)

object UploadCancellation {
  implicit val readsUploadCancellation = Json.reads[UploadCancellation]
}

sealed trait UploadFailureType extends EnumEntry

object UploadFailureType extends Enum[UploadFailureType] {
  case object BadForm extends UploadFailureType
  case object BadFileParts extends UploadFailureType
  case object MissingFiles extends UploadFailureType
  case object DuplicateFiles extends UploadFailureType

  override val values: IndexedSeq[UploadFailureType] = findValues
}
