package helpers

import java.time.format.{DateTimeFormatter, DateTimeFormatterBuilder, TextStyle}
import java.time.{OffsetDateTime, ZoneId}
import java.util.Locale

import play.api.libs.json.{JsString, Reads, Writes}
import warwick.core.helpers.JavaTime

import scala.collection.concurrent
import scala.jdk.CollectionConverters._
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

  private val TimezoneAbbreviationFormatterCache: concurrent.Map[ZoneId, DateTimeFormatter] = concurrent.TrieMap()

  implicit class MaybeZoneIdConversion(val maybeZoneId: LenientZoneId) extends AnyVal {
    def timezoneName: String = maybeZoneId match {
      case Left(timezoneName) => timezoneName
      case Right(zone) => zone.getId
    }

    def timezoneAbbr(referenceDate: OffsetDateTime = JavaTime.offsetDateTime): String = maybeZoneId match {
      case Left(timezoneName) => timezoneName
      case Right(zone) =>
        TimezoneAbbreviationFormatterCache.getOrElseUpdate(
          zone,
          new DateTimeFormatterBuilder()
            .appendZoneText(TextStyle.SHORT, Set(zone).asJava)
            .toFormatter(Locale.UK)
        ).format(referenceDate.atZoneSameInstant(zone))
    }
  }
}
