package domain

import enumeratum.{Enum, EnumEntry}

sealed trait DatabaseOperation extends EnumEntry

object DatabaseOperation extends Enum[DatabaseOperation] {
  case object Insert extends DatabaseOperation
  case object Update extends DatabaseOperation
  case object Delete extends DatabaseOperation

  override val values: IndexedSeq[DatabaseOperation] = findValues
}
