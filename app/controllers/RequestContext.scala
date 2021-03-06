package controllers

import play.api.Configuration
import play.api.mvc.{Flash, RequestHeader}
import services.Navigation
import services.tabula.TabulaConfiguration
import system.{CSRFPageHelper, CSRFPageHelperFactory, Features}
import warwick.core.timing.{ServerTimingFilter, TimingContext}
import warwick.sso.{AuthenticatedRequest, LoginContext, SSOClient, User}

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.util.Try

case class RequestContext(
  path: String,
  user: Option[User],
  actualUser: Option[User],
  loginUrl: String,
  logoutUrl: String,
  myWarwickBaseUrl: String,
  navigation: Seq[Navigation],
  flash: Flash,
  csrfHelper: CSRFPageHelper,
  userAgent: Option[String],
  ipAddress: String,
  timingData: TimingContext.Data,
  appFullName: String,
  appContactEmail: String,
  features: Features,
  tabulaConfiguration: TabulaConfiguration,
) extends TimingContext {
  def isMasquerading: Boolean = user != actualUser
}

object RequestContext {

  def authenticated(sso: SSOClient, request: AuthenticatedRequest[_], navigation: Seq[Navigation], csrfHelperFactory: CSRFPageHelperFactory, configuration: Configuration, features: Features, tabulaConfiguration: TabulaConfiguration): RequestContext =
    RequestContext(sso, request, request.context.user, request.context.actualUser, navigation, csrfHelperFactory, configuration, features, tabulaConfiguration)

  def authenticated(sso: SSOClient, request: RequestHeader, navigation: LoginContext => Seq[Navigation], csrfHelperFactory: CSRFPageHelperFactory, configuration: Configuration, features: Features, tabulaConfiguration: TabulaConfiguration): RequestContext = {
    import ExecutionContext.Implicits.global

    val eventualRequestContext = sso.withUser(request) { loginContext =>
      Future.successful(Right(RequestContext(sso, request, loginContext.user, loginContext.actualUser, navigation(loginContext), csrfHelperFactory, configuration, features, tabulaConfiguration)))
    }.map(_.getOrElse(throw new IllegalStateException))

    Await.result(eventualRequestContext, Duration.Inf)
  }

  def anonymous(sso: SSOClient, request: RequestHeader, navigation: Seq[Navigation], csrfHelperFactory: CSRFPageHelperFactory, configuration: Configuration, features: Features, tabulaConfiguration: TabulaConfiguration): RequestContext =
    RequestContext(sso, request, None, None, navigation, csrfHelperFactory, configuration, features, tabulaConfiguration)

  def apply(sso: SSOClient, request: RequestHeader, user: Option[User], actualUser: Option[User], navigation: Seq[Navigation], csrfHelperFactory: CSRFPageHelperFactory, configuration: Configuration, features: Features, tabulaConfiguration: TabulaConfiguration): RequestContext = {
    val target = (if (request.secure) "https://" else "http://") + request.host + request.path
    val linkGenerator = sso.linkGenerator(request)
    linkGenerator.setTarget(target)

    RequestContext(
      path = request.path,
      user = user,
      actualUser = actualUser,
      loginUrl = linkGenerator.getLoginUrl,
      logoutUrl = linkGenerator.getLogoutUrl,
      myWarwickBaseUrl = configuration.get[String]("mywarwick.instances.0.baseUrl"),
      navigation = navigation,
      flash = Try(request.flash).getOrElse(Flash()),
      csrfHelper = transformCsrfHelper(csrfHelperFactory, request),
      userAgent = request.headers.get("User-Agent"),
      ipAddress = request.remoteAddress,
      timingData = request.attrs.get(ServerTimingFilter.TimingData).getOrElse(new TimingContext.Data),
      appFullName = configuration.get[String]("app.name.full"),
      appContactEmail = configuration.get[String]("app.contactEmail"),
      features = features,
      tabulaConfiguration = tabulaConfiguration
    )
  }

  private[this] def transformCsrfHelper(helperFactory: CSRFPageHelperFactory, req: RequestHeader): CSRFPageHelper = {
    val token = play.filters.csrf.CSRF.getToken(req)

    val helper = helperFactory.getInstance(token)
    helper
  }

}
