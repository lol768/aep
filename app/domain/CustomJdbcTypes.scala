package domain

import domain.Assessment.AssessmentType
import domain.dao.AssessmentsTables.StoredBrief
import enumeratum.SlickEnumSupport
import javax.inject.{Inject, Singleton}
import play.api.db.slick.DatabaseConfigProvider
import play.api.libs.json.{Format, JsValue, Json, OFormat}
import slick.jdbc.{JdbcProfile, JdbcType}
import warwick.sso.{GroupName, UniversityID, Usercode}

import scala.reflect.ClassTag

/**
  * Defines custom types that Slick can use to bind things directly to a database column.
  * This is a class rather than an object because the types need access to the current database
  * profile, which can vary. Inject this into your DAO and import from there.
  */
@Singleton
class CustomJdbcTypes @Inject()(
  protected val dbConfigProvider: DatabaseConfigProvider
) extends SlickEnumSupport {

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

  implicit val groupNameTypeMapper: JdbcType[GroupName] = MappedColumnType.base[GroupName, String](
    g => g.string,
    s => GroupName(s)
  )

  implicit val jsonTypeMapper: JdbcType[JsValue] = MappedColumnType.base[JsValue, String](Json.stringify(_).replace("\\u0000", ""), Json.parse)

  implicit val symbolTypeMapper: JdbcType[Symbol] = MappedColumnType.base[Symbol, String](_.name, Symbol.apply)

  // Enum mappings
  implicit lazy val databaseOperationTypeMapper: JdbcType[DatabaseOperation] = mappedColumnTypeForEnum(DatabaseOperation)
  implicit lazy val uploadedFileOwnerMapper: JdbcType[UploadedFileOwner] = mappedColumnTypeForEnum(UploadedFileOwner)
  implicit val assessmentTypeTypeMapper: JdbcType[AssessmentType] = mappedColumnTypeForEnum(AssessmentType)

  /** Maps a column to a specific class via its implicit JSON conversions */
  private def jsonTypeMapper[T: ClassTag](implicit ev: Format[T]): JdbcType[T] = MappedColumnType.base[T, JsValue](
    Json.toJson(_),
    _.as[T]
  )

  // For explicitly passing an OFormat
  private def jsonTypeMapper[T: ClassTag](format: OFormat[T]): JdbcType[T] = jsonTypeMapper[T](implicitly[ClassTag[T]], format)

  // JSON types
  implicit val storedBriefMapper: JdbcType[StoredBrief] = jsonTypeMapper[StoredBrief]
}
