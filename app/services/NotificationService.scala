package services

import com.google.inject.ImplementedBy
import domain.messaging.Message
import domain.{Announcement, Assessment}
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
  def newMessage(message: Message)(implicit t: TimingContext): Future[ServiceResult[Activity]]
  def sendReminders(assessment: Assessment)(implicit t: TimingContext): Future[ServiceResult[Activity]]
}

@Singleton
class NotificationServiceImpl @Inject()(
  assessmentService: AssessmentService,
  studentAssessmentService: StudentAssessmentService,
  myWarwickService: MyWarwickService,
  scheduler: Scheduler,
  userLookupService: UserLookupService,
  configuration: Configuration,
)(implicit ec: ExecutionContext) extends NotificationService {

  private[this] lazy val domain = configuration.get[String]("domain")

  override def newAnnouncement(announcement: Announcement)(implicit t: TimingContext): Future[ServiceResult[Activity]] =
    ServiceResults.zip(
      assessmentService.get(announcement.assessment),
      studentAssessmentService.byAssessmentId(announcement.assessment),
    ).successFlatMapTo { case (assessment, students) =>
      val universityIds = students.map(_.studentId)

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

  override def newMessage(message: Message)(implicit t: TimingContext): Future[ServiceResult[Activity]] = {
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

  override def sendReminders(assessment: Assessment)(implicit t: TimingContext): Future[ServiceResult[Activity]] =
    studentAssessmentService.byAssessmentId(assessment.id).successFlatMapTo { students =>
      val universityIds = students.map(_.studentId)

      Future.successful(ServiceResults.fromTry(userLookupService.getUsers(universityIds))).successMapTo { users =>
        val usercodes = users.values.map(_.usercode.string).toSet

        val timezone: LenientZoneId = Right(JavaTime.timeZone)

        val activity = new Activity(
          usercodes.asJava,
          s"${assessment.paperCode}: Your alternative assessment for '${assessment.title}' is due today",
          controllers.routes.AssessmentController.view(assessment.id).absoluteURL(true, domain),
          (assessment.startTime, assessment.lastAllowedStartTime) match {
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
