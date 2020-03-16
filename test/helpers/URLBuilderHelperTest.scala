package helpers

import org.scalatestplus.play.PlaySpec

class URLBuilderHelperTest extends PlaySpec {

  "URLBuilderHelper" should {
    
    "just output the current route and existing params again if the requested additional query string is blank" in {
      val newParams = Map[String, String]()
      val currentRoute = "/admin/nomination"
      val currentParams = Seq(Map("page" -> "2", "format" -> "csv"))
      
      URLBuilderHelper.replaceExistingParams(currentRoute, currentParams, newParams) mustBe ("/admin/nomination?page=2&format=csv")
    }

    "add a single new query string param on the end of a route/query string" in {
      val newParams = Map("sorting" -> "upsideDown")
      val currentRoute = "/admin/nomination"
      val currentParams = Seq(Map("page" -> "2", "format" -> "csv"))

      URLBuilderHelper.replaceExistingParams(currentRoute, currentParams, newParams) mustBe ("/admin/nomination?page=2&format=csv&sorting=upsideDown")
    }

    "add multiple new query string params on the end of a route/query string" in {
      val newParams = Map("sorting" -> "upsideDown", "language" -> "french")
      val currentRoute = "/admin/nomination"
      val currentParams = Seq(Map("page" -> "2", "format" -> "csv"))

      URLBuilderHelper.replaceExistingParams(currentRoute, currentParams, newParams) mustBe ("/admin/nomination?page=2&format=csv&sorting=upsideDown&language=french")
    }
    
    "overwrite existing query string params, but leave the original params alone" in {
      val newParams = Map("page" -> "3", "language" -> "german")
      val currentRoute = "/admin/nomination"
      val currentParams = Seq(Map("page" -> "2", "format" -> "csv"))

      URLBuilderHelper.replaceExistingParams(currentRoute, currentParams, newParams) mustBe ("/admin/nomination?format=csv&page=3&language=german")
    }

    "just output the route with no query string params at all" in {
      val newParams = Map[String, String]()
      val currentRoute = "/admin/nomination"
      val currentParams = Seq()

      URLBuilderHelper.replaceExistingParams(currentRoute, currentParams, newParams) mustBe ("/admin/nomination")
    }
    
    "handle the current route having a query string already" in {
      val newParams = Map("weapon" -> "gun")
      val currentRoute = "/admin/nomination?artist=spector"
      val currentParams = Seq(Map("page" -> "2", "format" -> "csv"))

      URLBuilderHelper.replaceExistingParams(currentRoute, currentParams, newParams) mustBe ("/admin/nomination?artist=spector&page=2&format=csv&weapon=gun")
    }
  }
  
 
}
