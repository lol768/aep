package domain

import java.util.UUID

import play.api.libs.json.{Json, OWrites}

object UploadAttempt {
  implicit val writesInnerFile: OWrites[SelectedFile] = Json.writes[SelectedFile]
  implicit val writesEnclosing: OWrites[UploadAttempt] = Json.writes[UploadAttempt]
}

case class UploadAttempt(source: String, files: Seq[SelectedFile], studentAssessmentId: UUID)

case class SelectedFile(lastModified: Long, name: String, size: Long, mimeType: String, headerHex: String)
