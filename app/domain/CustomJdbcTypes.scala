package domain

import enumeratum.SlickEnumSupport
import javax.inject.{Inject, Singleton}
import play.api.db.slick.DatabaseConfigProvider
import play.api.libs.json.{JsValue, Json}
import slick.jdbc.{JdbcProfile, JdbcType}
import warwick.slick.jdbctypes.JdbcDateTypesUtc
import warwick.sso.{UniversityID, Usercode}

/**
  * Defines custom types that Slick can use to bind things directly to a database column.
  * This is a class rather than an object because the types need access to the current database
  * profile, which can vary. Inject this into your DAO and import from there.
  */
@Singleton
class CustomJdbcTypes @Inject() (
  protected val dbConfigProvider: DatabaseConfigProvider
) extends SlickEnumSupport with JdbcDateTypesUtc {

  protected lazy val dbConfig = dbConfigProvider.get[JdbcProfile]
  override lazy val profile = dbConfig.profile

  import profile.api._

  implicit val usercodeTypeMapper: JdbcType[Usercode] = MappedColumnType.base[Usercode, String](
    u => u.string,
    s => Usercode(s)
  )

  implicit val universityIdTypeMapper: JdbcType[UniversityID] = MappedColumnType.base[UniversityID, String](
    u => u.string,
    s => UniversityID(s)
  )

  implicit val jsonTypeMapper: JdbcType[JsValue] = MappedColumnType.base[JsValue, String](Json.stringify(_).replace("\\u0000", ""), Json.parse)

  implicit val symbolTypeMapper: JdbcType[Symbol] = MappedColumnType.base[Symbol, String](_.name, Symbol.apply)

  // Example enum
  implicit val colour: JdbcType[Colour] = mappedColumnTypeForEnum(Colour)
}
