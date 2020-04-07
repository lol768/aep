package system

import play.api.inject.{Binding, bind}
import services.sandbox.DataGeneration

import scala.util.Random

object BindingOverrides {
  def fixedDataGeneration: Binding[DataGeneration] =
    bind[DataGeneration].toInstance(new DataGeneration(new Random(787878)))
}
