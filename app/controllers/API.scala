package controllers

import play.api.data.Form
import play.api.i18n.Messages
import play.api.libs.functional.syntax._
import play.api.libs.json.Json._
import play.api.libs.json.Reads._
import play.api.libs.json.Writes._
import play.api.libs.json._
import play.api.mvc.{Result, Results}

/**
  * Builder for API response objects.
  */
object API {

  def badRequestJson(formWithErrors: Form[_])(implicit messages: Messages): Result =
    Results.BadRequest(Json.toJson(API.Failure[JsObject]("bad_request",
      formWithErrors.errors.map(error => API.Error(error.getClass.getSimpleName, error.format, error.message))
    )))

  sealed abstract class Response[A: Reads : Writes](val success: Boolean, status: String) {
    // Maybe this is useful, if you like using Either
    def either: Either[Failure[A], Success[A]]
  }

  case class Error(id: String, message: String, messageKey: String = null)

  case class Success[A: Reads : Writes](status: String = "ok", data: A) extends Response[A](true, status) {
    def either = Right(this)
  }
  object Success {
    implicit def writes[A : Reads : Writes]: Writes[Success[A]] = (s: Success[A]) => Json.obj(
      "success" -> true,
      "status" -> s.status,
      "data" -> s.data,
    )
  }

  case class Failure[A: Reads : Writes](status: String, errors: Seq[Error]) extends Response[A](false, status) {
    def either = Left(this)
  }
  object Failure {
    implicit def writes[A : Reads : Writes]: Writes[Failure[A]] = (f: Failure[A]) => Json.obj(
      "success" -> false,
      "status" -> f.status,
      "errors" -> f.errors,
    )
  }

  object Error {
    implicit val format: OFormat[Error] = Json.format[Error]

    def fromJsError(jsError: JsError): Seq[Error] = JsError.toFlatForm(jsError).toSeq.map {
      case (field, errors) =>
        val propertyName = field.substring(4) // Remove 'obj.' from start of field name
        Error(
          s"invalid-$propertyName",
          errors.flatMap(_.messages).mkString(", ")
        )
    }

  }

  object Response {
    implicit def reads[A: Reads : Writes]: Reads[Response[A]] = (json: JsValue) => {
      val status = (json \ "status").validate[String]
      (json \ "success").validate[Boolean].flatMap { success =>
        if (success) {
          val data = (json \ "data").validate[A]
          (status and data) (Success.apply[A] _)
        } else {
          val errors = (json \ "errors").validate[Seq[Error]](Reads.seq[Error])
          (status and errors) (Failure.apply[A] _)
        }
      }
    }

    implicit def writes[A: Reads : Writes]: Writes[Response[A]] = {
      case s: Success[A] => Success.writes[A].writes(s)
      case f: Failure[A] => Failure.writes[A].writes(f)
    }
  }

}
