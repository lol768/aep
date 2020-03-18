package domain

import java.time.{Duration, OffsetDateTime}
import java.util.UUID

import domain.Assessment.{AssessmentType, Brief}
import enumeratum.{EnumEntry, PlayEnum}
import play.api.libs.json.{Json, OFormat}

import scala.collection.immutable

case class Assessment(
  id: UUID = UUID.randomUUID(),
  code: String,
  startTime: OffsetDateTime,
  duration: Duration,
  assessmentType: AssessmentType,
  brief: Brief,
)

object Assessment {
  def tupled = (apply _).tupled

  sealed trait AssessmentType extends EnumEntry

  object AssessmentType extends PlayEnum[AssessmentType] {

    case object Moodle extends AssessmentType

    case object OnlineExams extends AssessmentType

    val values: immutable.IndexedSeq[AssessmentType] = findValues
  }

  case class Brief(
    text: Option[String],
    fileIds: Seq[UUID],
    url: Option[String],
  )

  object Brief {
    implicit val format: OFormat[Brief] = Json.format[Brief]
  }

}

