package helpers

import domain.dao.DaoRunning
import org.scalactic.source
import org.scalatest.OptionValues
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import play.api.Application
import services.NoAuditLogging
import warwick.core.helpers.ServiceResults.ServiceResult

import scala.concurrent.Future
import scala.language.implicitConversions
import scala.reflect.ClassTag

class ScenarioContext(val app: Application)

/** Lazy getters for any services etc. that a test might need */
trait DependencyGetters { this: HasApplicationGet =>

}

/** A particular setup of data, that can also be cleaned up afterwards. All vals here should be lazy. */
abstract class Scenario(ctx: ScenarioContext)
  extends HasApplicationGet
    with NoAuditLogging
    with DaoRunning
    with DependencyGetters  {

  // We'd like some mixins to be available inside Scenarios without them leaking out of a wildcard import,
  // so we're redefining them as protected.
  protected val secretMixins = new ScalaFutures with IntegrationPatience with FutureServiceMixins with OptionValues
  protected implicit val patienceConfig: secretMixins.PatienceConfig = secretMixins.patienceConfig
  protected implicit def convertScalaFuture[A]: Future[A] => secretMixins.FutureConcept[A] = secretMixins.convertScalaFuture
  protected implicit def futureService[A](f: Future[ServiceResult[A]]): secretMixins.FutureServiceResultOps[A] = new secretMixins.FutureServiceResultOps(f)
  protected implicit def convertOptionToValuable[T](opt: Option[T])(implicit pos: source.Position): secretMixins.Valuable[T] = secretMixins.convertOptionToValuable(opt)

  def cleanup(): Unit = {}

  override def get[T : ClassTag]: T = ctx.app.injector.instanceOf[T]
}

trait RunsScenarios {
  val app: Application

  /** Passed in to Scenarios on creation so that they have access to the running Application. */
  val scenarioCtx = new ScenarioContext(app)

  /** Does some stuff with a Scenario then calls its cleanup function. */
  def scenario[S <: Scenario](s: S)(fn: S => Unit): Unit =
    try fn(s)
    finally s.cleanup()
}
