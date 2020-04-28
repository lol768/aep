package controllers

import play.api.mvc.Flash
import play.api.mvc.RequestHeader
import system.Features
import warwick.sso.User
import warwick.core.timing.TimingContext

object TestRequestContexts {
    def nothing(request: RequestHeader, user: Option[User]): RequestContext = RequestContext(
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
      features = new Features {
        override val importStudentExtraTime: Boolean = false
        override val overwriteAssessmentTypeOnImport: Boolean = false
        override val twoWayMessages: Boolean = false
        override val announcementsAndQueriesCsv: Boolean = false
      },
      tabulaConfiguration = null,
    )
}
