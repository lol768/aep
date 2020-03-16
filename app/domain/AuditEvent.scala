package domain

import java.time.OffsetDateTime
import java.util.UUID

import play.api.libs.json.JsValue
import warwick.core.helpers.JavaTime
import warwick.sso.Usercode

case class AuditEvent(
  id: UUID = UUID.randomUUID(),
  date: OffsetDateTime = JavaTime.offsetDateTime,
  operation: Symbol,
  usercode: Option[Usercode],
  data: JsValue,
  targetId: String,
  targetType: Symbol
)

object AuditEvent {
  def tupled = (apply _).tupled

  object Target {
    val UploadedFile = Symbol("UploadedFile")
  }

  object Operation {
    object UploadedFile {
      val Save = Symbol("UploadedFileStore")
      val Delete = Symbol("UploadedFileDelete")
    }
  }
}