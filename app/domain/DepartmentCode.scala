package domain

import play.api.libs.json._
import play.api.mvc.PathBindable

case class Department(code: DepartmentCode, name: String)

class DepartmentCode(raw: String) {
  val string: String = raw.toUpperCase
  val lowerCase: String = raw.toLowerCase

  def unapply(arg: DepartmentCode): Option[String] = Some(arg.string)

  override def equals(obj: Any): Boolean = {
    obj match {
      case code: DepartmentCode => code.string == string
      case _ => false
    }
  }

  override def hashCode: Int = string.hashCode()

  override def toString: String = s"DepartmentCode($string)"
}

object DepartmentCode {
  def apply(raw: String) = new DepartmentCode(raw)

  implicit val reads: Reads[DepartmentCode] = JsPath.read[String].map(DepartmentCode.apply)
  implicit val writes: Writes[DepartmentCode] = (o: DepartmentCode) => JsString(o.string)
  implicit val format: Format[DepartmentCode] = Format(reads, writes)

  implicit def pathBinder(implicit initBinder: PathBindable[String]): PathBindable[DepartmentCode] = new PathBindable[DepartmentCode] {
    override def bind(key: String, value: String): Either[String, DepartmentCode] =
      initBinder.bind(key, value).map(key => apply(value))

    override def unbind(key: String, value: DepartmentCode): String =
      initBinder.unbind(key, value.unapply(value).get)
  }

}
