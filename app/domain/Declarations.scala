package domain

import java.util.UUID

import system.Features

case class Declarations(
  studentAssessmentId: UUID,
  acceptsAuthorship: Boolean = false,
  selfDeclaredRA: Option[Boolean] = None,
  completedRA: Boolean = false
) {
  def acceptable(features: Features): Boolean =
    acceptsAuthorship && (completedRA || features.importStudentExtraTime)
}
