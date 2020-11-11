package domain

import java.util.UUID

import system.Features

case class Declarations(
  studentAssessmentId: UUID,
  acceptsAuthorship: Boolean = false,
  selfDeclaredRA: Option[Boolean] = None, // Will be None if RA is imported because the form is not shown. That way we have a record of whether the import was on or not when the declaration was signed.
  completedRA: Boolean = false
) {
  def acceptable(features: Features): Boolean =
    acceptsAuthorship && (completedRA || features.importStudentExtraTime)
}
