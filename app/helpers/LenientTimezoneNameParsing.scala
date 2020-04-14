package helpers

import java.time.ZoneId

import play.api.libs.json.{JsString, Reads, Writes}

import scala.util.{Success, Try}

object LenientTimezoneNameParsing {
  type LenientZoneId = Either[String, ZoneId]

  implicit val readsLenientZoneId: Reads[LenientZoneId] = implicitly[Reads[String]].map(_.maybeZoneId)
  implicit val writesLenientZoneId: Writes[LenientZoneId] = o => JsString(o.timezoneName)

  implicit class TimezoneNameConversion(val timezoneName: String) extends AnyVal {
    def maybeZoneId: LenientZoneId = Try(ZoneId.of(timezoneName)) match {
      case Success(zone) => Right(zone)
      case _ => Left(timezoneName)
    }
  }

  implicit class MaybeZoneIdConversion(val maybeZoneId: LenientZoneId) extends AnyVal {
    def timezoneName: String = maybeZoneId match {
      case Left(timezoneName) => timezoneName
      case Right(zone) => zone.getId
    }
  }
}
