package services.tabula

import java.time.format.DateTimeFormatter
import java.time.{Duration, LocalDate, LocalDateTime, OffsetDateTime}
import java.util.UUID

import domain.tabula._
import play.api.libs.functional.syntax._
import play.api.libs.json._
import uk.ac.warwick.util.termdates.AcademicYear
import warwick.sso.{UniversityID, Usercode}

object TabulaResponseParsers {

  object TabulaProfileData {
    case class StudentCourseYearDetails(
      yearOfStudy: Int,
      studyLevel: Option[String],
      modeOfAttendance: String,
      enrolmentDepartment: DepartmentIdentity
    )

    val studentCourseYearDetailsReads: Reads[StudentCourseYearDetails] = (
      (__ \ "yearOfStudy").read[Int] and
      (__ \ "studyLevel").readNullable[String] and
      (__ \ "modeOfAttendance" \ "code").read[String] and
      (__ \ "enrolmentDepartment").read[DepartmentIdentity](departmentIdentity)
    ) (StudentCourseYearDetails.apply _)
    val studentCourseYearDetailsFields: Seq[String] =
      Seq("yearOfStudy", "studyLevel", "modeOfAttendance.code", "enrolmentDepartment")

    case class StudentCourseDetails(
      mostSignificant: Boolean,
      courseType: String,
      course: Course,
      level: Option[String],
      specialExamArrangementsExtraTime: Option[Duration],
      studentCourseYearDetails: Seq[StudentCourseYearDetails],
    )

    val studentCourseDetailsReads: Reads[StudentCourseDetails] = (
      (__ \ "mostSignificant").read[Boolean] and
      (__ \ "course" \ "type").read[String] and
      (__ \ "course").read[Course](courseReads) and
      (__ \ "levelCode").readNullable[String] and
      (__ \ "specialExamArrangementsExtraTime").readNullable[String].map(_.map(Duration.parse)) and
      (__ \ "studentCourseYearDetails").read[Seq[StudentCourseYearDetails]](Reads.seq(studentCourseYearDetailsReads))
    ) (StudentCourseDetails.apply _)
    val studentCourseDetailsFields: Seq[String] =
      Seq("mostSignificant", "course", "levelCode", "specialExamArrangementsExtraTime") ++
        studentCourseYearDetailsFields.map(f => s"studentCourseYearDetails.$f")

    case class Member(
      universityId: String,
      userId: String,
      firstName: String,
      lastName: String,
      fullName: String,
      homeDepartment: DepartmentIdentity,
      email: Option[String],
      disability: Option[SitsDisability],
      studentCourseDetails: Option[Seq[StudentCourseDetails]],
      userType: String,
    ) {
      def toUserProfile: SitsProfile = {
        val latestScd = studentCourseDetails.flatMap(scds => scds.find(_.mostSignificant))
        val latestScyd = latestScd.flatMap(_.studentCourseYearDetails.lastOption)
        val department = latestScyd.map(_.enrolmentDepartment).getOrElse(homeDepartment)

        SitsProfile(
          universityID = UniversityID(universityId),
          usercode = Usercode(userId),
          firstName = firstName,
          lastName = lastName,
          fullName = fullName,
          department = DepartmentIdentity(department.code, department.name),
          warwickEmail = email,
          course = latestScd.map(_.course),
          attendance = latestScyd.map(_.modeOfAttendance).flatMap(Attendance.withNameOption),
          group = latestScd.map(_.courseType).flatMap(StudentGroup.withNameOption),
          yearOfStudy = latestScyd.map(scyd => YearOfStudy(scyd.yearOfStudy, scyd.studyLevel)),
          disability = disability,
          specialExamArrangementsExtraTime = latestScd.flatMap(_.specialExamArrangementsExtraTime),
          userType = UserType.withName(userType)
        )
      }
    }

    val memberReads: Reads[Member] = (
      (__ \ "member" \ "universityId").read[String] and
      (__ \ "member" \ "userId").read[String] and
      (__ \ "member" \ "firstName").read[String] and
      (__ \ "member" \ "lastName").read[String] and
      (__ \ "member" \ "fullName").read[String] and
      (__ \ "member" \ "homeDepartment").read[DepartmentIdentity](departmentIdentity) and
      (__ \ "member" \ "email").readNullable[String] and
      (__ \ "member" \ "disability").readNullable[SitsDisability](disabilityReads) and
      (__ \ "member" \ "studentCourseDetails").readNullable[Seq[StudentCourseDetails]](Reads.seq(studentCourseDetailsReads)) and
      (__ \ "member" \ "userType").read[String]
    ) (Member.apply _)
    val memberFields: Seq[String] =
      Seq(
        "universityId", "userId", "firstName", "lastName", "fullName", "homeDepartment", "email", "userType"
      ).map(f => s"member.$f") ++
        disabilityFields.map(f => s"member.disability.$f") ++
        studentCourseDetailsFields.map(f => s"member.studentCourseDetails.$f")
  }

  private val codeAndNameBuilder = (__ \ "code").read[String] and (__ \ "name").read[String]

  val departmentIdentity: Reads[DepartmentIdentity] = codeAndNameBuilder(DepartmentIdentity.apply _)
  val courseReads: Reads[Course] = codeAndNameBuilder((code, name) => Course.apply(code, s"${code.toUpperCase} $name"))

  val disabilityReads: Reads[SitsDisability] = (
    (__ \ "code").read[String] and
    (__ \ "definition").read[String] and
    (__ \ "sitsDefinition").read[String]
  ) (SitsDisability.apply _)
  val disabilityFields: Seq[String] = Seq("code", "definition", "sitsDefinition")

  val universityIdResultReads: Reads[Seq[UniversityID]] = (__ \ "universityIds").read[Seq[String]](Reads.seq[String]).map(s => s.map(UniversityID))

  implicit val LocalDateReads: Reads[LocalDate] = new Reads[LocalDate] {

    final val DateFormat = "yyyy-MM-dd"

    override def reads(json: JsValue): JsResult[LocalDate] = json match {
      case JsString(s) => parseDate(s) match {
        case Some(d) => JsSuccess(d)
        case _ => JsError(Seq(JsPath() -> Seq(JsonValidationError("error.expected.localdate.format", DateFormat))))
      }
      case _ => JsError(Seq(JsPath() -> Seq(JsonValidationError("error.expected.date"))))
    }

    private def parseDate(input: String): Option[LocalDate] =
      scala.util.control.Exception.nonFatalCatch[LocalDate].opt(
        LocalDate.parse(input, DateTimeFormatter.ofPattern(DateFormat))
      )
  }

  implicit val LocalDateTimeReads: Reads[LocalDateTime] = new Reads[LocalDateTime] {
    final val DateFormat = "yyyy-MM-dd'T'HH:mm:ss"

    override def reads(json: JsValue): JsResult[LocalDateTime] = json match {
      case JsString(s) => parseDateTime(s) match {
        case Some(d) => JsSuccess(d)
        case _ => JsError(Seq(JsPath() -> Seq(JsonValidationError("error.expected.localdatetime.format", DateFormat))))
      }
      case _ => JsError(Seq(JsPath() -> Seq(JsonValidationError("error.expected.datetime"))))
    }

    private def parseDateTime(input: String): Option[LocalDateTime] =
      scala.util.control.Exception.nonFatalCatch[LocalDateTime].opt(
        LocalDateTime.parse(input, DateTimeFormatter.ofPattern(DateFormat))
      )
  }

  case class TabulaMemberSearchResult(
    universityID: UniversityID,
    usercode: Usercode,
    firstName: String,
    lastName: String,
    department: DepartmentIdentity,
    userType: String,
    photo: Option[String]
  ) extends Ordered[TabulaMemberSearchResult] {
    // Sort applicants to the bottom, and new applicants before others
    override def compare(that: TabulaMemberSearchResult): Int = {
      if (this.userType != that.userType) {
        if (this.userType == "Applicant") {
          1
        } else if (that.userType == "Applicant") {
          -1
        } else {
          0
        }
      } else {
        if (this.userType == "Applicant") {
          that.universityID.string.compare(this.universityID.string)
        } else {
          0
        }
      }
    }
  }

  val memberSearchFields: Seq[String] =
    Seq("universityId", "userId", "firstName", "lastName", "department", "userType")
      .map(f => s"results.$f")

  val memberSearchResultReads: Reads[TabulaMemberSearchResult] = (
    (__ \ "universityId").read[String].map[UniversityID](UniversityID.apply) and
    (__ \ "userId").read[String].map[Usercode](Usercode.apply) and
    (__ \ "firstName").read[String] and
    (__ \ "lastName").read[String] and
    (__ \ "department").read[DepartmentIdentity](departmentIdentity) and
    (__ \ "userType").read[String] and
    Reads.pure(None)
  ) (TabulaMemberSearchResult.apply _)

  val memberSearchResultsReads: Reads[Seq[TabulaMemberSearchResult]] = (__ \ "results").read(Reads.seq(memberSearchResultReads))

  case class TimetableEvent(
    start: LocalDateTime,
    end: LocalDateTime
  )

  val timetableEventReads: Reads[TimetableEvent] = Json.reads[TimetableEvent]
  val timetableEventsReads: Reads[Seq[TimetableEvent]] = (__ \ "events").read(Reads.seq(timetableEventReads))

  private case class ErrorMessage(message: String)

  private val errorMessageReads = Json.reads[ErrorMessage]

  val examPaperScheduleStudentReads: Reads[ExamPaperScheduleStudent] = (
    (__ \ "seatNumber").readNullable[Int] and
    (__ \ "universityId").read[String].map(UniversityID.apply) and
    (__ \ "sprCode").read[String] and
    (__ \ "occurrence").read[String] and
    (__ \ "specialExamArrangementsExtraTime").readNullable[Duration]
  ) (ExamPaperScheduleStudent.apply _)

  val examPaperScheduleReads: Reads[ExamPaperSchedule] = (
    (__ \ "examProfileCode").read[String] and
    (__ \ "academicYear").read[String].map(AcademicYear.parse) and
    (__ \ "slotId").read[String] and
    (__ \ "sequence").read[String] and
    (__ \ "locationSequence").read[String] and
    (__ \ "startTime").read[OffsetDateTime] and
    (__ \ "location" \ "name").readNullable[String] and
    (__ \ "students").read[Seq[ExamPaperScheduleStudent]](Reads.seq(examPaperScheduleStudentReads))
  ) (ExamPaperSchedule.apply _)

  val examPaperReads: Reads[ExamPaper] = (
    (__ \ "code").read[String] and
    (__ \ "duration").readNullable[Duration] and
    (__ \ "title").readNullable[String] and
    (__ \ "section").readNullable[String] and
    (__ \ "schedule").read[Seq[ExamPaperSchedule]](Reads.seq(examPaperScheduleReads))
  ) (ExamPaper.apply _)

  val moduleReads: Reads[Module] = (
    (__ \ "adminDepartment").read[DepartmentIdentity](departmentIdentity) and
    (__ \ "code").read[String] and
    (__ \ "name").read[String]
  ) (Module.apply _)

  case class SitsAssessmentType(
    astCode: String,
    name: String,
    code: String,
    value: String
  )

  object SitsAssessmentType {
    implicit val format: OFormat[SitsAssessmentType] = Json.format[SitsAssessmentType]
  }

  val assessmentComponentReads: Reads[AssessmentComponent] = (
    (__ \ "id").read[UUID] and
    (__ \ "type").read[SitsAssessmentType] and
    (__ \ "name").read[String] and
    (__ \ "examPaper").readNullable[ExamPaper](examPaperReads) and
    (__ \ "module").read[Module](moduleReads) and
    (__ \ "moduleCode").read[String] and
    (__ \ "sequence").read[String]
  ) (AssessmentComponent.apply _)

  val examMembershipReads: Reads[ExamMembership] = (
    (__ \ "moduleCode").read[String] and
    (__ \ "occurrence").read[String] and
    (__ \ "academicYear").read[String] and
    (__ \ "assessmentGroup").read[String] and
    (__ \ "sequence").read[String] and
    (__ \ "currentMembers").read[Seq[String]](Reads.seq[String]).map(s => s.map(UniversityID))
  ) (ExamMembership.apply _)

  val departmentReads: Reads[Department] = (
    (__ \ "code").read[String] and
    (__ \ "name").read[String] and
    (__ \ "fullName").read[String] and
    (__ \ "parentDepartment").readNullable[DepartmentIdentity](departmentIdentity)
  ) (Department.apply _)

  val assignmentReads: Reads[Assignment] = (
    (__ \ "id").read[String] and
    (__ \ "name").read[String] and
    (__ \ "academicYear").read[String].map(AcademicYear.parse) and
    (__ \ "summaryUrl").read[String]
  ) (Assignment.apply _)

  val attachmentReads: Reads[Attachment] = (
    (__ \ "id").read[String] and
      (__ \ "filename").read[String]
    ) (Attachment.apply _)

  val submissionReads: Reads[Submission] = (
    (__ \ "id").read[String] and
      (__ \ "submittedDate").read[OffsetDateTime] and
      (__ \ "late").read[Boolean] and
      (__ \ "authorisedLate").read[Boolean] and
      (__ \ "attachments").read[Seq[Attachment]](Reads.seq(attachmentReads))
    ) (Submission.apply _)

  // A response property containing an array of assessment components
  val responseAssessmentComponentsReads: Reads[Seq[AssessmentComponent]] =
    (__ \ "assessmentComponents").read(Reads.seq(assessmentComponentReads))

  val responsePaperCodesReads: Reads[Map[String, ExamMembership]] =
    (__ \ "paperCodes").read(Reads.map(examMembershipReads))

  val responseDepartmentReads: Reads[Seq[Department]] =
    (__ \ "departments").read(Reads.seq(departmentReads))

  val responseAssignmentReads: Reads[Assignment] = (__ \ "assignment").read(assignmentReads)

  val responseSubmissionReads: Reads[Submission] = (__ \ "submission").read(submissionReads)

  def validateAPIResponse[A](jsValue: JsValue, parser: Reads[A]): JsResult[A] = {
    (jsValue \ "success").validate[Boolean].flatMap {
      case false =>
        val status = (jsValue \ "status").validate[String].asOpt
        val errors = (jsValue \ "errors").validate[Seq[ErrorMessage]](Reads.seq(errorMessageReads)).asOpt
        JsError(__ \ "success", "Tabula API response not successful%s%s".format(
          status.map(s => s" (status: $s)").getOrElse(""),
          errors.map(e => s": ${e.map(_.message).mkString(", ")}").getOrElse("")
        ))
      case true =>
        jsValue.validate[A](parser)
    }
  }
}
