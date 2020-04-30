package services.tabula

import org.scalatestplus.play.PlaySpec
import services.tabula.TabulaStudentInformationService.GetMultipleStudentInformationOptions
import warwick.sso.UniversityID

class TabulaStudentInformationServiceTest extends PlaySpec {
  "GetMultipleStudentInformationOptions" should {
    "generate a valid memcached key" in {
      val r = new scala.util.Random()
      val ids = (0 until 10000).map(_ => UniversityID(f"${r.nextInt(9999999)}%07d"))
      val options = GetMultipleStudentInformationOptions(ids)

      val cacheKey = options.cacheKey
      net.spy.memcached.util.StringUtils.validateKey(cacheKey, false)
    }
  }
}
