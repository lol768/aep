package services.tabula

import javax.inject.{Inject, Singleton}
import play.api.Configuration

@Singleton
class TabulaConfiguration @Inject() (c: Configuration) {
  lazy val usercode = c.get[String]("tabula.usercode")
  lazy val rootUrl = c.get[String]("tabula.root.url")

  def getAssessmentsUrl(deptCode: String): String =
    s"$rootUrl/api/v1/department/$deptCode/upstreamassessments"
}
