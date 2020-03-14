package system.routes

import play.api.mvc.PathBindable
import warwick.sso.{UniversityID, Usercode}

/**
  * Added to the routesImport setting in build.sbt so that these are in scope to the
  * routes compiler, allowing you to use these types as types of path parameter.
  */
object PathBindables {

  implicit val universityIdBindable: PathBindable[UniversityID] = new PathBindable[UniversityID] {
    override def bind(key: String, value: String): Either[String, UniversityID] = Right(UniversityID(value))
    override def unbind(key: String, value: UniversityID): String = value.string
  }

  implicit val usercodeBindable: PathBindable[Usercode] = new PathBindable[Usercode] {
    override def bind(key: String, value: String): Either[String, Usercode] = Right(Usercode(value))
    override def unbind(key: String, value: Usercode): String = value.string
  }

}
