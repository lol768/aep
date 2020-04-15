package controllers

import java.time.OffsetDateTime

import play.api.data.Forms.{checked, mapping}
import play.api.data.{Form, Forms, Mapping}
import warwick.core.helpers.JavaTime

object FormMappings {

  val Html5LocalDateTimePattern = "yyyy-MM-dd'T'HH:mm"
  val offsetDateTime: Mapping[OffsetDateTime] =
    Forms.localDateTime(Html5LocalDateTimePattern).transform[OffsetDateTime](_.atZone(JavaTime.timeZone).toOffsetDateTime, _.toLocalDateTime)

  val confirmForm: Form[Boolean] = Form(mapping(
    "confirm" -> checked("You must check this box")
  )(b => b)(b => Some(b)))
}