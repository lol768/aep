package domain

import java.time.Duration

import com.github.tminglei.slickpg._
import slick.basic.Capability
import slick.jdbc.{JdbcCapabilities, JdbcType}
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
      with JsonImplicits {
    implicit val durationTypeMapper: JdbcType[Duration] = new GenericDateJdbcType[Duration]("interval", java.sql.Types.OTHER)
  }

}

object ExtendedPostgresProfile extends ExtendedPostgresProfile
