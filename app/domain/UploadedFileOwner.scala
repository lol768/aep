package domain

import enumeratum.{EnumEntry, PlayEnum}

import scala.collection.immutable

sealed trait UploadedFileOwner extends EnumEntry
object UploadedFileOwner extends PlayEnum[UploadedFileOwner] {
  // Add owner entity types here

  val values: immutable.IndexedSeq[UploadedFileOwner] = findValues
}
