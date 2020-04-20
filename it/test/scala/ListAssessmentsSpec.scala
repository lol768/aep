import domain.Fixtures
import support.BrowserFeatureSpec

import scala.language.postfixOps
import scala.languageFeature.postfixOps

class ListAssessmentsSpec extends BrowserFeatureSpec {

  import Fixtures.users.{student1 => student}

  "A student" should {

    "be able to view a list of their assessments" in {

      Given.i_am_a_student()
      And i_have_an_online_exam_to_sit(student)
      When i_visit_the assessmentsListPage
      Then the_page_should_contain "This page lists all the assessments you are scheduled to take during April and early May 2020"
      And the_page_should_contain student.name.full.value

      screenshot("Assessments list student")

      When.i_click_through_to_my_first_exam_from_assessments_list()
      Then.i_should_be_redirected_to_exam_page()

    }
  }
}
