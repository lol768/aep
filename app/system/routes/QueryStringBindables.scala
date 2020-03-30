package system.routes

import java.net.URI

import play.api.mvc.{Call, QueryStringBindable}

object QueryStringBindables {

  implicit val callQueryStringBindable: QueryStringBindable[Option[Call]] =
    QueryStringBindable.bindableString.transform(
      str => {
        try {
          if(!(new URI(str)).isAbsolute)  {
            Some(Call("GET", str))
          } else {
            None
          }
        } catch {
          case _: Throwable => None
        }
      },
      _.map(_.path()).getOrElse("")
    )

}
