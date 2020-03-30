
import support.BrowserFeatureSpec

class GuestSpec extends BrowserFeatureSpec {

  "A guest user" should {

    "not be able to view the home page" in {
      Given("I am signed out")

      When("I visit the home page")
      visit(homePage)

      Then("I should be sent to web sign-on")
      mustRedirectToWebsignon()
    }

    "not be able to view the masquerade form" in {
      Given("I am signed out")

      When("I visit the masquerade form")
      visit(controllers.sysadmin.routes.MasqueradeController.masquerade().url)

      Then("I should be sent to web sign-on")
      mustRedirectToWebsignon()
    }

  }

}
