package domain

import java.time.OffsetDateTime
import java.util.UUID

import play.api.libs.json.JsValue
import warwick.core.helpers.JavaTime
import warwick.sso.Usercode

case class AuditEvent(
  id: UUID = UUID.randomUUID(),
  date: OffsetDateTime = JavaTime.offsetDateTime,
  operation: Symbol,
  usercode: Option[Usercode],
  data: JsValue,
  targetId: String,
  targetType: Symbol
)

object AuditEvent {
  def tupled = (apply _).tupled

  object Target {
    val UploadedFile = Symbol("UploadedFile")
    val Assessment = Symbol("Assessment")
    val StudentAssessment = Symbol("StudentAssessment")
    var Declarations = Symbol("Declarations")
  }

  object Operation {
    object UploadedFile {
      val Save = Symbol("UploadedFileStore")
      val Delete = Symbol("UploadedFileDelete")
    }
    object Assessment {
      val MakeAnnouncement = Symbol("MakeAnnouncement")
    }
    object StudentAssessment {
      val StartAssessment = Symbol("StartAssessment")
      val AttachFilesToAssessment = Symbol("AttachFilesToAssessment")
      val DeleteAttachedAssessmentFile = Symbol("DeleteAttachedAssessmentFile")
      val FinishAssessment = Symbol("FinishAssessment")
      val MakeDeclarations = Symbol("MakeDeclarations")
    }
  }
}
