import java.time.format.DateTimeFormatter
import java.time.{Duration, LocalTime, OffsetDateTime}

import domain.Fixtures
import support.BrowserFeatureSpec
import warwick.core.helpers.JavaTime

import scala.language.postfixOps
import scala.languageFeature.postfixOps

class FixedStartAssessmentSpec extends BrowserFeatureSpec {

  import Fixtures.users.{student1 => student}

  "A student" should {
    "be able to view an upcoming assessment" in {
      Given.i_am_a_student()
      And i_have_a_fixed_start_assessment_to_take(student, startTime = JavaTime.offsetDateTime.plusMinutes(5))
      When i_visit_the assessmentPage

      Then i_should_see_the_text "This is a fixed time assessment. It does not run in a 24 hour window, so you must begin at the start time."
      And i_should_see_the_text "This assessment will start at"
    }

    "be able to start an assessment that has started" in {
      val startTime = JavaTime.offsetDateTime.minusMinutes(30)

      Given.i_am_a_student()
      And i_have_a_fixed_start_assessment_to_take(student, startTime = startTime)
      When i_visit_the assessmentPage

      Then i_should_see_the_text "This assessment began at"
      And i_should_see_the_text "Start now."

      And.i_click_to_start_the_assessment()
      Then i_should_see_the_text "This assessment is my original work"
      And i_should_see_the_text "I declare I will comply with this statement"
      And.the_authorship_declaration_button_is_disabled()

      When.i_tick_the_authorship_declaration()
      Then.the_authorship_declaration_button_is_enabled()

      When.i_click_to_confirm_the_authorship_declaration()

      Then i_should_see_the_text "I have already agreed reasonable adjustments"
      And.the_ra_declaration_button_is_disabled()

      When.i_choose_the_no_ra_declaration()
      Then.the_ra_declaration_button_is_enabled()

      When.i_choose_the_has_ra_declaration()
      Then.the_ra_declaration_button_is_enabled()

      When.i_click_to_confirm_the_ra_declaration()
      Then i_should_see_the_text "The assessment has begun."
      And i_should_see_the_text "This is a fixed time assessment. It does not run in a 24 hour window, so you must begin at the start time."

      screenshot("Fixed time assessment begun")

      val uploadGraceStart = startTime.plusHours(3)
      val onTimeEnd = startTime.plusHours(3).plusMinutes(45)
      val lateEnd = startTime.plusHours(3).plusMinutes(45).plusHours(2)
      def time(odt: OffsetDateTime) = JavaTime.Relative(odt, printToday = false)

      And i_should_see_the_text s"you should be aiming to finish answering by ${time(uploadGraceStart)}"
      And i_should_see_the_text s"${time(uploadGraceStart)} You have 45 minutes until ${time(onTimeEnd)} to upload your answers"
      And i_should_not_see_the_text s"${time(onTimeEnd)} Your assessment is now late"
      And i_should_see_the_text s"${time(lateEnd)} You can't upload answers at all after this point"
    }

    "be able to view an ended assessment" in {
      Given.i_am_a_student()
      And i_have_a_fixed_start_assessment_to_take(student, startTime = JavaTime.offsetDateTime.minusHours(8))
      When i_visit_the assessmentPage

      Then i_should_see_the_text ("The assessment has ended.")
    }
  }
}
