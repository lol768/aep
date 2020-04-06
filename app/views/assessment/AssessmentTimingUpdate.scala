package views.assessment

import java.util.UUID

import play.api.libs.json.{Json, OWrites}

/** Message sent by Websocket to be processed by assessment-timing  */
case class AssessmentTimingUpdate(
  id: UUID,
  startTime: Option[Long],
  hasStarted: Boolean,
  hasFinalised: Boolean,
)

object AssessmentTimingUpdate {
  implicit val writes: OWrites[AssessmentTimingUpdate] = Json.writes[AssessmentTimingUpdate]
}