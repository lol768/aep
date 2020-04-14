package controllers.api

import controllers.BaseController
import javax.inject.Singleton
import net.logstash.logback.argument.StructuredArguments
import org.slf4j.{Logger, LoggerFactory}
import play.api.libs.json.JsValue

import scala.jdk.CollectionConverters._
import scala.util.Try
import play.api.mvc.RequestHeader
import net.logstash.logback.argument.StructuredArgument
import controllers.RequestContext

@Singleton
class ErrorsController extends BaseController {

  lazy val slf4jLogger: Logger = LoggerFactory.getLogger("JAVASCRIPT_ERROR")

  def js = Action { implicit request =>
    request.body.asJson.flatMap(_.asOpt[Seq[Map[String, JsValue]]]).toSeq.flatten.foreach { error =>
      val (message, map) = process(error, request)
      slf4jLogger.info(message, StructuredArguments.entries(map.asJava))
    }
    Ok("")
  }

  def process(error: Map[String, JsValue], request: RequestHeader)(implicit ctx: RequestContext) : (String, Map[String, Any]) = {
    val message = error.get("message").flatMap(_.asOpt[String]).getOrElse("-")
    val map = Seq(
      error.get("column").flatMap(_.asOpt[String]).flatMap(s => Try(s.toInt).toOption).map("column" -> _),
      error.get("line").flatMap(_.asOpt[String]).flatMap(s => Try(s.toInt).toOption).map("line" -> _),
      error.get("source").flatMap(_.asOpt[String]).map("source" -> _),
      error.get("pageUrl").flatMap(_.asOpt[String]).map("page_url" -> _),
      error.get("stack").flatMap(_.asOpt[String]).map("stack_trace" -> _),
      Option(request.remoteAddress).map("source_ip" -> _),
      request.headers.get("User-Agent").map(ua => "request_headers" -> Map("user-agent" -> ua.toString)),
      ctx.user.map(_.usercode.string).map("username" -> _),
    ).flatten.toMap
    (message, map)
  }
}

