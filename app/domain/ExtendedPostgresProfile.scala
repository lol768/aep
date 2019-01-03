package domain

import java.sql.{PreparedStatement, ResultSet}

import com.github.tminglei.slickpg._
import slick.basic.Capability
import slick.jdbc.JdbcCapabilities

trait ExtendedPostgresProfile
  extends ExPostgresProfile
    with PgArraySupport {

  // Add back `capabilities.insertOrUpdate` to enable native `upsert` support; for postgres 9.5+
  override protected def computeCapabilities: Set[Capability] =
    super.computeCapabilities + JdbcCapabilities.insertOrUpdate

  override val columnTypes = new JdbcTypes

  class JdbcTypes extends super.JdbcTypes {
    override val stringJdbcType: StringJdbcType = new StringJdbcType

    // CASE-373 Strip out null bytes before they get to Postgres
    class StringJdbcType extends super.StringJdbcType {
      private def stripNullBytes(v: String): String = v.replace("\u0000", "")

      override def setValue(v: String, p: PreparedStatement, idx: Int): Unit = super.setValue(stripNullBytes(v), p, idx)
      override def updateValue(v: String, r: ResultSet, idx: Int): Unit = super.updateValue(stripNullBytes(v), r, idx)
      override def valueToSQLLiteral(value: String): String = super.valueToSQLLiteral(stripNullBytes(value))
    }
  }

  override val api: API = new API {}

  trait API
    extends super.API
      with ArrayImplicits
}

object ExtendedPostgresProfile extends ExtendedPostgresProfile
