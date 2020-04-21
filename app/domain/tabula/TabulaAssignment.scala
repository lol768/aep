package domain.tabula

import java.util.UUID

import uk.ac.warwick.util.termdates.AcademicYear

case class TabulaAssignment(
  id: UUID,
  name: String,
  academicYear: AcademicYear
)
