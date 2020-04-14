package helpers

import warwick.core.helpers.ServiceResults
import warwick.core.helpers.ServiceResults.ServiceResult

import scala.concurrent.{ExecutionContext, Future}

object ServiceResultUtils {
  def traverseSerial[A, B](in: Seq[A])(fn: A => Future[ServiceResult[B]])(implicit  executionContext: ExecutionContext): Future[ServiceResult[Seq[B]]] =
    in.foldLeft(Future.successful(Seq.empty[ServiceResult[B]])) { (future, item) =>
      future.flatMap(seq =>
        fn(item).map { result =>
          seq :+ result
        }
      )
    }.map(ServiceResults.sequence)
}
