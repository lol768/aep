package views.html.tags

import controllers.RequestContext
import play.twirl.api.Html
import play.twirl.api.StringInterpolation

object contactEmail {
  def apply()(implicit context: RequestContext): Html =
    html"<a href='mailto:${context.appContactEmail}'>${context.appContactEmail}</a>"

}
