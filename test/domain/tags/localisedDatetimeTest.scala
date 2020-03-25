package domain.tags

import java.time.{Instant, OffsetDateTime}

import specs.BaseSpec
import views.html.tags.localisedDatetime
import warwick.core.helpers.JavaTime

class localisedDatetimeTest extends BaseSpec {

  // We're going to pretend it's always 24/03/2020
  private val march24_1030 = OffsetDateTime.ofInstant(Instant.ofEpochMilli(1585045800000L), JavaTime.timeZone)

  "localisedDatetimeTest" should {

    "output a correctly formatted localised date time and element for JDDT to populate" in {
      val result = localisedDatetime(march24_1030).body
      result must include("Tue 24th Mar")
      result must include("10:30")
      result must include("Europe/London")
      result must include("class=\"jddt\"")
      result must include("data-millis=\"1585045800000\"")
      result must include("data-server-timezone-offset=\"0\"")
      result must include("data-server-timezone-name=\"Europe/London\"")
    }

    "correctly identify when the supplied date is today" in {
      localisedDatetime(JavaTime.offsetDateTime).body must include("Today")
    }

    "include the year when it's different to the current year" in {
      localisedDatetime(march24_1030.minusYears(1)).body must include("2019")
    }

  }

}
