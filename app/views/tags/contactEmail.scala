package views.tags

import controllers.RequestContext
import play.twirl.api.Html

object contactEmail {
  def apply()(implicit context: RequestContext): Html = Html(
    s"<a href='mailto:${context.appContactEmail}'>${context.appContactEmail}</a>"
  )
}
