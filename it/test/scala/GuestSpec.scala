
import controllers.sysadmin
import org.scalatest._
import org.scalatestplus.play._
import play.api.test._
import play.api.test.Helpers._

class GuestSpec extends AppFeatureSpec {

  "A guest user" should {

    "be able to view the home page" in {

      Given("I am signed out")

      When("I visit the home page")
      visit(homePage)

      Then("I should see some welcome text")
      pageMustContain("Lorem ipsum")
    }

    "not be able to view the masquerade form" in {
      Given("I am signed out")

      When("I visit the masquerade form")
      visit(sysadmin.routes.MasqueradeController.masquerade().url)

      Then("I should be sent to web sign-on")
      mustRedirectToWebsignon()
    }

  }

}
