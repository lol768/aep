package services.refiners

import controllers.{ControllerHelper, ServiceResultErrorRendering}
import javax.inject.{Inject, Singleton}
import play.api.mvc.{ActionFilter, ActionRefiner, Result}
import services.{AssessmentService, StudentAssessmentService}
import system.Roles
import system.routes.Types.UUID
import warwick.core.helpers.ServiceResults.Implicits._
import warwick.sso.{AuthenticatedRequest, UniversityID, Usercode}

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class ActionRefiners @Inject() (
  studentAssessmentService: StudentAssessmentService,
  assessmentService: AssessmentService,
)(implicit ec: ExecutionContext) extends ServiceResultErrorRendering {

  // Type aliases to shorten some long lines
  type AuthReq[A] = AuthenticatedRequest[A]
  type Refinement[R] = Future[Either[Result, R]]

  /** Base class to reduce the subclasses by a couple of lines each */
  abstract class Refiner[P[A] <: AuthReq[A], R[A]] extends ActionRefiner[P, R] {
    // Has implicit keyword built-in so that implementing bodies don't have to do it themselves
    protected def apply[A](implicit request: P[A]): Refinement[R[A]]
    override protected final def refine[A](request: P[A]): Refinement[R[A]] = apply(request)
    override protected def executionContext: ExecutionContext = ec
    def universityId(implicit request: AuthReq[_]): Option[UniversityID] = request.context.user.flatMap(_.universityId)
    def usercode(implicit request: AuthReq[_]): Option[Usercode] = request.context.user.map(_.usercode)
  }

  abstract class Filter[R[A] <: AuthReq[A]] extends ActionFilter[R] {
    protected def apply[A](implicit request: R[A]): Future[Option[Result]]
    override protected def filter[A](request: R[A]): Future[Option[Result]] = apply(request)
    override protected def executionContext: ExecutionContext = ec
    def universityId(implicit request: AuthReq[_]): Option[UniversityID] = request.context.user.flatMap(_.universityId)
    def usercode(implicit request: AuthReq[_]): Option[Usercode] = request.context.user.map(_.usercode)
  }

  def WithAssessment(assessmentId: UUID): Refiner[AuthenticatedRequest, AssessmentSpecificRequest] =
    new Refiner[AuthenticatedRequest, AssessmentSpecificRequest] {
      override protected def apply[A](implicit request: AuthenticatedRequest[A]): Refinement[AssessmentSpecificRequest[A]] = {
        assessmentService.get(assessmentId).successMapTo { a =>
          Right(new AssessmentSpecificRequest[A](a, request))
        }
    }.map(_.fold(err => Left(showErrors(err)), identity))
}

  def WithStudentAssessmentWithAssessment(assessmentId: UUID): Refiner[AuthenticatedRequest, StudentAssessmentSpecificRequest] =
    new Refiner[AuthenticatedRequest, StudentAssessmentSpecificRequest] {
      override protected def apply[A](implicit request: AuthenticatedRequest[A]): Refinement[StudentAssessmentSpecificRequest[A]] = {
        studentAssessmentService.getWithAssessment(universityId.get, assessmentId).successMapTo[Either[Result, StudentAssessmentSpecificRequest[A]]] { _.map { studentAssessmentWithAssessment =>
          Right(new StudentAssessmentSpecificRequest[A](studentAssessmentWithAssessment, request))
        }.getOrElse {
          Left(NotFound(views.html.errors.notFound()))
        }}
      }.map(_.fold(err => Left(showErrors(err)), identity))
    }

  val IsStudentAssessmentStarted: Filter[StudentAssessmentSpecificRequest] =
    new Filter[StudentAssessmentSpecificRequest] {
      override protected def apply[A](implicit request: StudentAssessmentSpecificRequest[A]): Future[Option[Result]] =
        Future.successful {
          if (!request.studentAssessmentWithAssessment.started)
            Some(Forbidden(views.html.errors.assessmentNotStarted(request.studentAssessmentWithAssessment)))
          else
            None
        }
    }

  val IsStudentAssessmentNotFinished: Filter[StudentAssessmentSpecificRequest] =
    new Filter[StudentAssessmentSpecificRequest] {
      override protected def apply[A](implicit request: StudentAssessmentSpecificRequest[A]): Future[Option[Result]] =
        Future.successful {
          if (request.studentAssessmentWithAssessment.finalised)
            Some(Forbidden(views.html.errors.assessmentFinished(request.studentAssessmentWithAssessment)))
          else
            None
        }
    }

  val IsInvigilatorOrAdmin: Filter[AssessmentSpecificRequest] =
    new Filter[AssessmentSpecificRequest] {
      override protected def apply[A](implicit request: AssessmentSpecificRequest[A]): Future[Option[Result]] = Future.successful {
        if (request.context.user.exists(u => request.assessment.invigilators.contains(u.usercode)) || request.context.userHasRole(Roles.Admin))
          None
        else
          Some(Forbidden(views.html.errors.forbidden(None)))
      }
    }
}
