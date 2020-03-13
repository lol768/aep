package helpers

import warwick.core.helpers.ServiceResults.{ServiceResult, ServiceResultException}
import org.scalatest.TestSuite
import org.scalatest.concurrent.ScalaFutures

import scala.concurrent.Future

trait FutureServiceMixins { this: ScalaFutures =>

  implicit class FutureServiceResultOps[A](f: Future[ServiceResult[A]]) {

    // Convenient way to block on a Future[ServiceResult[_]] that you expect
    // to be successful.
    def serviceValue: A =
      f.futureValue.fold(
        e => throw new ServiceResultException(e),
        identity // return success as-is
      )
  }

}
