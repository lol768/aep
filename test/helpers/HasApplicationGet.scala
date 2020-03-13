package helpers

import scala.reflect.ClassTag

trait HasApplicationGet {
  /** Retrieve object of type T from an Application's DI container. */
  def get[T : ClassTag]: T
}
