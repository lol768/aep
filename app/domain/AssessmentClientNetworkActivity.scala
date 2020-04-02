package domain

import java.time.OffsetDateTime
import java.util.UUID
import warwick.core.helpers.JavaTime

case class AssessmentClientNetworkActivity (
  downlink: Option[Double], // mbps
  downlinkMax: Option[Double], // mbps
  effectiveType: Option[String], // 'slow-2g', '2g', '3g', or '4g',
  rtt: Option[Int], // rounded to nearest 25ms
  `type`: Option[String], // bluetooth, cellular, ethernet, none, wifi, wimax, other, unknown
  studentAssessmentId: UUID,
  timestamp: OffsetDateTime = JavaTime.offsetDateTime,
)
