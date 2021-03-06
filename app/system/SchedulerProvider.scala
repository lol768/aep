package system

import javax.inject.{Inject, Provider, Singleton}
import javax.sql.DataSource
import org.quartz._
import org.quartz.impl.StdSchedulerFactory
import play.api.db.slick.DatabaseConfigProvider
import play.api.inject.ApplicationLifecycle
import play.api.libs.JNDI
import slick.jdbc.JdbcProfile

import scala.concurrent.{ExecutionContext, Future}

object DataSourceExtractor {
  import scala.language.reflectiveCalls

  // runtime type, will basically use reflection to get at ds.
  // It's probably HikariCPJdbcDataSource but could be others, and there's no common trait.
  type HasDataSource = { def ds: javax.sql.DataSource }

  def extract(db: DatabaseConfigProvider): DataSource =
    db.get[JdbcProfile].db.source.asInstanceOf[HasDataSource].ds
}

@Singleton
class SchedulerProvider @Inject()(
  jobFactory: GuiceJobFactory,
  lifecycle: ApplicationLifecycle,
  db: DatabaseConfigProvider
)(implicit ec: ExecutionContext) extends Provider[Scheduler] {

  private lazy val instance: Scheduler = {
    // quartz.properties specifies this JNDI name
    JNDI.initialContext.rebind("db.default", DataSourceExtractor.extract(db))

    val scheduler = new StdSchedulerFactory().getScheduler
    scheduler.setJobFactory(jobFactory)

    def shutdown: Future[Unit] = Future {
      // Waits for running jobs to finish.
      scheduler.shutdown(true)
    }

    lifecycle.addStopHook(() => shutdown)

    scheduler
  }

  def get(): Scheduler = instance
}
