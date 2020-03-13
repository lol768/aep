package helpers

import play.api.mvc.Request
import play.api.test.FakeRequest
import warwick.sso.{AuthenticatedRequest, LoginContextData, User}

object FakeRequestMethods {
  implicit class RequestOps[A](val req: Request[A]) extends AnyVal {
    // Sets a request attr that MockSSOClient understands
    def withUser(u: User): Request[A] =
      req.addAttr(AuthenticatedRequest.LoginContextDataAttr, new LoginContextData {
        override val user: Option[User] = Some(u)
        override val actualUser: Option[User] = Some(u)
      })
  }
}
