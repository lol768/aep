package system

import warwick.core.timing.TimingContext.Category
import warwick.core.timing.{TimingCategories => CoreTimingCategories}

object TimingCategories {
  import CoreTimingCategories._

  object TabulaRead extends Category(id = "TabulaRead", description = Some("Tabula HTTP reads"), inherits = Seq(Tabula))
  object TabulaWrite extends Category(id = "TabulaWrite", description = Some("Tabula HTTP writes"), inherits = Seq(Tabula))
}
