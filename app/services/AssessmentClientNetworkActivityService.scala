package services

import scala.concurrent.{ExecutionContext, Future}
import akka.Done
import com.google.inject.ImplementedBy
import domain.AssessmentClientNetworkActivity
import domain.dao.{AssessmentClientNetworkActivityDao, DaoRunner}
import javax.inject.{Inject, Singleton}
import warwick.core.helpers.ServiceResults
import warwick.core.helpers.ServiceResults.ServiceResult
import warwick.core.timing.TimingContext

@ImplementedBy(classOf[AssessmentClientNetworkActivityServiceImpl])
trait AssessmentClientNetworkActivityService {
  def record(assessmentClientNetworkActivity: AssessmentClientNetworkActivity)(implicit t: TimingContext): Future[ServiceResult[Done]]
}

@Singleton
class AssessmentClientNetworkActivityServiceImpl @Inject()(
  dao: AssessmentClientNetworkActivityDao,
  daoRunner: DaoRunner
)(implicit ec: ExecutionContext) extends AssessmentClientNetworkActivityService {

  override def record(assessmentClientNetworkActivity: AssessmentClientNetworkActivity)(implicit t: TimingContext): Future[ServiceResult[Done]] = {
    daoRunner.run(dao.insert(assessmentClientNetworkActivity)).map(_ => ServiceResults.success(Done))
  }
}
