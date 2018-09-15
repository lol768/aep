package domain

import enumeratum.{EnumEntry, PlayEnum}

// Example enum
sealed trait Colour extends EnumEntry

object Colour extends PlayEnum[Colour] {
  case object Red extends Colour
  case object Green extends Colour
  case object Blue extends Colour
  case object Yellow extends Colour

  override val values = findValues
}
