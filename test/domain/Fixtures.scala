package domain

import java.time._
import java.util.UUID

import com.typesafe.config.Config
import domain.Assessment.{AssessmentType, Platform}
import domain.dao.AssessmentsTables.{StoredAssessment, StoredBrief}
import domain.dao.{AuditEventsTable, OutgoingEmailsTables}
import play.api.db.slick.{DatabaseConfigProvider, HasDatabaseConfigProvider}
import slick.basic.{BasicProfile, DatabaseConfig}
import warwick.fileuploads.UploadedFileControllerHelper.TemporaryUploadedFile
import warwick.fileuploads.UploadedFileSave
import warwick.sso.{Department => _, _}

object Fixtures {
  import warwick.core.helpers.JavaTime.{timeZone => zone}

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
  }

  object assessments {
    val storedBrief: StoredBrief =
      StoredBrief(
        Some("Here is a brief"),
        Seq(UUID.randomUUID()),
        Some("https://example.com/a-brief-history-of-briefs")
      )

    def storedAssessment(uuid: UUID): StoredAssessment = {
      val date = LocalDate.of(2018, 1, 1)
      val localCreateTime = LocalDateTime.of(date, LocalTime.of(8, 0, 0, 0))
      val localStartTime = LocalDateTime.of(date, LocalTime.of(11, 0, 0, 0))
      val createTime = localCreateTime.atOffset(zone.getRules.getOffset(localCreateTime))
      val startTime = localStartTime.atOffset(zone.getRules.getOffset(localStartTime))

      StoredAssessment(
        id = uuid,
        code = "IT101",
        title = "ITIL Foundation",
        startTime = Some(startTime),
        duration = Duration.ofHours(3),
        platform = Platform.OnlineExams,
        assessmentType = AssessmentType.OpenBook,
        storedBrief = storedBrief,
        created = createTime,
        version = createTime
      )
    }
  }

  object uploadedFiles {
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
    import dbConfig.profile.api._

    def truncateAndReset =
      auditEvents.delete andThen
      outgoingEmails.table.delete andThen
      outgoingEmails.versionsTable.delete
  }
}
