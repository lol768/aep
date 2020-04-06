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

  def WithSitting(assessmentId: UUID): Refiner[AuthenticatedRequest, StudentAssessmentSpecificRequest] =
    new Refiner[AuthenticatedRequest, StudentAssessmentSpecificRequest] {
      override protected def apply[A](implicit request: AuthenticatedRequest[A]): Refinement[StudentAssessmentSpecificRequest[A]] = {
        studentAssessmentService.getWithAssessment(universityId.get, assessmentId).successMapTo[Either[Result, StudentAssessmentSpecificRequest[A]]] { _.map { sitting =>
          Right(new StudentAssessmentSpecificRequest[A](sitting, request))
        }.getOrElse {
          Left(NotFound(views.html.errors.notFound()))
        }}
      }.map(_.fold(err => Left(showErrors(err)), identity))
    }

  def WithDepartmentsUserIsAdminFor: Refiner[AuthenticatedRequest, DepartmentAdminRequest] =
    new Refiner[AuthenticatedRequest, DepartmentAdminRequest] {
      override protected def apply[A](implicit request: AuthenticatedRequest[A]): Refinement[DepartmentAdminRequest[A]] = {
        request.context.user.map { user =>
          deptService.getDepartments.successMapTo[Either[Result, DepartmentAdminRequest[A]]] { allDepts =>

            val userAdminDepartments =
              if (request.context.userHasRole(Roles.Admin) || request.context.userHasRole(Roles.Sysadmin)) {
                // If the user is an admin or sysadmin they're an admin for all departments
                allDepts
              } else {
                val groupsForUser = groupService.getGroupsForUser(user.usercode).get
                allDepts.filter(dept => recursiveAdminGroupCheck(dept, allDepts, groupsForUser.map(_.name)))
              }

            if (userAdminDepartments.isEmpty) { // User is not an admin for any department
              Left(Forbidden(views.html.errors.forbidden(user.name.first)))
            } else {
              Right(new DepartmentAdminRequest[A](userAdminDepartments, request))
            }
          }.map(_.fold(err => Left(showErrors(err)), identity))
        }
      }.getOrElse { // No user attached to request
        Future.successful(Left(Forbidden(views.html.errors.forbidden(None))))
      }
    }

  def WithAssessmentToAdminister(assessmentId: UUID): Refiner[DepartmentAdminRequest, DepartmentAdminAssessmentRequest] =
    new Refiner[DepartmentAdminRequest, DepartmentAdminAssessmentRequest] {
      override protected def apply[A](implicit request: DepartmentAdminRequest[A]): Refinement[DepartmentAdminAssessmentRequest[A]] =
        assessmentService.get(assessmentId).successMapTo[Either[Result, DepartmentAdminAssessmentRequest[A]]] { assessment =>
          if (request.departments.exists(_.code.toLowerCase == assessment.departmentCode.lowerCase)) {
            Right(new DepartmentAdminAssessmentRequest[A](assessment, request.departments, request))
          } else {
            Left(Forbidden(views.html.errors.forbidden(request.user.flatMap(_.name.first))))
          }
        }.map(_.fold(err => Left(showErrors(err)), identity))
    }

  val IsStudentAssessmentStarted: Filter[StudentAssessmentSpecificRequest] =
    new Filter[StudentAssessmentSpecificRequest] {
      override protected def apply[A](implicit request: StudentAssessmentSpecificRequest[A]): Future[Option[Result]] =
        Future.successful {
          if (!request.sitting.started)
            Some(Forbidden(views.html.errors.assessmentNotStarted(request.sitting)))
          else
            None
        }
    }

  val IsStudentAssessmentNotFinished: Filter[StudentAssessmentSpecificRequest] =
    new Filter[StudentAssessmentSpecificRequest] {
      override protected def apply[A](implicit request: StudentAssessmentSpecificRequest[A]): Future[Option[Result]] =
        Future.successful {
          if (request.sitting.finalised)
            Some(Forbidden(views.html.errors.assessmentFinished(request.sitting)))
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

  def IsAdminForDepartment(deptCode: String): Filter[DepartmentAdminRequest] =
    new Filter[DepartmentAdminRequest] {
      override protected  def apply[A](implicit request: DepartmentAdminRequest[A]): Future[Option[Result]] = Future.successful {
        if (request.departments.exists(_.code == deptCode)) {
          None
        } else {
          Some(Forbidden(views.html.errors.forbidden(request.user.flatMap(_.name.first))))
        }
      }
    }

  // A user has admin permissions for a department if they're in the admin webgroup for that deprtment
  // or the admin webgroup for a parent department of that department.
  // Capped at a depth of 4 parent departments to avoid explosions if there's a circular reference somwhere
  private def recursiveAdminGroupCheck(
    department: Department,
    allDepartments: Seq[Department],
    groupNamesForUser: Seq[GroupName],
    depth: Int = 0
  ): Boolean = {
    val deptGroupPostfix = configuration.get[String]("app.assessmentManagerGroup")

    def userInDeptAdminGroup(dept: Department): Boolean = {
      groupNamesForUser.exists(_.string == s"${dept.code.toLowerCase()}-$deptGroupPostfix")
    }

    if (userInDeptAdminGroup(department)) {
      true
    } else {
      if (depth <= 4) {
        department.parentDepartment.exists { pdIdentity =>
          allDepartments.find(_.code == pdIdentity.code).exists { pd =>
            recursiveAdminGroupCheck(pd, allDepartments, groupNamesForUser, depth + 1)
          }
        }
      } else {
        false
      }
    }
  }
}
