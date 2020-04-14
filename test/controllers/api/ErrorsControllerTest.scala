package controllers.api

import org.scalatestplus.play._
import play.api.mvc.Results
import play.api.test._
import controllers.TestRequestContexts

class ErrorsControllerTest extends PlaySpec with Results {

    val controller = new ErrorsController()

    val req = FakeRequest()

    "ErrorsController.js" should {
        "process an error with all properties" in {
            val error = JavaScriptError(
                message = Option("Error message"),
                line = Option(12),
                column = Option(345),
                source = Option("sourcefile.js"),
                pageUrl = Option("https://example.com/broken-page"),
                stack = Option("TypeError: beans is not defined\nThere are no beans"),
            )

            val ctx = TestRequestContexts.nothing(req, None)
            val (message, args) = controller.process(error, req)(ctx)
            message mustBe "Error message"
            args must contain key "line"
            args must contain key "column"
            args mustBe Map(
                "column" -> 12,
                "line" -> 345,
                "source" -> "sourcefile.js",
                "page_url" -> "https://example.com/broken-page",
                "stack_trace" -> "TypeError: beans is not defined\nThere are no beans",
                "source_ip" -> "127.0.0.1",
            )
        }

        "process an error with scant properties" in {

            val error = JavaScriptError(
                message = Option("Error message"),
                column = None,
                line = None,
                source = None,
                pageUrl = Option("https://example.com/broken-page"),
                stack = Option("Promise rejected"),
            )

            val ctx = TestRequestContexts.nothing(req, None)
            val (message, args) = controller.process(error, req)(ctx)
            message mustBe "Error message"
            args mustBe Map(
                "page_url" -> "https://example.com/broken-page",
                "stack_trace" -> "Promise rejected",
                "source_ip" -> "127.0.0.1",
            )
        }
    }

}
