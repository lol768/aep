package views.html.tags

import play.twirl.api._

object printDataAttributes {
  def apply(attributes: Map[String, String]): Html = {
    val bits = attributes.map {
      case (key, value) => html""" data-$key="$value""""
    }.toSeq
    HtmlFormat.fill(bits)
  }
}
