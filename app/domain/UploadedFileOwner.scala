package domain

import enumeratum.{EnumEntry, PlayEnum}

import scala.collection.immutable

sealed trait UploadedFileOwner extends EnumEntry
object UploadedFileOwner extends PlayEnum[UploadedFileOwner] {
  case object Assessment extends UploadedFileOwner
  case object StudentAssessment extends UploadedFileOwner

  val values: IndexedSeq[UploadedFileOwner] = findValues
}
