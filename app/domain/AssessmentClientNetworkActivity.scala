package domain

import java.time.{Duration, OffsetDateTime}
import java.util.UUID

import helpers.LenientTimezoneNameParsing._
import play.api.libs.json.{Json, Writes}

case class AssessmentClientNetworkActivity (
  downlink: Option[Double], // mbps
  downlinkMax: Option[Double], // mbps
  effectiveType: Option[String], // 'slow-2g', '2g', '3g', or '4g',
  rtt: Option[Int], // rounded to nearest 25ms
  `type`: Option[String], // bluetooth, cellular, ethernet, none, wifi, wimax, other, unknown
  studentAssessmentId: UUID,
  localTimezoneName: Option[LenientZoneId],
  timestamp: OffsetDateTime = OffsetDateTime.now,
) {
  def isOnline = Duration.between(timestamp, OffsetDateTime.now).compareTo(Duration.ofMinutes(2L)) < 0;
}

object AssessmentClientNetworkActivity {
  def tupled = (apply _).tupled

  val writesAssessmentClientNetworkActivity: Writes[AssessmentClientNetworkActivity] = Json.writes[AssessmentClientNetworkActivity]
}
