package domain

import java.time.OffsetDateTime
import java.util.UUID

import play.api.libs.json.JsValue
import warwick.sso.Usercode

case class AuditEvent(
  id: UUID = UUID.randomUUID(),
  date: OffsetDateTime = OffsetDateTime.now(),
  operation: String,
  usercode: Option[Usercode],
  data: JsValue,
  targetId: String,
  targetType: String
)

