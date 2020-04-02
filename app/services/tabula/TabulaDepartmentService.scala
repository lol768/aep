package services.tabula

import com.google.inject.ImplementedBy
import com.google.inject.name.Named
import domain.tabula
import javax.inject.{Inject, Singleton}
import play.api.Logger
import play.api.cache.AsyncCacheApi
import play.api.libs.ws.WSClient
import services.tabula.TabulaDepartmentService.DepartmentsReturn
import scala.concurrent.duration._
import system.TimingCategories
import uk.ac.warwick.sso.client.trusted.TrustedApplicationsManager
import warwick.caching.{Ttl, VariableTtlCacheHelper}
import warwick.core.Logging
import warwick.core.helpers.ServiceResults.Implicits._
import warwick.core.helpers.ServiceResults.ServiceResult
import warwick.core.timing.{TimingContext, TimingService}
import scala.concurrent.{ExecutionContext, Future}

@ImplementedBy(classOf[CachingTabulaDepartmentService])
trait TabulaDepartmentService {
  def getDepartments()(implicit t: TimingContext): Future[DepartmentsReturn]
}

object TabulaDepartmentService {
  type DepartmentsReturn = ServiceResult[Seq[tabula.Department]]
}

class CachingTabulaDepartmentService @Inject()(
  @Named("TabulaDepartmentService-NoCache") impl: TabulaDepartmentService,
  cache: AsyncCacheApi,
  timing: TimingService,
)(implicit ec: ExecutionContext) extends TabulaDepartmentService with Logging {

  private lazy val ttlStrategy: DepartmentsReturn => Ttl = a => a.fold(
    _ => Ttl(soft = 10.seconds, medium = 1.minute, hard = 1.hour),
    _ => Ttl(soft = 1.hour, medium = 1.day, hard = 7.days)
  )

  private lazy val wrappedCache = VariableTtlCacheHelper.async[DepartmentsReturn](cache, logger, ttlStrategy, timing)

  override def getDepartments()(implicit t: TimingContext): Future[DepartmentsReturn] = timing.time(TimingCategories.TabulaRead) {
    wrappedCache.getOrElseUpdate(s"TabulaDepartmentService:getDepartments") {
      impl.getDepartments()
    }
  }
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
