package services

import java.time.OffsetDateTime

import com.google.inject.ImplementedBy
import domain.messaging.Message
import domain.{Announcement, Assessment, Sitting}
import helpers.LenientTimezoneNameParsing._
import javax.inject.{Inject, Singleton}
import org.quartz.Scheduler
import play.api.Configuration
import uk.ac.warwick.util.mywarwick.MyWarwickService
import uk.ac.warwick.util.mywarwick.model.request.Activity
import warwick.core.helpers.ServiceResults.Implicits._
import warwick.core.helpers.ServiceResults.ServiceResult
import warwick.core.helpers.{JavaTime, ServiceResults}
import warwick.core.timing.TimingContext
import warwick.sso.UserLookupService

import scala.concurrent.{ExecutionContext, Future}
import scala.jdk.CollectionConverters._

@ImplementedBy(classOf[NotificationServiceImpl])
trait NotificationService {
  def newAnnouncement(announcement: Announcement)(implicit t: TimingContext): Future[ServiceResult[Activity]]
  def newMessageFromStudent(message: Message)(implicit t: TimingContext): Future[ServiceResult[Activity]]
  def newMessageFromInvigilator(message: Message)(implicit t: TimingContext): Future[ServiceResult[Activity]]
  def sendReminders(assessment: Assessment)(implicit t: TimingContext): Future[ServiceResult[Seq[Activity]]]
}

@Singleton
class NotificationServiceImpl @Inject()(
  assessmentService: AssessmentService,
  studentAssessmentService: StudentAssessmentService,
  myWarwickService: MyWarwickService,
  scheduler: Scheduler,
  userLookupService: UserLookupService,
  configuration: Configuration,
  timingInfo: TimingInfoService,
)(implicit ec: ExecutionContext) extends NotificationService {

  private[this] lazy val domain = configuration.get[String]("domain")

  override def newAnnouncement(announcement: Announcement)(implicit t: TimingContext): Future[ServiceResult[Activity]] =
    ServiceResults.zip(
      assessmentService.get(announcement.assessment),
      studentAssessmentService.byAssessmentId(announcement.assessment),
    ).successFlatMapTo { case (assessment, students) =>
      val universityIds = students.filter(_.startTime.nonEmpty).map(_.studentId)

      Future.successful(ServiceResults.fromTry(userLookupService.getUsers(universityIds))).successMapTo { users =>
        val usercodes = users.values.map(_.usercode.string).toSet

        val activity = new Activity(
          usercodes.asJava,
          s"${assessment.paperCode} ${assessment.title}: Announcement",
          controllers.routes.AssessmentController.view(assessment.id).absoluteURL(true, domain),
          announcement.text,
          "assessment-announcement"
        )

        if (usercodes.nonEmpty) {
          myWarwickService.queueNotification(activity, scheduler)
        }

        activity
      }
    }

  override def newMessageFromStudent(message: Message)(implicit t: TimingContext): Future[ServiceResult[Activity]] = {
    assessmentService.get(message.assessmentId).successMapTo { assessment =>
      val usercodes = assessment.invigilators.map(_.string)

      val activity = new Activity(
        usercodes.asJava,
        s"${assessment.paperCode}: Query from student",
        controllers.invigilation.routes.InvigilatorAssessmentController.view(assessment.id).absoluteURL(true, domain),
        message.text,
        "assessment-query"
      )

      if (usercodes.nonEmpty) {
        myWarwickService.queueNotification(activity, scheduler)
      }

      activity
    }
  }

  override def newMessageFromInvigilator(message: Message)(implicit t: TimingContext): Future[ServiceResult[Activity]] = {
    ServiceResults.zip(
      Future.successful(ServiceResults.fromTry(userLookupService.getUsers(Seq(message.student)))),
      assessmentService.get(message.assessmentId),
    ).successMapTo { case (users, assessment) =>
      val usercodes = users.get(message.student).map(_.usercode.string).toSet

      val activity = new Activity(
        usercodes.asJava,
        s"${assessment.paperCode}: Reply from invigilator",
        controllers.routes.MessageController.showForm(assessment.id).absoluteURL(true, domain),
        message.text,
        "assessment-query"
      )

      if (usercodes.nonEmpty) {
        myWarwickService.queueNotification(activity, scheduler)
      }

      activity
    }
  }

  override def sendReminders(assessment: Assessment)(implicit t: TimingContext): Future[ServiceResult[Seq[Activity]]] =
    studentAssessmentService.sittingsByAssessmentId(assessment.id).successFlatMapTo { sittings =>
      ServiceResults.futureSequence{
        val universityIds = sittings.map(_.studentAssessment.studentId)
        val sittingsByTime = sittings.groupBy(_.lastAllowedStartTimeForStudent(timingInfo.lateSubmissionPeriod)).toSeq

        sittingsByTime.map { case (lastStartTimeOption: Option[OffsetDateTime], seqOfSittings: Seq[Sitting]) =>
          Future.successful(ServiceResults.fromTry(userLookupService.getUsers(seqOfSittings.map(_.studentAssessment.studentId))))
            .successMapTo { users =>
              val usercodes = users.values.map(_.usercode.string).toSet

              val timezone: LenientZoneId = Right(JavaTime.timeZone)

              val activity = new Activity(
                usercodes.asJava,
                s"${assessment.paperCode}: Your alternative assessment for '${assessment.title}' is due today",
                controllers.routes.AssessmentController.view(assessment.id).absoluteURL(true, domain),
                (assessment.startTime, lastStartTimeOption) match {
                  // Special case where this crosses the DST boundary
                  case (Some(startTime), Some(lastAllowedStartTime)) if timezone.timezoneAbbr(startTime) != timezone.timezoneAbbr(lastAllowedStartTime) =>
                    s"You can start this assessment between ${JavaTime.Relative(startTime)} (${timezone.timezoneAbbr(startTime)}) and ${JavaTime.Relative(lastAllowedStartTime)} (${timezone.timezoneAbbr(lastAllowedStartTime)})."

                  case (Some(startTime), Some(lastAllowedStartTime)) =>
                    s"You can start this assessment between ${JavaTime.Relative(startTime)} and ${JavaTime.Relative(lastAllowedStartTime)} (${timezone.timezoneAbbr(lastAllowedStartTime)})."

                  case (Some(startTime), _) =>
                    s"You can start this assessment at ${JavaTime.Relative(startTime)} (${timezone.timezoneAbbr(startTime)})."

                  // Should be filtered before it gets here (how can it be today without a start time?), but just ignore
                  case _ => ""
                },
                "assessment-reminder"
              )

              if (usercodes.nonEmpty) {
                myWarwickService.queueNotification(activity, scheduler)
              }

              activity
            }
        }
      }
    }

}
