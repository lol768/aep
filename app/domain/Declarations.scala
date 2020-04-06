package domain

import java.util.UUID

case class Declarations(
  id: UUID,
  acceptsAuthorship: Boolean = false,
  selfDeclaredRA: Boolean = false,
  completedRA: Boolean = false
) {
  def acceptable: Boolean =
    acceptsAuthorship && completedRA
}
