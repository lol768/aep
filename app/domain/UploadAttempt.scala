package domain

import java.util.UUID

import play.api.libs.json.{Json, OWrites, Reads}

object UploadAttempt {
  implicit val writesInnerFile: OWrites[SelectedFile] = Json.writes[SelectedFile]
  implicit val readsInnerFile: Reads[SelectedFile] = Json.reads[SelectedFile]
  implicit val writesEnclosing: OWrites[UploadAttempt] = Json.writes[UploadAttempt]
  implicit val readsEnclosing: Reads[UploadAttempt] = Json.reads[UploadAttempt]
  val websocketType = "UploadAttempt"
}

case class UploadAttempt(var source: String, proposedFiles: Seq[SelectedFile], studentAssessmentId: UUID)

case class SelectedFile(lastModified: Long, name: String, size: Long, mimeType: String, headerHex: String)
