package helpers

import java.util.concurrent.Executors

import scala.concurrent.ExecutionContext

trait ThreadPoolExecutionContextMixin {
  implicit val ec: ExecutionContext = ExecutionContext.fromExecutorService(Executors.newCachedThreadPool())
}
