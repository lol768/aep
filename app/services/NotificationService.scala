package services

import com.google.inject.ImplementedBy
import domain.Announcement
import helpers.StringUtils._
import javax.inject.{Inject, Singleton}
import org.quartz.Scheduler
import play.api.Configuration
import play.api.mvc.Call
import uk.ac.warwick.util.mywarwick.MyWarwickService
import uk.ac.warwick.util.mywarwick.model.request.Activity
import warwick.core.helpers.ServiceResults
import warwick.core.helpers.ServiceResults.Implicits._
import warwick.core.helpers.ServiceResults.ServiceResult
import warwick.core.timing.TimingContext
import warwick.sso.UserLookupService

import scala.concurrent.{ExecutionContext, Future}
import scala.jdk.CollectionConverters._

@ImplementedBy(classOf[NotificationServiceImpl])
trait NotificationService {
  def newAnnouncement(announcement: Announcement)(implicit t: TimingContext): Future[ServiceResult[Activity]]
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

        myWarwickService.queueNotification(
          activity,
          scheduler
        )

        activity
      }
    }

}
