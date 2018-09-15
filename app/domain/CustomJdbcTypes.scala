package domain

import java.sql.{PreparedStatement, ResultSet, Timestamp}
import java.time.format.DateTimeFormatter
import java.time.{OffsetDateTime, ZoneId, ZoneOffset}
import java.util.{Calendar, TimeZone}

import enumeratum.SlickEnumSupport
import javax.inject.{Inject, Singleton}
import play.api.db.slick.DatabaseConfigProvider
import play.api.libs.json.{JsValue, Json}
import slick.jdbc.{JdbcProfile, JdbcType}
import warwick.sso.{UniversityID, Usercode}

/**
  * Defines custom types that Slick can use to bind things directly to a database column.
  * This is a class rather than an object because the types need access to the current database
  * profile, which can vary. Inject this into your DAO and import from there.
  */
@Singleton
class CustomJdbcTypes @Inject() (
  protected val dbConfigProvider: DatabaseConfigProvider
) extends SlickEnumSupport {

  protected lazy val dbConfig = dbConfigProvider.get[JdbcProfile]
  lazy val profile = dbConfig.profile

  import profile.DriverJdbcType
  import profile.api._

  implicit val usercodeTypeMapper: JdbcType[Usercode] = MappedColumnType.base(
    _.string,
    Usercode.apply
  )

  implicit val uniIdTypeMapper: JdbcType[UniversityID] = MappedColumnType.base(
    _.string,
    UniversityID.apply
  )

  /**
    * Long-winded way for OffsetDateTime to be stored as UTC in the database.
    * The only way to store anything other than the JVM timezone in a Timestamp is to call
    * a special setTimestamp method that contains a Calendar containing the desired timezone.
    * This isn't exposed by any Slick types hence the need to implement DriverJdbcType.
    */
  implicit val offsetDateTimeTypeMapper: JdbcType[OffsetDateTime] = new DriverJdbcType[OffsetDateTime]() {
    private[this] val referenceCalendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
    private[this] val literalDateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS")

    private[this] def map(v: OffsetDateTime): Timestamp = Timestamp.from(v.toInstant)
    private[this] def comap(ts: Timestamp): OffsetDateTime = OffsetDateTime.ofInstant(ts.toInstant, ZoneId.systemDefault())

    def sqlType: Int = java.sql.Types.TIMESTAMP
    def setValue(v: OffsetDateTime, p: PreparedStatement, idx: Int): Unit = p.setTimestamp(idx, map(v), referenceCalendar)
    def getValue(r: ResultSet, idx: Int): OffsetDateTime = {
      val v = r.getTimestamp(idx, referenceCalendar)

      if ((v eq null) || wasNull(r, idx)) null
      else comap(v)
    }
    def updateValue(v: OffsetDateTime, r: ResultSet, idx: Int): Unit = r.updateTimestamp(idx, map(v))
    override def valueToSQLLiteral(value: OffsetDateTime): String =
      s"{ts '${value.atZoneSameInstant(ZoneOffset.UTC).format(literalDateTimeFormatter)}'}"
  }

  implicit val jsonTypeMapper: JdbcType[JsValue] = MappedColumnType.base[JsValue, String](Json.stringify, Json.parse)

  implicit val symbolTypeMapper: JdbcType[Symbol] = MappedColumnType.base[Symbol, String](_.name, Symbol.apply)

  // Example enum
  implicit val colour: JdbcType[Colour] = mappedColumnTypeForEnum(Colour)
}
