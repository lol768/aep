package domain

import java.time.{Duration, LocalDate, LocalDateTime, OffsetDateTime, OffsetTime, ZonedDateTime}

import com.github.tminglei.slickpg._
import com.github.tminglei.slickpg.date.PgDateExtensions
import slick.ast.Library.SqlFunction
import slick.ast.{LiteralNode, Node}
import slick.basic.Capability
import slick.jdbc.{JdbcCapabilities, JdbcType}
import slick.lifted.OptionMapperDSL
import warwick.slick.jdbctypes.pg.FixedPgLocalDateTypeSupport
import warwick.slick.jdbctypes.{CustomJdbcDateTypesSupport, CustomStringJdbcTypeSupport}

trait ExtendedPostgresProfile
  extends ExPostgresProfile
    with PgArraySupport
    with PgDate2Support
    with PgJsonSupport
    with PgPlayJsonSupport
    with CustomStringJdbcTypeSupport
    with CustomJdbcDateTypesSupport
    with FixedPgLocalDateTypeSupport {

  // Add back `capabilities.insertOrUpdate` to enable native `upsert` support; for postgres 9.5+
  override protected def computeCapabilities: Set[Capability] =
    super.computeCapabilities + JdbcCapabilities.insertOrUpdate

  override val pgjson = "jsonb"

  override val columnTypes: ExtendedJdbcTypes = new ExtendedJdbcTypes

  class ExtendedJdbcTypes extends super.JdbcTypes
    with NullByteStrippingStringType
    with JdbcDateTypesUtc
    with FixedLocalDateType

  override val api: ExtendedAPI = new ExtendedAPI {}

  trait ExtendedAPI
    extends super.API
      with ArrayImplicits
      with JsonImplicits
      with DateTimeImplicits {

    // We override these Slick types in columnTypes to UTC versions,
    // then DateTimeImplicits overrides those to its non-UTC versions,
    // then we override those back again here 🙄
    override implicit val date2TzTimestampTypeMapper: JdbcType[OffsetDateTime] = columnTypes.offsetDateTimeType
    override implicit val date2TzTimestamp1TypeMapper: JdbcType[ZonedDateTime] = columnTypes.zonedDateType

    def stringToArray[R](column: Rep[String], separator: String, nullString: Option[String] = None)(
      implicit om: OptionMapperDSL.arg[String, String]#to[List[String], R]
    ): Rep[R] = {
      om.column(
        new SqlFunction("string_to_array"),
        Seq[Node](
          column.toNode,
          LiteralNode(separator),
        ) ++ nullString.toSeq.map[Node](LiteralNode(_)): _*
      )
    }
  }


}

object ExtendedPostgresProfile extends ExtendedPostgresProfile
