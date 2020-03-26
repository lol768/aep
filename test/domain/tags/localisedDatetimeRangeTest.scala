package domain.tags

import java.time.{Instant, OffsetDateTime}

import specs.BaseSpec
import views.html.tags.localisedDatetimeRange
import warwick.core.helpers.JavaTime

class localisedDatetimeRangeTest extends BaseSpec {

  // We're going to pretend it's always 24/03/2020
  private val march24_1030 = OffsetDateTime.ofInstant(Instant.ofEpochMilli(1585045800000L), JavaTime.timeZone)
  private val march24_1130 = march24_1030.plusHours(1L)

  "localisedDatetimeTest" should {

    "output a correctly formatted localised date time and element for JDDT to populate" in {
      val result = localisedDatetimeRange(march24_1030, march24_1130).body.replaceAll("\\s+", " ")
      result must include("Tue 24th Mar")
      result must include("10:30 to 11:30")
      result must include("Europe/London")
      result must include("class=\"jddt-range\"")
      result must include("data-from-millis=\"1585045800000\"")
      result must include("data-to-millis=\"1585049400000\"")
      result must include("data-server-timezone-offset=\"0\"")
      result must include("data-server-timezone-name=\"Europe/London\"")
    }

    "correctly identify when the supplied date is today" in {
      localisedDatetimeRange(JavaTime.offsetDateTime, JavaTime.offsetDateTime.plusHours(1L)).body must include("Today")
    }

    "include the year when it's different to the current year" in {
      localisedDatetimeRange(march24_1030.minusYears(1), march24_1130.minusYears(1)).body must include("'19")
    }

    "include both dates but only one instance of the month when it's different days but the same month" in {
      val result = localisedDatetimeRange(march24_1030.minusDays(1), march24_1030).body.replaceAll("\\s+", " ")
      "Mar".r.findAllMatchIn(result).length mustBe 1
      result must include("Mon 23rd")
      result must include("Tue 24th")
    }

    "not include the from month when it's the same as the to month" in {
      "Mar".r.findAllMatchIn(localisedDatetimeRange(march24_1030, march24_1130).body).length mustBe 1
    }

    "include the days and dates of both datetimes when they're in different months" in {
      val result = localisedDatetimeRange(march24_1030.minusMonths(1), march24_1030).body.replaceAll("\\s+", " ")
      result must include("Mon 24th Feb")
      result must include("Tue 24th Mar")
    }

    "not include the from day and date when it's the same as the to date" in {
      "Tue 24th".r.findAllMatchIn(
        localisedDatetimeRange(march24_1030, march24_1130).body.replaceAll("\\s+", " ")
      ).length mustBe 1
    }

  }

}
