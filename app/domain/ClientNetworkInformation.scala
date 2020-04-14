package domain

import java.time.ZoneId
import java.util.UUID

case class ClientNetworkInformation(
  downlink: Option[Double], // mbps
  downlinkMax: Option[Double], // mbps
  effectiveType: Option[String], // 'slow-2g', '2g', '3g', or '4g',
  rtt: Option[Int], // rounded to nearest 25ms
  `type`: Option[String], // bluetooth, cellular, ethernet, none, wifi, wimax, other, unknown
  studentAssessmentId: Option[UUID],
  localTimezoneName: Option[String], // Accept a String here but store a ZoneId
)
