
import domain.Fixtures
import support.BrowserFeatureSpec

class HomePageSpec extends BrowserFeatureSpec {

  "A student" should {
    import Fixtures.users.{student1 => student}

    "be able to view the home page" in {

      Given("I am signed in")
      setUser(student)

      When("I visit the home page")
      visit(homePage)

      Then("I should have been redirected to the assessments page")
      pageContentMustContain("This page lists all the exams you are scheduled to take during summer 2020.")

      screenshot("Home page for student")

      And(s"I should see my name: ${student.name.full.value}")
      pageMustContain(student.name.full.value)
    }

  }

}
