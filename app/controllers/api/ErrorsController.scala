package controllers.api

import javax.inject.Singleton
import controllers.MyController
import net.logstash.logback.argument.StructuredArguments
import org.slf4j.{Logger, LoggerFactory}
import play.api.libs.json.JsValue
import play.api.mvc.{Action, AnyContent}

import scala.collection.JavaConverters._

@Singleton
class ErrorsController extends MyController {

  lazy val slf4jLogger: Logger = LoggerFactory.getLogger("JAVASCRIPT_ERROR")

  def js = Action { implicit request =>
    request.body.asJson.flatMap(_.validate[Seq[Map[String, JsValue]]].asOpt).toSeq.flatten.foreach { error =>
      val message = error.get("message").flatMap(_.asOpt[String]).getOrElse("-")
      val entries = StructuredArguments.entries(Map(
        "stack_trace" -> error.get("stack").flatMap(_.asOpt[String]).getOrElse("-"),
        "source_ip" -> request.remoteAddress
      ).asJava)
      slf4jLogger.info(message, entries)
    }
    Ok("")
  }

  def tls: Action[AnyContent] = Action { implicit request =>
    NoContent // look at the access log.
  }
}

