package domain

import java.time.{Duration, OffsetDateTime}
import java.util.UUID

import domain.Assessment.State.Imported
import domain.Assessment.{AssessmentType, Brief, Platform}
import services.tabula.TabulaResponseParsers.SitsAssessmentType

package object tabula {

  import enumeratum.EnumEntry.CapitalWords
  import enumeratum.{Enum, EnumEntry}
  import warwick.sso._

  case class ExamPaper(
    code: String,
    duration: Option[Duration],
    title: Option[String],
    section: Option[String],
    schedule: Seq[ExamPaperSchedule],
  )

  case class ExamPaperSchedule(
    examProfileCode: String,
    slotId: String,
    sequence: String,
    locationSequence: String,
    startTime: OffsetDateTime,
    locationName: Option[String],
    students: Seq[ExamPaperScheduleStudent],
  )

  case class ExamPaperScheduleStudent(
    seatNumber: Option[Int],
    universityID: UniversityID,
    sprCode: String,
    occurrence: String,
    extraTimePerHour: Option[Duration],
  )

  case class Module(
    adminDepartment: DepartmentIdentity,
    code: String,
    name: String
  )

  case class AssessmentComponent(
    id: UUID,
    assessmentType: SitsAssessmentType,
    name: String,
    examPaper: Option[ExamPaper],
    module: Module,
    fullModuleCode: String,
    sequence: String
  ) {
    def asAssessment(existingAssessment: Option[Assessment], schedule: ExamPaperSchedule): Assessment = {
      val paper = examPaper.get

      Assessment(
        id = existingAssessment.map(_.id).getOrElse(UUID.randomUUID()),
        paperCode = paper.code,
        section = paper.section.filterNot(_ == "n/a"),
        title = paper.title.getOrElse(name),
        startTime = Some(schedule.startTime),
        duration = paper.duration,
        platform = existingAssessment.map(_.platform).getOrElse(schedule.locationName.map {
          case "Assignment" => Platform.TabulaAssignment
          case "Open book assessment" | "Files-based open book assessment" => Platform.OnlineExams
          case "Multiple Choice Questions" => Platform.QuestionmarkPerception
          case "Spoken exam under time conditions" | "Controlled online exam" => Platform.Moodle
          case _ => Platform.OnlineExams
        }.getOrElse(Platform.OnlineExams)),
        assessmentType = existingAssessment.map(_.assessmentType).getOrElse(schedule.locationName.map {
          case "Open book assessment" => AssessmentType.OpenBook
          case "Open Book Assessment, files based" => AssessmentType.OpenBookFileBased
          case "MCQ" => AssessmentType.MultipleChoice
          case "Spoken Open Book Assessment" => AssessmentType.Spoken
          case "Bespoke Option (only if previously agreed) " => AssessmentType.Bespoke
          case _ => AssessmentType.OpenBook
        }.getOrElse(AssessmentType.OpenBook)),
        brief = existingAssessment.map(_.brief).getOrElse(Brief(None, Nil, None)),
        invigilators = existingAssessment.map(_.invigilators).getOrElse(Set.empty),
        state = existingAssessment.map(_.state).getOrElse(Imported),
        tabulaAssessmentId = existingAssessment.map(_.tabulaAssessmentId).getOrElse(Some(id)),
        examProfileCode = schedule.examProfileCode,
        moduleCode = fullModuleCode,
        departmentCode = DepartmentCode(module.adminDepartment.code),
        sequence = sequence
      )
    }
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
    firstName: String,
    lastName: String,
    fullName: String,
    department: DepartmentIdentity,
    course: Option[Course],
    attendance: Option[Attendance],
    group: Option[StudentGroup],
    yearOfStudy: Option[YearOfStudy],
    disability: Option[SitsDisability],
    specialExamArrangementsExtraTime: Option[Duration],
    userType: UserType
  )

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
    val values: IndexedSeq[UserType] = findValues

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
    val values: IndexedSeq[Attendance] = findValues

    case object FullTime extends Attendance("F", "Full-time")

    case object PartTime extends Attendance("P", "Part-time")

  }

  sealed abstract class StudentGroup(override val entryName: String, val description: String) extends EnumEntry {
    // Default constructor for serialization
    def this() = this("", "")
  }

  object StudentGroup extends Enum[StudentGroup] {

    val values: IndexedSeq[StudentGroup] = findValues

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
}
