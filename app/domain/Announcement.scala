package domain

import java.util.UUID

case class Announcement(
  id: UUID = UUID.randomUUID(),
  assessment: UUID,
  text: String,
)
