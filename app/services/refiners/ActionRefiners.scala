package services.refiners

import javax.inject.{Inject, Singleton}
import play.api.mvc.{ActionRefiner, Result}
import services.StudentAssessmentService
import system.ImplicitRequestContext
import system.routes.Types.UUID
import warwick.sso.AuthenticatedRequest

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class ActionRefiners @Inject() (
  studentAssessmentService: StudentAssessmentService
)(implicit ec: ExecutionContext) extends ImplicitRequestContext{

  // Type aliases to shorten some long lines
  type AuthReq[A] = AuthenticatedRequest[A]
  type Refinement[R] = Future[Either[Result, R]]

  /** Base class to reduce the subclasses by a couple of lines each */
  abstract class Refiner[P[A] <: AuthReq[A], R[A]] extends ActionRefiner[P, R] {
    // Has implicit keyword built-in so that implementing bodies don't have to do it themselves
    protected def apply[A](implicit request: P[A]): Refinement[R[A]]
    override protected final def refine[A](request: P[A]): Refinement[R[A]] = apply(request)
    override protected def executionContext: ExecutionContext = ec
    def universityId(implicit request: AuthReq[_]) = request.context.user.flatMap(_.universityId)
    def usercode(implicit request: AuthReq[_]) = request.context.user.map(_.usercode)
  }

  def WithStudentAssessmentWithAssessment(assessmentId: UUID): Refiner[AuthenticatedRequest, AssessmentSpecificRequest] =
    new Refiner[AuthenticatedRequest, AssessmentSpecificRequest] {
      override protected def apply[A](implicit request: AuthenticatedRequest[A]): Refinement[AssessmentSpecificRequest[A]] = {
        studentAssessmentService.getWithAssessment(universityId.get, assessmentId).map { studentAssessmentWithAssessment =>
          Right(new AssessmentSpecificRequest[A](studentAssessmentWithAssessment, request))
        }
      }
    }
}
