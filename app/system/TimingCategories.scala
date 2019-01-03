package system

import warwick.core.timing.TimingContext.Category

object TimingCategories {
  object Http extends Category(id = "HTTP", description = Some("External HTTP calls"))
  object Db extends Category(id = "DB", description = Some("Database queries"))
  object Cache extends Category(id = "Cache", description = Some("Cache operations"))
  object CacheRead extends Category(id = "CacheRead", description = Some("Cache reads"), inherits = Seq(Cache))
  object CacheWrite extends Category(id = "CacheWrite", description = Some("Cache writes"), inherits = Seq(Cache))
}
