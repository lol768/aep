import domain.Fixtures
import support.BrowserFeatureSpec

import scala.language.postfixOps
import scala.languageFeature.postfixOps

class AssessmentSpec extends BrowserFeatureSpec {

  import Fixtures.users.{student1 => student}

  "A student" should {
    "be able to complete declarations and start an assessment" in {
      Given.i_am_a_student()
      And i_have_an_online_exam_to_sit(student)
      When i_visit_the assessmentPage

      screenshot("Assessment student")

      And.i_click_to_start_the_assessment()

      screenshot("Authorship declaration")

      Then i_should_see_the_text "This assessment is my original work"
      And i_should_see_the_text "I declare I will comply with this statement"
      And.the_authorship_declaration_button_is_disabled()

      When.i_tick_the_authorship_declaration()
      Then.the_authorship_declaration_button_is_enabled()

      When.i_click_to_confirm_the_authorship_declaration()

      screenshot("Reasonable adjustments declaration")

      Then i_should_see_the_text "I have already agreed reasonable adjustments"
      And.the_ra_declaration_button_is_disabled()

      When.i_choose_the_no_ra_declaration()
      Then.the_ra_declaration_button_is_enabled()

      When.i_choose_the_has_ra_declaration()
      Then.the_ra_declaration_button_is_enabled()

      When.i_click_to_confirm_the_ra_declaration()
      Then i_should_see_the_text "The assessment has begun."
    }

    "be able to download files for an in-progress assessment" in {
      Given.i_am_a_student()
      And i_have_an_assessment_in_progress(student)
      When i_visit_the assessmentPage
      Then i_should_see_assessment_file "Exam paper.pdf"

      When i_download_assessment_file "Exam paper.pdf"
      Then.a_file_should_be_displayed()
    }

    "be able to upload files for an in-progress assessment" in {
      Given.i_am_a_student()
      And i_have_an_assessment_in_progress(student)

      When i_visit_the assessmentPage
      And i_upload "night-heron-500-beautiful.jpg"
      Then i_should_see_uploaded_file "night-heron-500-beautiful.jpg"
    }

    "have to delete a file to upload another with the same name" in {
      Given.i_am_a_student()
      And i_have_an_assessment_in_progress(student)

      When i_visit_the assessmentPage
      And i_upload "night-heron-500-beautiful.jpg"
      Then i_should_see_the_text "The files have been uploaded to the assessment."

      When i_upload "night-heron-500-beautiful.jpg"
      Then i_should_see_the_text "You uploaded at least one file, night-heron-500-beautiful.jpg, which already exists. Please delete it first."

      When i_delete "night-heron-500-beautiful.jpg"
      Then i_should_see_the_text "1 file has been deleted."

      When i_upload "night-heron-500-beautiful.jpg"
      Then i_should_see_the_text "The files have been uploaded to the assessment."
    }

    "be unable to finish an assessment when no files uploaded" in {
      Given.i_am_a_student()
      And i_have_an_assessment_in_progress(student)

      When i_visit_the assessmentPage
      Then the_page_content_should_not_contain "Finish exam"
    }

    "be able to finish an assessment with files uploaded" in {
      Given.i_am_a_student()
      And i_have_an_assessment_in_progress(student)

      When i_visit_the assessmentPage
      And i_upload "night-heron-500-beautiful.jpg"
      Then i_should_see_the_text "The files have been uploaded to the assessment."

      When.i_finish_the_assessment()
      Then i_should_see_the_text "The assessment has been completed."
      And the_page_content_should_not_contain "Finish exam"
    }

    "be able to start a Moodle assignment" in {
      Given.i_am_a_student()
      And i_have_a_moodle_assignment_to_take(student)

      When i_visit_the assessmentPage
      And.i_click_to_start_the_assessment()

      screenshot("Authorship declaration")

      Then i_should_see_the_text "This assessment is my original work"
      And i_should_see_the_text "I declare I will comply with this statement"
      And.the_authorship_declaration_button_is_disabled()

      When.i_tick_the_authorship_declaration()
      Then.the_authorship_declaration_button_is_enabled()

      When.i_click_to_confirm_the_authorship_declaration()

      screenshot("Reasonable adjustments declaration")

      Then i_should_see_the_text "I have already agreed reasonable adjustments"
      And.the_ra_declaration_button_is_disabled()

      When.i_choose_the_no_ra_declaration()
      Then.the_ra_declaration_button_is_enabled()

      When.i_choose_the_has_ra_declaration()
      Then.the_ra_declaration_button_is_enabled()

      When.i_click_to_confirm_the_ra_declaration()
      Then i_should_see_the_text "Started"
      And i_should_see_the_text "View your assessment in Moodle"
    }

    "be unable to see announcements before they start" in {
        Given.i_am_a_student()
        And i_have_an_online_exam_to_sit_with_an_existing_announcement(student, Fixtures.users.staff1)
        When i_visit_the assessmentPage

        screenshot("Assessment student before starting no announcements")
        Then the_page_content_should_not_contain "Test announcement number one"
    }

    "be unable to see invigilators names on existing announcements after they start" in {
      Given.i_am_a_student()
      And i_have_an_assessment_in_progress(student, Some(Fixtures.users.staff1))
      When i_visit_the assessmentPage

      screenshot("Assessment student in progress with announcement")
      Then i_should_see_the_text "Test announcement number one"
      And the_page_content_should_not_contain(Fixtures.users.staff1.name.full.get)
    }

    "be able to see the grace upload period added to the duration" in {
      Given.i_am_a_student()
      And i_have_an_assessment_in_progress (student)
      // ...and that assessment started five minutes ago

      When i_visit_the assessmentPage
      screenshot("Assessment duration")
      Then i_should_see_the_text("You have 3 hours and 39 minutes remaining until you should upload your answers")
      And i_should_see_the_text("This is a 3 hour paper, plus 45 minutes to upload your answers")
      And i_should_see_the_text("You have 45 minutes until")
    }

    "be able to upload files and finish the exam during the grace upload period" in {
      Given.i_am_a_student()
      And i_have_an_assessment_in_progress_that_started_x_minutes_ago(student, assessmentStart = 195.toLong, studentStart = 185.toLong)

      When i_visit_the assessmentPage
      Then i_should_see_the_text("You have 39 minutes remaining until you should upload your answers")

      When i_upload "night-heron-500-beautiful.jpg"
      Then i_should_see_the_text "The files have been uploaded to the assessment."

      When.i_finish_the_assessment()
      Then i_should_see_the_text "The assessment has been completed."
      And the_page_content_should_not_contain "Finish exam"
    }

    "see a late message after the grace upload period" in {
      Given.i_am_a_student()
      // assessment started 5h 56 ago, student started 3h 46 minutes ago
      And i_have_an_assessment_in_progress_that_started_x_minutes_ago(student, assessmentStart = 356.toLong, studentStart = 226.toLong)

      When i_visit_the assessmentPage
      screenshot("Assessment late message")
      Then i_should_see_the_text("You started this assessment, but missed the deadline to upload your answers.")
      And i_should_see_the_text("Exceeded deadline by 1 minute")
    }

    "be unable to upload files after the late period" in {
      Given.i_am_a_student()
      // assessment is 3 hours long, grace period 45 minutes
      // assessment started 5h 56 ago, student started 5h 46 minutes ago
      And i_have_an_assessment_in_progress_that_started_x_minutes_ago(student, assessmentStart = 356.toLong, studentStart = 346.toLong)

      When i_visit_the assessmentPage
      Then the_page_content_should_not_contain "Uploaded answer files"
    }
  }
}
