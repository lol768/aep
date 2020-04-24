package specs

import java.io.{ByteArrayOutputStream, File}

import akka.stream.Materializer
import akka.stream.testkit.NoMaterializer
import akka.util.ByteString
import domain.dao.DaoTestTrait
import helpers.FakeRequestMethods._
import helpers.{FutureServiceMixins, OneAppPerSuite, RunsScenarios, ThreadPoolExecutionContextMixin}
import org.apache.http.entity.ContentType
import org.apache.http.entity.mime.MultipartEntityBuilder
import org.apache.http.entity.mime.content.{FileBody, StringBody}
import org.jsoup.Jsoup
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{MustMatchers, OptionValues, TestSuite}
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.play.PlaySpec
import play.api.http.Writeable
import play.api.mvc.MultipartFormData.FilePart
import play.api.mvc.{Codec, MultipartFormData, Request, Result}
import play.api.test.FakeRequest
import play.api.test.Helpers._
import play.filters.csrf.CSRF
import services.NoAuditLogging
import warwick.html.{HtmlNavigation, ID7PageNavigation}
import warwick.sso.User

import scala.concurrent.Future
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
    with FutureServiceMixins
    with ThreadPoolExecutionContextMixin { self: TestSuite =>

  // This might be a bad idea. Experimenting with ways to make all the specs
  // be readable and not too repetitive.
  case class req(
    path: String,
    user: Option[User] = None,
    followRedirects: Boolean = false,
    accept: String = "text/html",
    file: Option[File] = None,
  ) {
    def forUser(u: User): req = copy(user = Option(u))
    def withFile(f: File): req = copy(file = Some(f))

    private val MULTI_PART_FORM_BOUNDARY = "----YouMessWithTheHonkYouGetTheBonk"

    // This is a fiddled version of some code I copied from Stack Overflow here: https://stackoverflow.com/questions/15133794/writing-a-test-case-for-file-uploads-in-play-2-1-and-scala/24622059#24622059
    implicit def multipartFormWriteable(implicit codec: Codec): Writeable[MultipartFormData[File]] = {
      val builder = MultipartEntityBuilder.create().setBoundary(MULTI_PART_FORM_BOUNDARY)

      def transform(multipart: MultipartFormData[File]): ByteString = {
        multipart.dataParts.foreach { part =>
          part._2.foreach { p2 =>
            builder.addPart(part._1, new StringBody(p2, ContentType.create("text/plain", "UTF-8")))
          }
        }
        multipart.files.foreach { file =>
          val part = new FileBody(file.ref, ContentType.create(file.contentType.getOrElse("application/octet-stream")), file.filename)
          builder.addPart(file.key, part)
        }

        val outputStream = new ByteArrayOutputStream
        builder.build.writeTo(outputStream)
        ByteString(outputStream.toByteArray)
      }

      new Writeable[MultipartFormData[File]](transform, Some(builder.build.getContentType.getValue))
    }

    private def standardHeaders[A](r: FakeRequest[A]): FakeRequest[A] =
      r.withHeaders("Accept" -> accept) // otherwise MIME-aware responses will return JSON

    private def multiPartFormHeaders[A](r: FakeRequest[A]): FakeRequest[A] =
      r.withHeaders(
        "Accept" -> accept,
        "Content-Type" -> s"multipart/form-data; boundary=$MULTI_PART_FORM_BOUNDARY"
      )

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

    def postMultiPartForm(data: Seq[(String, String)]): Future[Result] = {
      val filePart = file.map { f =>
        Seq(FilePart[File](
          key = "file",
          filename = f.getName,
          contentType = Some("application/csv"),
          ref = f
        ))
      }.getOrElse(Seq.empty)

      val request = multiPartFormHeaders(FakeRequest(POST, path).withBody(
        MultipartFormData[File](play.utils.OrderPreserving.groupBy(data)(_._1), filePart, badParts = Nil)
      )).addAttr(
        CSRF.Token.InfoAttr, CSRF.TokenInfo(CSRF.Token("jim", "123"))
      )

      exec(request)
    }


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
  def htmlErrors(res: Future[Result])(implicit mat: Materializer = NoMaterializer): Seq[String] = {
    val string = contentAsString(res)
    val jdoc: org.jsoup.nodes.Document = Jsoup.parse(string)
    val serviceResultErrors = jdoc.select("ul.service-results li").eachText().asScala
    val validationErrors = jdoc.select(".has-error").eachText().asScala
    val flashErrors = jdoc.select(".flash-error").eachText().asScala

    (serviceResultErrors ++ validationErrors ++ flashErrors).toSeq
  }

}
