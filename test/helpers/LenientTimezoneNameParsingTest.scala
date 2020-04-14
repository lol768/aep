package helpers

import java.time.ZoneId

import helpers.LenientTimezoneNameParsing._
import org.scalatestplus.play.PlaySpec

class LenientTimezoneNameParsingTest extends PlaySpec {

  "LenientTimezoneNameParsing" should {
    "convert from a timezone name to a ZoneId where valid" in {
      "Europe/London".maybeZoneId mustBe Right(ZoneId.of("Europe/London"))
      "America/Paris".maybeZoneId mustBe Left("America/Paris")
      (null: String).maybeZoneId mustBe Left(null)
    }

    "convert from a lenient ZoneId to the timezone name" in {
      Right(ZoneId.of("Europe/London")).timezoneName mustBe "Europe/London"
      Left("America/Paris").timezoneName mustBe "America/Paris"
      Left(null).timezoneName mustBe (null: String)
    }
  }

}
