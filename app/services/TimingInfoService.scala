package services

import java.time.Duration
import com.google.inject.ImplementedBy
import javax.inject.{Inject, Singleton}
import system.Features

@ImplementedBy(classOf[TimingInfoServiceImpl])
trait TimingInfoService {
  def lateSubmissionPeriod: Duration
}

@Singleton
class TimingInfoServiceImpl @Inject()(
  features: Features
) extends TimingInfoService {

  // Students are allowed 2 extra hours after the official finish time of the exam
  // for them to make submissions. Anything submitted during this period should be
  // marked as LATE though.
  // If we have the feature flag active to import extra time adjustments from SITS,
  // the late period should be removed entirely.
  override def lateSubmissionPeriod: Duration =
    if (features.importStudentExtraTime) Duration.ofHours(0L)
    else Duration.ofHours(2L)

}
