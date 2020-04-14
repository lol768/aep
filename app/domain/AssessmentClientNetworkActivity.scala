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
  def isOnline = Duration.between(timestamp, OffsetDateTime.now).compareTo(Duration.ofMinutes(2L)) < 0

  // Returns an int between 1 and 5 if we can make assertions
  val signalStrength: Option[Int] =
    if (effectiveType.contains("slow-2g") || rtt.exists(_ >= 1000) || downlink.exists(_ <= 0.1d)) Some(1)
    else if (effectiveType.contains("2g") || rtt.exists(_ >= 800) || downlink.exists(_ <= 0.2d)) Some(2)
    else if (effectiveType.contains("3g") || rtt.exists(_ >= 500) || downlink.exists(_ <= 0.5d)) Some(3)
    else if (rtt.exists(_ >= 200) || downlink.exists(_ <= 2.0d)) Some(4)
    else if (rtt.exists(_ < 200 && downlink.exists(_ > 2.0d))) Some(5)
    else None // We can't make a judgement
}

object AssessmentClientNetworkActivity {
  def tupled = (apply _).tupled

  val writesAssessmentClientNetworkActivity: Writes[AssessmentClientNetworkActivity] = Json.writes[AssessmentClientNetworkActivity]
}
