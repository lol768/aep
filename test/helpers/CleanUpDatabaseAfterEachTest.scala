package helpers

import org.scalatest.{BeforeAndAfterEach, Suite}
import play.api.db.DBApi
import play.api.db.evolutions.Evolutions
import slick.basic.DatabaseConfig
import slick.jdbc.JdbcProfile

trait CleanUpDatabaseAfterEachTest extends BeforeAndAfterEach {
  this: Suite with HasApplicationGet =>

  private lazy val databaseApi: DBApi = get[DBApi]
  val dbConfig: DatabaseConfig[JdbcProfile]

  override def afterEach(): Unit = {
    removeAllTables("postgres")
    Evolutions.applyEvolutions(databaseApi.database("default"))
  }

  private val deleteTablesFunctionSql = """
    | CREATE OR REPLACE FUNCTION drop_all_tables(username IN VARCHAR) RETURNS void AS $$
    | DECLARE
    |     statements CURSOR FOR
    |         SELECT tablename FROM pg_tables
    |         WHERE tableowner = username AND schemaname = 'public';
    | BEGIN
    |     FOR stmt IN statements LOOP
    |         EXECUTE 'DROP TABLE ' || quote_ident(stmt.tablename) || ' CASCADE;';
    |     END LOOP;
    | EXCEPTION
    |     WHEN deadlock_detected THEN -- ignore!?
    | END;
    | $$ LANGUAGE plpgsql;
  """.stripMargin

  private def removeAllTables(username: String): Unit = {
    val conn = dbConfig.db.source.createConnection()
    try {
      val executeDeleteTablesFunctionSql = s"select drop_all_tables('$username');"
      val sqlToRun = Seq(deleteTablesFunctionSql, executeDeleteTablesFunctionSql)

      for (eachSql <- sqlToRun) {
        val s = conn.createStatement()
        s.execute(eachSql)
      }
    } finally {
      conn.close()
    }
  }
}
