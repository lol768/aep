package helpers

import uk.ac.warwick.util.web.UriBuilder

object URLBuilderHelper {
  private def build(currentRoute: String, formParams: Seq[Map[String, String]]): UriBuilder = {
    val uriBuilder = UriBuilder.parse(currentRoute)

    formParams.filter(_.nonEmpty).flatten.foreach { case (name: String, value: String) =>
      uriBuilder.addQueryParameter(name, value)
    }
    
    uriBuilder
  }

  def addParams(currentRoute: String, formParams: Seq[Map[String, String]], newParams: Map[String, String]): String = {
    val uriBuilder = build(currentRoute, formParams)
    
    newParams.foreach { case (name:String, value: String) =>
      uriBuilder.addQueryParameter(name, value)
    }
    
    uriBuilder.toString
  }

  def replaceExistingParams(currentRoute: String, formParams: Seq[Map[String, String]], newParams: Map[String, String]): String = {
    val uriBuilder = build(currentRoute, formParams)

    newParams.foreach { case (name:String, value: String) =>
      uriBuilder.removeQueryParameter(name)
      uriBuilder.addQueryParameter(name, value)
    }

    uriBuilder.toString
  }
}
