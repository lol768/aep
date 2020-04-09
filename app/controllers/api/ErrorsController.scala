package controllers.api

import controllers.BaseController
import javax.inject.Singleton
import net.logstash.logback.argument.StructuredArguments
import org.slf4j.{Logger, LoggerFactory}
import play.api.libs.json.JsValue

import scala.jdk.CollectionConverters._
import scala.util.Try

@Singleton
class ErrorsController extends BaseController {

  lazy val slf4jLogger: Logger = LoggerFactory.getLogger("JAVASCRIPT_ERROR")

  def js = Action { implicit request =>
    request.body.asJson.flatMap(_.validate[Seq[Map[String, JsValue]]].asOpt).toSeq.flatten.foreach { error =>
      val message = error.get("message").flatMap(_.asOpt[String]).getOrElse("-")
      val entries = StructuredArguments.entries {
        Seq(
          error.get("column").flatMap(_.asOpt[String]).flatMap(s => Try(s.toInt).toOption).map("column" -> _),
          error.get("line").flatMap(_.asOpt[String]).flatMap(s => Try(s.toInt).toOption).map("line" -> _),
          error.get("source").flatMap(_.asOpt[String]).map("source" -> _),
          error.get("pageUrl").flatMap(_.asOpt[String]).map("page_url" -> _),
          error.get("stack").flatMap(_.asOpt[String]).map("stack_trace" -> _),
          Option(request.remoteAddress).map("source_ip" -> _),
          request.headers.get("User-Agent").map(ua => "request_headers" -> Map("user-agent" -> ua.toString)),
          request.user.map(_.usercode.string).map("username" -> _),
        ).flatten.toMap.asJava
      }
      slf4jLogger.info(message, entries)
    }
    Ok("")
  }
}

