package views.assessment

import java.util.UUID

import play.api.libs.json.{Json, OWrites}

case class AssessmentTimingInformation(
  id: UUID,
  timeRemaining: Option[Long],
  extraTimeAdjustment: Option[Long],
  timeUntilStart: Option[Long],
  timeSinceStart: Option[Long],
  timeUntilEndOfWindow: Option[Long],
  hasStarted: Boolean,
  hasFinalised: Boolean,
)

object AssessmentTimingInformation {
  implicit val writes: OWrites[AssessmentTimingInformation] = Json.writes[AssessmentTimingInformation]
}
