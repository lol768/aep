package helpers

import warwick.core.helpers.ServiceResults.{ServiceResult, ServiceResultException}
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.exceptions.TestFailedException

import scala.concurrent.Future

trait FutureServiceMixins { this: ScalaFutures =>

  implicit class FutureServiceResultOps[A](f: Future[ServiceResult[A]]) {

    // Convenient way to block on a Future[ServiceResult[_]] that you expect
    // to be successful.
    def serviceValue: A = try {
      f.futureValue.fold(
        e => throw new ServiceResultException(e),
        identity // return success as-is
      )
    } catch {
      // so that we can intercept {} on an actual exception, rather than always getting TFE
      case e: TestFailedException => throw e.getCause
    }
  }

}
