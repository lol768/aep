package domain

import domain.Assessment.{Platform, AssessmentType}
import domain.dao.AssessmentsTables.StoredBrief
import enumeratum.SlickEnumSupport
import javax.inject.{Inject, Singleton}
import play.api.db.slick.DatabaseConfigProvider
import play.api.libs.json.{Format, JsValue, Json, OFormat}
import slick.basic.DatabaseConfig
import slick.jdbc.{JdbcProfile, JdbcType}
import warwick.sso.{GroupName, UniversityID, Usercode}

import scala.reflect.ClassTag

/**
  * Defines custom types that Slick can use to bind things directly to a database column.
  * This is a class rather than an object because the types need access to the current database
  * profile, which can vary. Inject this into your DAO and import from there.
  */
@Singleton
abstract class CustomJdbcTypes[Profile <: JdbcProfile] @Inject() (
  protected val dbConfigProvider: DatabaseConfigProvider
) extends SlickEnumSupport {

  protected lazy val dbConfig: DatabaseConfig[Profile] = dbConfigProvider.get[Profile]
  override lazy val profile: Profile = dbConfig.profile

  import profile.api._

  implicit val usercodeTypeMapper: JdbcType[Usercode] = MappedColumnType.base[Usercode, String](
    _.string,
    Usercode
  )

  implicit val universityIdTypeMapper: JdbcType[UniversityID] = MappedColumnType.base[UniversityID, String](
    _.string,
    UniversityID
  )

  implicit val groupNameTypeMapper: JdbcType[GroupName] = MappedColumnType.base[GroupName, String](
    _.string,
    GroupName
  )

  implicit val symbolTypeMapper: JdbcType[Symbol] = MappedColumnType.base[Symbol, String](_.name, Symbol.apply)

  // Enum[] mappings
  implicit val databaseOperationTypeMapper: JdbcType[DatabaseOperation] = mappedColumnTypeForEnum(DatabaseOperation)
  implicit val uploadedFileOwnerMapper: JdbcType[UploadedFileOwner] = mappedColumnTypeForEnum(UploadedFileOwner)
  implicit val platformTypeMapper: JdbcType[Platform] = mappedColumnTypeForEnum(Platform)
  implicit val assessmentTypeTypeMapper: JdbcType[AssessmentType] = mappedColumnTypeForEnum(AssessmentType)
  
}

class OracleCustomJdbcTypes @Inject() (
  dbConfigProvider: DatabaseConfigProvider
) extends CustomJdbcTypes[JdbcProfile](dbConfigProvider)

class PostgresCustomJdbcTypes @Inject() (
  dbConfigProvider: DatabaseConfigProvider
) extends CustomJdbcTypes[ExtendedPostgresProfile](dbConfigProvider) {

  import profile.api._

  /** Maps a column to a specific class via its implicit JSON conversions */
  private def jsonTypeMapper[T : ClassTag](implicit ev: Format[T]): JdbcType[T] = MappedColumnType.base[T, JsValue](
    Json.toJson(_),
    _.as[T]
  )

  // For explicitly passing an OFormat
  private def jsonTypeMapper[T : ClassTag](format: OFormat[T]): JdbcType[T] = jsonTypeMapper[T](implicitly[ClassTag[T]], format)

  // JSON types

  implicit val listOfStringTuplesMapper: JdbcType[Seq[(String, String)]] = jsonTypeMapper[Seq[(String, String)]]
  implicit val listOfStringsMapper: JdbcType[Seq[String]] = jsonTypeMapper[Seq[String]]
  implicit val storedBriefMapper: JdbcType[StoredBrief] = jsonTypeMapper[StoredBrief]
}
