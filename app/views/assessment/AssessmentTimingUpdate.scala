package views.assessment

import java.time.{Duration, OffsetDateTime}
import java.util.UUID

import domain.BaseSitting.{ProgressState, SubmissionState}
import play.api.libs.json._

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
  submissionState: SubmissionState
)

object AssessmentTimingUpdate {
  private implicit val offsetDateTimeWrites: Writes[OffsetDateTime] = (o: OffsetDateTime) => JsNumber(o.toInstant.toEpochMilli)
  private implicit val durationWrites: Writes[Duration] = (o: Duration) => JsNumber(o.toMillis)
  implicit val writes: OWrites[AssessmentTimingUpdate] = Json.writes[AssessmentTimingUpdate]
}
