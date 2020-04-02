package services.refiners

import controllers.{ControllerHelper, ServiceResultErrorRendering}
import domain.tabula.Department
import javax.inject.{Inject, Singleton}
import play.api.Configuration
import play.api.mvc.{ActionFilter, ActionRefiner, Result}
import services.tabula.TabulaDepartmentService
import services.{AssessmentService, StudentAssessmentService}
import system.Roles
import system.routes.Types.UUID
import warwick.core.helpers.ServiceResults.Implicits._
import warwick.sso.{AuthenticatedRequest, GroupName, GroupService, UniversityID, User, Usercode}

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class ActionRefiners @Inject() (
  studentAssessmentService: StudentAssessmentService,
  assessmentService: AssessmentService,
  groupService: GroupService,
  deptService: TabulaDepartmentService,
  configuration: Configuration,
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

  def WithDepartmentsUserIsAdminFor: Refiner[AuthenticatedRequest, DepartmentAdminSpecificRequest] =
    new Refiner[AuthenticatedRequest, DepartmentAdminSpecificRequest] {
      override protected def apply[A](implicit request: AuthenticatedRequest[A]): Refinement[DepartmentAdminSpecificRequest[A]] = {
        request.user.map { user =>
          deptService.getDepartments.successMapTo[Either[Result, DepartmentAdminSpecificRequest[A]]] { allDepts =>
            val anyDeptGroupName = configuration.get[String]("app.anyDepartmentAdminGroup")

            val userAdminDepartments =
              if (userInGroup(user, anyDeptGroupName) || request.context.userHasRole(Roles.Sysadmin)) {
                // If the user is in the app admin usergroup, or is a sysadmin they're an admin for all departments
                allDepts
              } else {
                allDepts.filter(dept => recursiveAdminGroupCheck(user, dept, allDepts))
              }

            if (userAdminDepartments.isEmpty) { // User is not an admin for any department
              Left(Forbidden(views.html.errors.forbidden(user.name.first)))
            } else {
              Right(new DepartmentAdminSpecificRequest[A](userAdminDepartments, request))
            }
          }.map(_.fold(err => Left(showErrors(err)), identity))
        }
      }.getOrElse { // No user attached to request
        Future.successful(Left(Forbidden(views.html.errors.forbidden(None))))
      }
    }

  def WithAssessmentToAdminister(assessmentId: UUID): Refiner[DepartmentAdminSpecificRequest, DepartmentAdminAssessmentSpecificRequest] =
    new Refiner[DepartmentAdminSpecificRequest, DepartmentAdminAssessmentSpecificRequest] {
      override protected def apply[A](implicit request: DepartmentAdminSpecificRequest[A]): Refinement[DepartmentAdminAssessmentSpecificRequest[A]] =
        assessmentService.get(assessmentId).successMapTo[Either[Result, DepartmentAdminAssessmentSpecificRequest[A]]] { assessment =>
          if (request.departmentCodesUserIsAdminFor.exists(_.code.toLowerCase == assessment.departmentCode.lowerCase)) {
            Right(new DepartmentAdminAssessmentSpecificRequest[A](assessment, request.departmentCodesUserIsAdminFor, request))
          } else {
            Left(Forbidden(views.html.errors.forbidden(request.user.flatMap(_.name.first))))
          }
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

  val IsInvigilator: Filter[AssessmentSpecificRequest] =
    new Filter[AssessmentSpecificRequest] {
      override protected def apply[A](implicit request: AssessmentSpecificRequest[A]): Future[Option[Result]] = Future.successful {
        if (request.context.user.exists(u => request.assessment.invigilators.contains(u.usercode)))
          None
        else
          Some(Forbidden(views.html.errors.forbidden(None)))
      }
    }

  def IsAdminForDepartment(deptCode: String): Filter[DepartmentAdminSpecificRequest] =
    new Filter[DepartmentAdminSpecificRequest] {
      override protected  def apply[A](implicit request: DepartmentAdminSpecificRequest[A]): Future[Option[Result]] = Future.successful {
        if (request.departmentCodesUserIsAdminFor.exists(_.code == deptCode)) {
          None
        } else {
          Some(Forbidden(views.html.errors.forbidden(request.user.flatMap(_.name.first))))
        }
      }
    }

  private def userInGroup(user: User, groupName: String): Boolean =
    groupService.isUserInGroup(user.usercode, GroupName(groupName)).getOrElse(false)

  private def userInDeptAdminGroup(user: User, deptCode: String): Boolean = {
    val deptGroupPostfix = configuration.get[String]("app.assessmentManagerGroup")
    userInGroup(
      user,
      groupName = s"${deptCode.toLowerCase()}-$deptGroupPostfix"
    )
  }

  // A user has admin permissions for a department if they're in the admin webgroup for that deprtment
  // or the admin webgroup for a parent department of that department.
  // Capped at a depth of 4 parent departments to avoid explosions if there's a circular reference somwhere
  private def recursiveAdminGroupCheck(user: User, department: Department, allDepartments: Seq[Department], depth: Int = 0): Boolean = {
    if (userInDeptAdminGroup(user, department.code)) {
      true
    } else {
      if (depth <= 4) {
        department.parentDepartment.exists { pdIdentity =>
          allDepartments.find(_.code == pdIdentity.code).exists { pd =>
            recursiveAdminGroupCheck(user, pd, allDepartments, depth + 1)
          }
        }
      } else {
        false
      }
    }
  }
}
