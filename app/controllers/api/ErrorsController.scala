package controllers.api

import controllers.BaseController
import javax.inject.Singleton
import net.logstash.logback.argument.StructuredArguments
import org.slf4j.{Logger, LoggerFactory}
import play.api.libs.json.JsValue

import scala.jdk.CollectionConverters._

@Singleton
class ErrorsController extends BaseController {

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
}

