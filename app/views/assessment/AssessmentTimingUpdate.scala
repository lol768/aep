package views.assessment

import java.time.{Duration, OffsetDateTime}
import java.util.UUID

import domain.BaseSitting.ProgressState
import play.api.libs.json.{JsNumber, JsObject, JsString, JsValue, Json, OWrites, Writes}

/** Message sent by Websocket to be processed by assessment-timing  */
case class AssessmentTimingUpdate(
  id: UUID,
  windowStart: Option[OffsetDateTime],
  windowEnd: Option[OffsetDateTime],
  lastRecommendedStart: Option[OffsetDateTime],
  start: Option[OffsetDateTime],
  end: Option[OffsetDateTime],
  hasStarted: Boolean,
  hasFinalised: Boolean,
  extraTimeAdjustment: Option[Duration],
  showTimeRemaining: Boolean,
  progressState: Option[ProgressState],
)

object AssessmentTimingUpdate {
  private implicit val offsetDateTimeWrites: Writes[OffsetDateTime] = (o: OffsetDateTime) => JsNumber(o.toInstant.toEpochMilli)
  implicit val writes: OWrites[AssessmentTimingUpdate] = Json.writes[AssessmentTimingUpdate]
}
