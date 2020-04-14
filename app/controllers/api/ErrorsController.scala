package controllers.api

import controllers.BaseController
import javax.inject.Singleton
import net.logstash.logback.argument.StructuredArguments
import org.slf4j.{Logger, LoggerFactory}
import play.api.libs.json.{JsValue, Json, OFormat, Reads}

import scala.jdk.CollectionConverters._
import scala.util.Try
import play.api.mvc.RequestHeader
import net.logstash.logback.argument.StructuredArgument
import controllers.RequestContext

@Singleton
class ErrorsController extends BaseController {

  lazy val slf4jLogger: Logger = LoggerFactory.getLogger("JAVASCRIPT_ERROR")

  def js = Action { implicit request =>
    request.body.asJson.flatMap(_.asOpt[JavaScriptError]).foreach { jsError =>
      val (message, map) = process(jsError, request)
      slf4jLogger.info(message, StructuredArguments.entries(map.asJava))
    }
    Ok("")
  }

  def process(jsError: JavaScriptError, request: RequestHeader)(implicit ctx: RequestContext): (String, Map[String, Any]) = {
    val message = jsError.message.getOrElse("-")
    val map = Seq(
      jsError.column.map("column" -> _),
      jsError.line.map("line" -> _),
      jsError.source.map("source" -> _),
      jsError.pageUrl.map("page_url" -> _),
      jsError.stack.map("stack_trace" -> _),
      Option(request.remoteAddress).map("source_ip" -> _),
      request.headers.get("User-Agent").map(ua => "request_headers" -> Map("user-agent" -> ua.toString)),
      ctx.user.map(_.usercode.string).map("username" -> _),
    ).flatten.toMap
    (message, map)
  }
}

case class JavaScriptError(
  column: Option[Int],
  line: Option[Int],
  source: Option[String],
  pageUrl: Option[String],
  stack: Option[String],
  message: Option[String],
)

object JavaScriptError {
  implicit val reads: Reads[JavaScriptError] = Json.reads[JavaScriptError]
}
