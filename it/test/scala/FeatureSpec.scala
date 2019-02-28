import helpers.OneAppPerSuite
import org.scalatest.{GivenWhenThen, WordSpec}
import play.api.mvc.{Result, Results}
import specs.BaseSpecLike
import warwick.sso.User

import scala.concurrent.Future

import play.api.test._
import play.api.test.Helpers._

abstract class FeatureSpec extends WordSpec with BaseSpecLike with GivenWhenThen

abstract class AppFeatureSpec extends FeatureSpec with OneAppPerSuite with Results {
  var user: Option[User] = None
  var res: Future[Result] = _

  val homePage = "/"

  def visit(path: String): Unit = {
    res = req(path, user).get()
  }

  def pageMustContain(text: String): Unit = {
    contentAsString(res) must include(text)
  }

  def mustRedirectToWebsignon(): Unit = {
    status(res) mustBe SEE_OTHER
    headers(res).get("Location").value must startWith ("https://sso.example.com/login")
  }
}
