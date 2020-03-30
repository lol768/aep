package domain

import java.time._
import java.util.UUID

import com.typesafe.config.Config
import domain.Assessment.{AssessmentType, Platform, State}
import domain.dao.AnnouncementsTables.StoredAnnouncement
import domain.dao.AssessmentsTables.{StoredAssessment, StoredBrief}
import domain.dao.StudentAssessmentsTables.StoredStudentAssessment
import domain.dao.UploadedFilesTables.StoredUploadedFile
import domain.dao.{AuditEventsTable, OutgoingEmailsTables}
import play.api.db.slick.{DatabaseConfigProvider, HasDatabaseConfigProvider}
import services.sandbox.DataGeneration
import slick.basic.{BasicProfile, DatabaseConfig}
import warwick.fileuploads.UploadedFileControllerHelper.TemporaryUploadedFile
import warwick.fileuploads.UploadedFileSave
import warwick.sso.{Department => _, _}

import scala.util.Random

object Fixtures {
  import warwick.core.helpers.JavaTime.{timeZone => zone}

  object dateConversion {
    implicit class localDateTimeConversion(ldt: LocalDateTime) {
      def asOffsetDateTime: OffsetDateTime = ldt.atOffset(zone.getRules.getOffset(ldt))
      def asInstant: Instant = ldt.toInstant(zone.getRules.getOffset(ldt))
    }

    implicit class instantConversion(instant: Instant) {
      def asOffsetDateTime: OffsetDateTime = instant.atOffset(zone.getRules.getOffset(instant))
    }
  }

  object users {
    val noUniId: User = Users.create(Usercode("nouniid"))

    private val baseStaff: User = Users.create(
      usercode = null,
      universityId = None,
      staff = true,
      email = Some("no-reply@warwick.ac.uk")
    )

    private val baseStudent: User = Users.create(
      usercode = null,
      universityId = None,
      student = true,
      email = Some("no-reply@warwick.ac.uk")
    )

    val admin1: User = baseStaff.copy(
      usercode = Usercode("admin1"),
      universityId = Some(UniversityID("1200001")),
      name = Name(Some("Admin"), Some("User1"))
    )

    // Staff users here correspond with webgroup members defined in test.conf
    val staff1: User = baseStaff.copy(
      usercode = Usercode("staff1"),
      universityId = Some(UniversityID("1700001")),
      name = Name(Some("Staff"), Some("User1"))
    )

    // Staff users here correspond with webgroup members defined in test.conf
    val staff2: User = baseStaff.copy(
      usercode = Usercode("staff2"),
      universityId = Some(UniversityID("1700002")),
      name = Name(Some("Staff"), Some("User2"))
    )

    val staff3: User = baseStaff.copy(
      usercode = Usercode("staff3"),
      universityId = Some(UniversityID("1700003")),
      name = Name(Some("Staff"), Some("User1"))
    )

    val student1: User = baseStudent.copy(
      usercode = Usercode("student1"),
      universityId = Some(UniversityID("1900001")),
      name = Name(Some("Student"), Some("User1"))
    )
    val student2: User = baseStudent.copy(
      usercode = Usercode("student2"),
      universityId = Some(UniversityID("1900002")),
      name = Name(Some("Student"), Some("User2"))
    )

    def students(count: Int = 9): Set[User] = (1 to count).map { i =>
      val index = f"$i%03d"
      baseStudent.copy(
        usercode = Usercode(s"student$index"),
        universityId = Some(UniversityID(s"1900$index")),
        name = Name(Some("Student"), Some(s"User$index"))
      )
    }.toSet
  }

  object assessments {
    import dateConversion._


    val invigilator1 = "Mary"
    val invigilator2 = "Bob"

    def storedBrief: StoredBrief =
      StoredBrief(
        Some(DataGeneration.dummyWords(Random.between(6,30))),
        Seq.empty,
        Some(DataGeneration.fakePath)
      )

    def storedAssessment(uuid: UUID = UUID.randomUUID): StoredAssessment = {
      val date = LocalDate.of(2016, 1, 1)
      val createTime = LocalDateTime.of(date, LocalTime.of(8, 0, 0, 0))
      val startTime = LocalDateTime.of(date, LocalTime.of(Random.between(9, 15), 0, 0, 0))
      val code = f"${DataGeneration.fakeDept}${Random.between(101, 999)}%03d-${Random.between(1, 99)}%02d"
      val platform = Platform.values(Random.nextInt(Platform.values.size))
      val assType = AssessmentType.values(Random.nextInt(AssessmentType.values.size))
      val state = State.values(Random.nextInt(State.values.size))

      StoredAssessment(
        id = uuid,
        code = code,
        title = DataGeneration.fakeTitle,
        startTime = Some(startTime.asOffsetDateTime),
        duration = Duration.ofHours(3),
        platform = platform,
        assessmentType = assType,
        storedBrief = storedBrief,
        invigilators = List(invigilator1,invigilator2),
        created = createTime,
        version = createTime
        created = createTime.asOffsetDateTime,
        version = createTime.asOffsetDateTime,
        state = state
      )
    }
  }

  object studentAssessments {
    import dateConversion._

    def storedStudentAssessment(assId: UUID, studentId: UniversityID = users.student1.universityId.get): StoredStudentAssessment = {
      val createTime = LocalDateTime.of(2016, 1, 1, 8, 0, 0, 0)

      StoredStudentAssessment(
        id = UUID.randomUUID(),
        assessmentId = assId,
        studentId = studentId,
        inSeat = false,
        startTime = None,
        finaliseTime = None,
        uploadedFiles = List.empty,
        created = createTime.asOffsetDateTime,
        version = createTime.asOffsetDateTime
      )
    }
  }

  object announcements {
    import dateConversion._

    def storedAnnouncement(assId: UUID): StoredAnnouncement = {
      val createTime = LocalDateTime.of(2016, 1, 1, 8, 0, 0, 0)
      val text = DataGeneration.dummyWords(Random.between(6,30))

      StoredAnnouncement(
        id = UUID.randomUUID(),
        assessmentId = assId,
        text = text,
        created = createTime.asOffsetDateTime,
        version = createTime.asOffsetDateTime
      )
    }
  }

  object uploadedFiles {
    import dateConversion._
    import helpers.FileResourceUtils._

    object specialJPG {
      val path = "/night-heron-500-beautiful.jpg"
      val uploadedFileSave = UploadedFileSave(path, 8832L, "image/jpeg")
      def temporaryUploadedFile = TemporaryUploadedFile("file", byteSourceResource(path), uploadedFileSave)
    }

    object homeOfficeStatementPDF {
      val path = "/home-office-statement.pdf"
      val uploadedFileSave = UploadedFileSave(path, 8153L, "application/pdf")
      def temporaryUploadedFile = TemporaryUploadedFile("file", byteSourceResource(path), uploadedFileSave)
    }

    def storedUploadedAssessmentFile() = {
      val createTime = LocalDateTime.of(2016, 1, 1, 8, 0, 0, 0)

      StoredUploadedFile(
        id = UUID.randomUUID(),
        fileName = homeOfficeStatementPDF.uploadedFileSave.fileName,
        contentLength = homeOfficeStatementPDF.uploadedFileSave.contentLength,
        contentType = homeOfficeStatementPDF.uploadedFileSave.contentType,
        uploadedBy = users.staff1.usercode,
        ownerId = None,
        ownerType = Some(UploadedFileOwner.Assessment),
        created = createTime.asOffsetDateTime,
        version = createTime.asOffsetDateTime
      )
    }

    def storedUploadedStudentAssessmentFile(studentAssessmentId: UUID) = {
      val createTime = LocalDateTime.of(2016, 1, 1, 8, 0, 0, 0)

      StoredUploadedFile(
        id = UUID.randomUUID(),
        fileName = specialJPG.uploadedFileSave.fileName,
        contentLength = specialJPG.uploadedFileSave.contentLength,
        contentType = specialJPG.uploadedFileSave.contentType,
        uploadedBy = users.student1.usercode,
        ownerId = Some(studentAssessmentId),
        ownerType = Some(UploadedFileOwner.StudentAssessment),
        created = createTime.asOffsetDateTime,
        version = createTime.asOffsetDateTime
      )
    }
  }


  object schemas extends AuditEventsTable with OutgoingEmailsTables with HasDatabaseConfigProvider[ExtendedPostgresProfile] {
    override protected val dbConfigProvider: DatabaseConfigProvider = new DatabaseConfigProvider {
      override def get[P <: BasicProfile] = new DatabaseConfig[P] {
        override val profile: P = new ExtendedPostgresProfile {}.asInstanceOf[P]
        override def db: P#Backend#Database = ???
        override val driver: P = ???
        override def config: Config = ???
        override def profileName: String = ???
        override def profileIsObject: Boolean = ???
      }
    }
    override val jdbcTypes: PostgresCustomJdbcTypes = new PostgresCustomJdbcTypes(dbConfigProvider)
    import profile.api._

    def truncateAndReset =
      auditEvents.delete andThen
      outgoingEmails.table.delete andThen
      outgoingEmails.versionsTable.delete
  }
}
