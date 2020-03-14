package helpers

import org.scalatest._
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import warwick.functional.EmbeddedPostgres
import warwick.sso.User

import scala.reflect.ClassTag

trait OneAppPerSuite extends Suite
  with GuiceOneAppPerSuite
  with EmbeddedPostgres
  with BeforeAndAfterAll
  with HasApplicationGet {
  self: TestSuite =>

  def fakeApplicationBuilder(user: Option[User] = None): GuiceApplicationBuilder =
    configureDatabase(TestApplications.fullBuilder(user))

    implicit override def fakeApplication: Application = fakeApplicationBuilder().build()

  def get[T : ClassTag]: T = app.injector.instanceOf[T]

}
