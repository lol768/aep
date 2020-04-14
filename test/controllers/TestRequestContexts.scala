package controllers

import play.api.mvc.Flash
import play.api.mvc.RequestHeader
import warwick.sso.User
import warwick.core.timing.TimingContext

object TestRequestContexts {
    def nothing(request: RequestHeader, user: Option[User]) = RequestContext(
      path = "/",
      user = user,
      actualUser = user,
      loginUrl = null,
      logoutUrl = null,
      myWarwickBaseUrl = null,
      navigation = null,
      flash = Flash(),
      csrfHelper = null,
      userAgent = request.headers.get("User-Agent"),
      ipAddress = request.remoteAddress,
      timingData = new TimingContext.Data,
      appFullName = null,
      appContactEmail = null,
      tabulaConfiguration = null,
    )
}