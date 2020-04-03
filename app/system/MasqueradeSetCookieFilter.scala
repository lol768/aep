package system

import javax.inject.{Inject, Singleton}
import play.api.Configuration
import play.api.libs.streams.Accumulator
import play.api.mvc.{Cookie, EssentialAction, EssentialFilter, Results}
import uk.ac.warwick.sso.client.SSOClientHandlerImpl.DEFAULT_MASQUERADE_COOKIE_NAME

import scala.concurrent.ExecutionContext

@Singleton
class MasqueradeSetCookieFilter @Inject()(
  configuration: Configuration
)(implicit executionContext: ExecutionContext) extends EssentialFilter {

  val cookieName: String = configuration.getOptional[String]("sso-client.masquerade.cookie.name")
    .getOrElse(DEFAULT_MASQUERADE_COOKIE_NAME)

  val cookiePath: String = configuration.getOptional[String]("sso-client.masquerade.cookie.path")
    .orElse(configuration.getOptional[String]("sso-client.shire.sscookie.path"))
    .getOrElse("/")
  val cookieDomain: Option[String] = configuration.getOptional[String]("sso-client.masquerade.cookie.domain")
    .orElse(configuration.getOptional[String]("sso-client.shire.sscookie.domain"))


  override def apply(next: EssentialAction): EssentialAction = EssentialAction { req =>
    if (req.queryString.contains(cookieName)
      && !req.cookies.get(cookieName).exists(_.value == req.queryString(cookieName).head)
      && configuration.get[Boolean]("app.allowMasqUrls")) {
      Accumulator.done(Results.TemporaryRedirect(req.uri).withCookies(Cookie(
        name = cookieName,
        value = req.queryString(cookieName).head,
        domain = cookieDomain,
        path = cookiePath,
        httpOnly = true
      )))
    } else {
      next(req)
    }
  }

}
