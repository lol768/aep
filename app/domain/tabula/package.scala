package domain

import java.time.{Duration, OffsetDateTime}
import java.util.UUID

import domain.Assessment.State.Imported
import domain.Assessment.{AssessmentType, Brief, Platform}
import services.tabula.TabulaResponseParsers.SitsAssessmentType

package object tabula {

  import java.time.LocalDate

  import enumeratum.EnumEntry.CapitalWords
  import enumeratum.{Enum, EnumEntry}
  import helpers.StringUtils._
  import warwick.core.helpers.JavaTime
  import warwick.sso._

  import scala.collection.immutable

  case class ExamPaper(
    code: String,
    title: Option[String]
  )

  case class Module(
    adminDepartment: DepartmentIdentity,
    code: String
  )

  case class AssessmentComponent(
    id: String, //tabula Id
    assessmentType: SitsAssessmentType,
    examPaper: Option[ExamPaper],
    module: Module,
    cats: Option[String], //TODO -some API data has null cats - check this again once we know for sure tabula will provide active assessments
    sequence: String
  ) {
    def asAssessment(existingAssessment: Option[Assessment]): Assessment = Assessment(
      id = existingAssessment.map(_.id).getOrElse(UUID.randomUUID()),
      code = examPaper.map(_.code).getOrElse(""),
      title = examPaper.map(_.title.getOrElse("")).getOrElse(""),
      startTime = Some(OffsetDateTime.now), //TODO This would be populated from API
      duration = Duration.ofHours(3), //TODO - This would be populated from API.
      platform = existingAssessment.map(_.platform).getOrElse(Platform.OnlineExams),
      assessmentType = existingAssessment.map(_.assessmentType).getOrElse(AssessmentType.OpenBook),
      brief = existingAssessment.map(_.brief).getOrElse(Brief(None, Nil, None)),
      invigilators = existingAssessment.map(_.invigilators).getOrElse(Set.empty),
      state = existingAssessment.map(_.state).getOrElse(Imported),
      tabulaAssessmentId = existingAssessment.map(_.tabulaAssessmentId).getOrElse(Some(UUID.fromString(id))), //for assessments created within app directly this will be blank.
      moduleCode = s"${module.code}-${cats.getOrElse("0")}",
      departmentCode = DepartmentCode(module.adminDepartment.code),
      sequence = sequence
    )

  }

  case class ExamMembership(
    moduleCode: String,
    occurrence: String,
    academicYear: String,
    assessmentGroup: String,
    sequence: String,
    currentMembers: Seq[UniversityID],
  )

  case class Department(
    code: String,
    name: String,
    fullName: String,
    parentDepartment: Option[DepartmentIdentity]
  )

  case class SitsProfile(
    universityID: UniversityID,
    usercode: Usercode,
    fullName: String,
    dateOfBirth: LocalDate,
    phoneNumber: Option[String],
    warwickEmail: Option[String], // Only used for notification sending, not displayed on profile
    address: Option[Address],
    department: DepartmentIdentity,
    course: Option[Course],
    attendance: Option[Attendance],
    group: Option[StudentGroup],
    yearOfStudy: Option[YearOfStudy],
    startDate: Option[LocalDate],
    endDate: Option[LocalDate],
    nationality: Option[String],
    dualNationality: Option[String],
    tier4VisaRequired: Option[Boolean],
    disability: Option[SitsDisability],
    disabilityFundingStatus: Option[SitsDisabilityFundingStatus],
    jobTitle: Option[String],
    photo: Option[String],
    userType: UserType
  ) {
    def asUser: User = User(
      usercode = usercode,
      universityId = Some(universityID),
      name = Name(fullName.split(' ').headOption, fullName.split(' ').lastOption),
      email = warwickEmail,
      department = Some(warwick.sso.Department(None, Some(department.name), Some(department.code.toUpperCase))),
      userSource = Some("Tabula"),
      isStaffOrPGR = group.isEmpty || group.contains(StudentGroup.PGR),
      isStaffNotPGR = group.isEmpty,
      isStudent = userType == UserType.Student,
      isAlumni = false,
      isApplicant = userType == UserType.Applicant,
      isUndergraduate = group.contains(StudentGroup.Undergraduate) || group.contains(StudentGroup.Foundation),
      isPGT = group.contains(StudentGroup.PGT),
      isPGR = group.contains(StudentGroup.PGR),
      isFound = true,
      isVerified = true,
      isLoginDisabled = endDate.exists(_.isAfter(JavaTime.localDate)),
      rawProperties = Map()
    )
  }

  object SitsProfile {
    def universityId(e: Either[UniversityID, SitsProfile]): UniversityID = e.fold(identity, _.universityID)
  }

  case class Course(
    code: String,
    name: String
  )

  case class DepartmentIdentity(
    code: String,
    name: String
  )

  sealed abstract class UserType extends EnumEntry with CapitalWords

  object UserType extends Enum[UserType] {
    val values: immutable.IndexedSeq[UserType] = findValues

    case object Other extends UserType

    case object Student extends UserType

    case object Staff extends UserType

    case object EmeritusAcademic extends UserType

    case object Applicant extends UserType

    def apply(user: User): UserType =
      if (user.isStudent || user.isPGR) UserType.Student
      else if (user.isStaffNotPGR) UserType.Staff
      // TODO else if (user.isApplicant) UserType.Applicant
      else UserType.Other
  }

  sealed abstract class Attendance(override val entryName: String, val description: String) extends EnumEntry {
    // Default constructor for serialization
    def this() = this("", "")
  }

  object Attendance extends Enum[Attendance] {
    val values: immutable.IndexedSeq[Attendance] = findValues

    case object FullTime extends Attendance("F", "Full-time")

    case object PartTime extends Attendance("P", "Part-time")

  }

  sealed abstract class StudentGroup(override val entryName: String, val description: String) extends EnumEntry {
    // Default constructor for serialization
    def this() = this("", "")
  }

  object StudentGroup extends Enum[StudentGroup] {

    val values: immutable.IndexedSeq[StudentGroup] = findValues

    case object Foundation extends StudentGroup("F", "Foundation course")

    case object Undergraduate extends StudentGroup("UG", "Undergraduate")

    case object PGT extends StudentGroup("PG(T)", "Postgraduate (taught)")

    case object PGR extends StudentGroup("PG(R)", "Postgraduate (research)")

  }

  case class YearOfStudy(
    block: Int,
    level: Option[String]
  )

  case class SitsDisability(
    code: String,
    description: String,
    sitsDefinition: String,
  )

  case class SitsDisabilityFundingStatus(
    code: String,
    description: String,
  )

  case class Address(
    line1: Option[String],
    line2: Option[String],
    line3: Option[String],
    line4: Option[String],
    line5: Option[String],
    postcode: Option[String],
  ) {
    override def toString: String = Seq(line1, line2, line3, line4, line5, postcode)
      .flatten.filter(_.hasText).mkString(", ")
  }

  case class VisaStatus(
    tier4: Boolean,
    requiresClearance: Boolean
  )

}
