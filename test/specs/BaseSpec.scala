package specs

import java.util.concurrent.Executors

import domain.dao.DaoTestTrait
import helpers.FakeRequestMethods._
import helpers.{FutureServiceMixins, OneAppPerSuite, RunsScenarios}
import org.jsoup.Jsoup
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatest.{MustMatchers, OptionValues, TestSuite}
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.play.PlaySpec
import play.api.http.Writeable
import play.api.mvc.{Request, Result}
import play.api.test.FakeRequest
import play.api.test.Helpers._
import play.filters.csrf.CSRF
import services.NoAuditLogging
import warwick.html.{HtmlNavigation, ID7PageNavigation}
import warwick.sso.User

import scala.concurrent.{ExecutionContext, Future}
import scala.jdk.CollectionConverters._

abstract class BaseSpec extends PlaySpec with BaseSpecLike with RunsScenarios

/**
  * A spec designed for testing most of the app stack, calling
  * controllers as a browser would (though without using HTTP).
  */
trait BaseSpecLike
  extends ScalaFutures
    with DaoTestTrait
    with OptionValues
    with MustMatchers
    with MockitoSugar
    with OneAppPerSuite
    with NoAuditLogging
    with HtmlNavigation
    with ID7PageNavigation
    with FutureServiceMixins { self: TestSuite =>

  implicit val ec: ExecutionContext = ExecutionContext.fromExecutorService(Executors.newCachedThreadPool())

  // This might be a bad idea. Experimenting with ways to make all the specs
  // be readable and not too repetitive.
  case class req(
    path: String,
    user: Option[User] = None,
    followRedirects: Boolean = false,
    accept: String = "text/html"
  ) {
    def forUser(u: User) = copy(user = Option(u))

    private def standardHeaders[A](r: FakeRequest[A]): FakeRequest[A] =
      r.withHeaders("Accept" -> accept) // otherwise MIME-aware responses will return JSON

    def get(): Future[Result] =
      exec(standardHeaders(FakeRequest(GET, path)))

    def post(data: Seq[(String, String)]): Future[Result] =
      exec(standardHeaders(FakeRequest(POST, path))
        .withFormUrlEncodedBody(data: _*)
          .addAttr(
            // Just to shut up the CSRF-aware form tags
            CSRF.Token.InfoAttr, CSRF.TokenInfo(CSRF.Token("jim","123"))
          )
      )


    private def exec[A : Writeable](r: Request[A]): Future[Result] = {
      val req = user.map(r.withUser(_)).getOrElse(r)
      route(app, req).map(handleRedirects).getOrElse {
        fail(s"No match found for $path")
      }
    }

    private def handleRedirects(res: Future[Result]): Future[Result] =
      if (followRedirects) {
        res.flatMap { result =>
          result.header.headers.get("Location").map { loc =>
            req(loc, user).get()
          }.getOrElse {
            res
          }
        }
      } else {
        res
      }
  }

  /** Scrape any validation or service errors that have been output to the page. Helps to
   * make the reason for test failures more obvious when it tells you what it's actually sad about. */
  def htmlErrors(res: Future[Result]): Seq[String] = {
    val string = contentAsString(res)
    val jdoc: org.jsoup.nodes.Document = Jsoup.parse(string)
    val serviceResultErrors = jdoc.select("ul.service-results li").eachText().asScala
    val validationErrors = jdoc.select(".has-error").eachText().asScala
    val flashErrors = jdoc.select(".flash-error").eachText().asScala

    (serviceResultErrors ++ validationErrors ++ flashErrors).toSeq
  }

}
