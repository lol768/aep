package services.tabula

import javax.inject.{Inject, Singleton}
import play.api.Configuration
import system.routes.Types.UniversityID
import uk.ac.warwick.util.termdates.AcademicYear

@Singleton
class TabulaConfiguration @Inject() (c: Configuration) {
  lazy val usercode: String = c.get[String]("tabula.usercode")
  lazy val rootUrl: String = c.get[String]("tabula.root.url")

  def getAssessmentsUrl(deptCode: String): String =
    s"$rootUrl/api/v1/department/$deptCode/upstreamassessments"

  def getAssessmentComponentMembersUrl(deptCode: String, academicYear: AcademicYear): String =
    s"$rootUrl/api/v1/department/$deptCode/${academicYear.getStartYear}/assessmentComponentMembers"

  def getDepartmentsUrl: String =
    s"$rootUrl/api/v1/department"

  def getStudentInformationUrl(universityID: UniversityID): String =
    s"$rootUrl/api/v1/member/${universityID.string}"

  def getProfileUrl(universityID: UniversityID): String =
    s"$rootUrl/profiles/view/${universityID.string}"

}
