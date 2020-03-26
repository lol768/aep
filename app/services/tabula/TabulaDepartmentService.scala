package services.tabula

import com.google.inject.ImplementedBy
import domain.tabula
import javax.inject.{Inject, Singleton}
import play.api.Logger
import play.api.libs.ws.WSClient
import services.tabula.TabulaDepartmentService.DepartmentsReturn
import uk.ac.warwick.sso.client.trusted.TrustedApplicationsManager
import warwick.core.Logging
import warwick.core.helpers.ServiceResults.Implicits._
import warwick.core.helpers.ServiceResults.ServiceResult
import warwick.core.timing.TimingContext

import scala.concurrent.{ExecutionContext, Future}


@ImplementedBy(classOf[TabulaDepartmentServiceImpl])
trait TabulaDepartmentService {
  def getDepartments()(implicit t: TimingContext): Future[DepartmentsReturn]
}

object TabulaDepartmentService {
  type DepartmentsReturn = ServiceResult[Seq[tabula.Department]]
}


@Singleton
class TabulaDepartmentServiceImpl @Inject()(
  ws: WSClient,
  config: TabulaConfiguration,
  trustedApplicationsManager: TrustedApplicationsManager,
  tabulaHttp: TabulaHttp,
)(implicit ec: ExecutionContext) extends TabulaDepartmentService with Logging {

  import tabulaHttp._

  override def getDepartments()(implicit t: TimingContext): Future[DepartmentsReturn] = {
    val url = config.getDepartmentUrl()
    val req = ws.url(url)

    implicit def l: Logger = logger

    doGet(url, req, description = "getDepartments").successFlatMapTo { jsValue =>
      parseAndValidate(jsValue, TabulaResponseParsers.responseDepartmentReads)
    }
  }

}
