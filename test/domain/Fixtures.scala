package domain

import java.time._
import java.util.UUID

import com.google.common.io.{ByteSource, Files}
import com.typesafe.config.Config
import domain.Assessment.{DurationStyle, Platform}
import domain.dao.AnnouncementsTables.StoredAnnouncement
import domain.dao.AssessmentsTables.StoredAssessment
import domain.dao.StudentAssessmentsTables.{StoredDeclarations, StoredStudentAssessment}
import domain.dao.UploadedFilesTables.StoredUploadedFile
import domain.dao.{AuditEventsTable, OutgoingEmailsTables}
import play.api.db.slick.{DatabaseConfigProvider, HasDatabaseConfigProvider}
import play.api.libs.Files.TemporaryFileCreator
import services.DataGenerationService
import services.sandbox.DataGeneration
import slick.basic.{BasicProfile, DatabaseConfig}
import warwick.core.helpers.JavaTime
import warwick.fileuploads.UploadedFileControllerHelper.TemporaryUploadedFile
import warwick.fileuploads.UploadedFileSave
import warwick.sso.{Department => _, _}

object Fixtures {

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

    // Set up as an app admin in fake-userlookup.conf
    val admin1: User = baseStaff.copy(
      usercode = Usercode("admin1"),
      universityId = Some(UniversityID("1200001")),
      name = Name(Some("Admin"), Some("User1"))
    )

    // Set up as a departmental admin for Philosophy (PH) in fake-userlookup.conf
    val phAdmin: User = baseStaff.copy(
      usercode = Usercode("phadmin"),
      universityId = Some(UniversityID("1700101")),
      name = Name(Some("Philosophy"), Some("Admin"))
    )

    // Set up as a departmental admin for Life Sciences (LF) in fake-userlookup.conf
    val lfAdmin: User = baseStaff.copy(
      usercode = Usercode("lfadmin"),
      universityId = Some(UniversityID("1700102")),
      name = Name(Some("LifeSci"), Some("Admin"))
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
    val student3: User = baseStudent.copy(
      usercode = Usercode("student3"),
      universityId = Some(UniversityID("1900003")),
      name = Name(Some("Student"), Some("User3"))
    )
    val student4: User = baseStudent.copy(
      usercode = Usercode("student4"),
      universityId = Some(UniversityID("1900004")),
      name = Name(Some("Student"), Some("User4"))
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

    def storedAssessment(
      uuid: UUID = UUID.randomUUID,
      platformOption: Option[Platform] = None,
      duration: Option[Duration] = Some(Duration.ofHours(3)),
      durationStyle: DurationStyle = DurationStyle.DayWindow,
    )(implicit dataGeneration: DataGeneration): StoredAssessment =
      DataGenerationService.makeStoredAssessment(uuid, platformOption, duration, durationStyle)

    // If you just need any old assessment that's assigned to philosophy to test with...
    lazy val philosophyAssessment: Assessment = Assessment(
      UUID.randomUUID, "ph-assessment", None, "Philosophy Assessment", Some(JavaTime.offsetDateTime.plusHours(1)),  Some(Duration.ofHours(3)), Set(Platform.OnlineExams),
      Some(DurationStyle.DayWindow), Assessment.Brief.empty, Set.empty, Assessment.State.Approved, None, Set.empty, "meh", "ph101", DepartmentCode("ph"),
      "sequence"
    )
  }

  object studentAssessments {
    import helpers.DateConversion._

    def storedStudentAssessment(
      assId: UUID,
      studentId: UniversityID = users.student1.universityId.get,
      hourlyExtraTime: Option[Option[Duration]] = None
    ): StoredStudentAssessment = {
      DataGenerationService.makeStoredStudentAssessment(assId, studentId, hourlyExtraTime = hourlyExtraTime)
    }

    def storedDeclarations(id: UUID): StoredDeclarations = {
      val createTime = LocalDateTime.of(2016, 1, 1, 8, 0, 0, 0)

      StoredDeclarations(
        studentAssessmentId = id,
        acceptsAuthorship = true,
        selfDeclaredRA = Some(false),
        completedRA = true,
        created = createTime.asOffsetDateTime,
        version = createTime.asOffsetDateTime
      )
    }
  }

  object announcements {
    import helpers.DateConversion._

    def storedAnnouncement(assId: UUID, usercode: Usercode)(implicit dataGeneration: DataGeneration): StoredAnnouncement = {
      import dataGeneration.random
      val createTime = LocalDateTime.of(2016, 1, 1, 8, 0, 0, 0)
      val text = dataGeneration.dummyWords(random.between(6,30)).trim

      StoredAnnouncement(
        id = UUID.randomUUID(),
        sender = Some(usercode),
        assessmentId = assId,
        text = text,
        created = createTime.asOffsetDateTime,
        version = createTime.asOffsetDateTime
      )
    }
  }

  object uploadedFiles {
    import helpers.DateConversion._
    import helpers.FileResourceUtils._

    object specialJPG {
      val path = "/night-heron-500-beautiful.jpg"
      val uploadedFileSave: UploadedFileSave = UploadedFileSave("night-heron-500-beautiful.jpg", 8832L, "image/jpeg")
      def byteSource: ByteSource = byteSourceResource(path)
      def temporaryUploadedFile(implicit temporaryFileCreator: TemporaryFileCreator): TemporaryUploadedFile = {
        val tempFile = temporaryFileCreator.create("night-heron-500-beautiful", ".jpg")
        byteSource.copyTo(Files.asByteSink(tempFile))
        TemporaryUploadedFile("file", Files.asByteSource(tempFile.path.toFile), uploadedFileSave, tempFile)
      }
    }

    object homeOfficeStatementPDF {
      val path = "/home-office-statement.pdf"
      val uploadedFileSave: UploadedFileSave = UploadedFileSave("home-office-statement.pdf", 8153L, "application/pdf")
      def byteSource: ByteSource = byteSourceResource(path)
      def temporaryUploadedFile(implicit temporaryFileCreator: TemporaryFileCreator): TemporaryUploadedFile = {
        val tempFile = temporaryFileCreator.create("home-office-statement", ".pdf")
        byteSource.copyTo(Files.asByteSink(tempFile))
        TemporaryUploadedFile("file", Files.asByteSource(tempFile.path.toFile), uploadedFileSave, tempFile)
      }
    }

    def storedUploadedAssessmentFile(): StoredUploadedFile = {
      val createTime = LocalDateTime.of(2016, 1, 1, 8, 0, 0, 0)

      StoredUploadedFile(
        id = UUID.randomUUID(),
        fileName = homeOfficeStatementPDF.uploadedFileSave.fileName,
        contentLength = homeOfficeStatementPDF.uploadedFileSave.contentLength,
        contentType = homeOfficeStatementPDF.uploadedFileSave.contentType,
        uploadedBy = users.staff1.usercode,
        uploadStarted = createTime.asOffsetDateTime.minusSeconds(7L),
        ownerId = None,
        ownerType = Some(UploadedFileOwner.AssessmentBrief),
        created = createTime.asOffsetDateTime,
        version = createTime.asOffsetDateTime,
      )
    }

    def storedUploadedStudentAssessmentFile(
      studentAssessmentId: UUID,
      id: UUID = UUID.randomUUID(),
      createTime: OffsetDateTime = LocalDateTime.of(2016, 1, 1, 8, 0, 0, 0).asOffsetDateTime,
      uploadDuration: Duration = Duration.ofSeconds(7)
    ) = {
      StoredUploadedFile(
        id = id,
        fileName = specialJPG.uploadedFileSave.fileName,
        contentLength = specialJPG.uploadedFileSave.contentLength,
        contentType = specialJPG.uploadedFileSave.contentType,
        uploadedBy = users.student1.usercode,
        uploadStarted = createTime.minus(uploadDuration),
        ownerId = Some(studentAssessmentId),
        ownerType = Some(UploadedFileOwner.StudentAssessment),
        created = createTime,
        version = createTime
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
