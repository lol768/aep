package domain

import com.typesafe.config.Config
import domain.dao.{AuditEventsTable, OutgoingEmailsTables}
import play.api.db.slick.{DatabaseConfigProvider, HasDatabaseConfigProvider}
import slick.basic.{BasicProfile, DatabaseConfig}
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
    override val jdbcTypes: CustomJdbcTypes = new CustomJdbcTypes(dbConfigProvider)
    import dbConfig.profile.api._

    def truncateAndReset =
      auditEvents.delete andThen
      outgoingEmails.table.delete andThen
      outgoingEmails.versionsTable.delete
  }
}
