package system

import play.api.inject.{Binding, bind}
import services.sandbox.DataGeneration

import scala.util.Random

object BindingOverrides {
  val fixedRandomSeed: Long = 787878L

  def fixedDataGeneration: Binding[DataGeneration] =
    bind[DataGeneration].toInstance(new DataGeneration(new Random(fixedRandomSeed)))
}
