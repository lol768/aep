package helpers

import uk.ac.warwick.util.web.useragent._
import controllers.RequestContext

object AssetSwitching {
  private val parser = new UserAgentParser

  // should match what's in webpack.config.babel.js
  private val modernTargets = new Targets()
    .chrome(75)
    .edge(44)
    .firefox(70)
    .safari(11, 0)
    .samsung(8, 0)

  def bundleType(ctx: RequestContext): String = {
    val isModern: Boolean = ctx.userAgent.exists { ua =>
      parser.matchTargets(ua, modernTargets)
    }

    if (isModern) "modern" else "es5"
  }
}