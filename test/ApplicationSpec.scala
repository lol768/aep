
import play.api.http.{HeaderNames, MimeTypes}
import play.api.test.Helpers._
import play.api.test._
import specs.BaseSpec

/**
 * Add your spec here.
 * You can mock out a whole application including requests, plugins etc.
 * For more information, consult the wiki.
 */
class ApplicationSpec extends BaseSpec {

  "Application" should {
    "redirect on a bad request if the user isn't authenticated" in {
      status(req("/service/boom").get()) mustEqual SEE_OTHER
    }

    "respond to GTG" in {
      val res = req("/service/gtg").get()
      status(res) mustBe OK
      contentType(res) mustBe Some(MimeTypes.TEXT)
      contentAsString(res) mustBe """"OK""""
    }

    "render the index page" in {
      val home = route(app, FakeRequest(GET, "/").withHeaders(HeaderNames.ACCEPT -> "text/html")).get

      status(home) mustEqual OK
      contentType(home).get mustEqual "text/html"
      contentAsString(home) must include("Lorem ipsum")
    }
  }
}
