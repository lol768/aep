import domain.Fixtures
import support.BrowserFeatureSpec

import scala.language.postfixOps
import scala.languageFeature.postfixOps

class AssessmentSpec extends BrowserFeatureSpec {

  import Fixtures.users.{student1 => student}

  "A student" should {
    "be able to start an assessment" in {
      Given.i_am_a_student()
      And i_have_an_online_exam_to_sit(student)
      When i_visit_the assessmentPage

      screenshot("Assessment student")

      And.i_start_the_assessment()

      screenshot("Assessment started")

      Then i_should_see_the_text "The assessment has begun."
      And i_should_see_the_text "Started a moment ago"
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
      Then the_page_content_should_not_contain "Finish exam"
    }
  }
}
