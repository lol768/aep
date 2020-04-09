package controllers.api

import org.scalatestplus.play._
import play.api.mvc.Results
import play.api.libs.json._
import play.api.test._
import controllers.TestRequestContexts

class ErrorsControllerTest extends PlaySpec with Results {

    val controller = new ErrorsController()

    val req = FakeRequest()

    "ErrorsController.js" should {
        "process an error with all properties" in {
            val error: Map[String, JsValue] = Map(
                "message" -> JsString("Error message"),
                "column" -> JsNumber(12),
                "line" -> JsNumber(345),
                "source" -> JsString("sourcefile.js"),
                "pageUrl" -> JsString("https://example.com/broken-page"),
                "stack" -> JsString("TypeError: beans is not defined\nThere are no beans"),
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
            val error: Map[String, JsValue] = Map(
                "message" -> JsString("Error message"),
                "column" -> JsNull,
                "line" -> JsNull,
                "source" -> JsNull,
                "pageUrl" -> JsString("https://example.com/broken-page"),
                "stack" -> JsString("Promise rejected"),
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