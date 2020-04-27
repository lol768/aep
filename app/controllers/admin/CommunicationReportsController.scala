package controllers.admin

import java.time.format.DateTimeFormatter

import controllers.BaseController
import domain.DepartmentCode
import domain.messaging.MessageSender
import javax.inject.{Inject, Singleton}
import play.api.Configuration
import play.api.mvc.{Action, AnyContent}
import services.messaging.MessageService
import services.refiners.ActionRefiners
import services.tabula.TabulaDepartmentService
import services.{AnnouncementService, SecurityService}
import system.Features
import warwick.sso.{GroupService, User, UserLookupService}

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class CommunicationReportsController @Inject()(
  security: SecurityService,
  userLookupService: UserLookupService,
  deptService: TabulaDepartmentService,
  groupService: GroupService,
  actionRefiners: ActionRefiners,
  announcementService: AnnouncementService,
  messageService: MessageService,
  features: Features,
  configuration: Configuration,
)(implicit ec: ExecutionContext) extends BaseController {

  import security._

  val csvDateTimeFormat: DateTimeFormatter = DateTimeFormatter.ofPattern(configuration.get[String]("app.csvDateTimeFormat"))

  def index(): Action[AnyContent] = GeneralDepartmentAdminAction.async { implicit request =>
    if (features.announcementsAndQueriesCsv) {
      deptService.getDepartments.successMap { allDepts =>
        val depts = request.user.map { user =>
          val groupsForUser = groupService.getGroupsForUser(user.usercode).get
          allDepts.filter(dept => actionRefiners.recursiveAdminGroupCheck(dept, allDepts, groupsForUser.map(_.name)))
        }.getOrElse(Seq.empty)
        Ok(views.html.admin.communicationReports.index(depts))
      }
    } else { Future.successful(NotFound(views.html.errors.notFound())) }
  }

   def announcementsCsv(departmentCode: String): Action[AnyContent] = SpecificDepartmentAdminAction(departmentCode).async { implicit request =>
     if (features.announcementsAndQueriesCsv) {
       announcementService.getByDepartmentCode(DepartmentCode(departmentCode)).successMap { assessmentWithAnnouncements =>
         val headerRow: Seq[String] = Seq(
           "Module code", "Paper code", "Section", "Title", "Assessment date/time", "Announcement sender", "Announcement", "Sent at"
         )
         val dataRows: Seq[Seq[String]] = assessmentWithAnnouncements.map { case (assessment, announcement) =>
           Seq(
             assessment.moduleCode,
             assessment.paperCode,
             assessment.section.getOrElse(""),
             assessment.title,
             assessment.startTime.map(csvDateTimeFormat.format).getOrElse(""),
             announcement.sender
               .flatMap(sender => userLookupService.getUser(sender).toOption)
               .filter(_.isFound)
               .map(formatUserForCsv)
               .getOrElse(""),
             announcement.text,
             csvDateTimeFormat.format(announcement.created),
           )
         }
         Ok.chunked(csvSource(headerRow +: dataRows)).as("text/csv")
       }
     } else { Future.successful(NotFound(views.html.errors.notFound())) }
  }

  def queriesCsv(departmentCode: String): Action[AnyContent] = SpecificDepartmentAdminAction(departmentCode).async { implicit request =>
    if (features.announcementsAndQueriesCsv) {
      messageService.findByDepartmentCode(DepartmentCode(departmentCode)).successMap{ assessmentWithMessages =>
        val headerRow: Seq[String] = Seq(
          "Module code",
          "Paper code",
          "Section",
          "Title",
          "Assessment date/time",
          "Message sender",
          "Message",
          "Sent at"
        ) ++ (if(features.twoWayMessages) Seq("Message recipient") else Seq.empty)

        val dataRows: Seq[Seq[String]] = assessmentWithMessages.map { case (assessment, message) =>
          val studentName = userLookupService.getUsers(Seq(message.student), includeDisabled = true).toOption
            .flatMap(_.headOption.map(_._2))
            .map(formatUserForCsv)
            .getOrElse("")

          val staffName = message.staffId.flatMap { usercode =>
            userLookupService.getUser(usercode).toOption.map(formatUserForCsv)
          }.getOrElse("Invigilator team")

          Seq(
            assessment.moduleCode,
            assessment.paperCode,
            assessment.section.getOrElse(""),
            assessment.title,
            assessment.startTime.map(csvDateTimeFormat.format).getOrElse(""),
            if (message.sender == MessageSender.Student) studentName else staffName,
            message.text,
            csvDateTimeFormat.format(message.created),
          ) ++ (if(features.twoWayMessages) Seq(if (message.sender == MessageSender.Student) staffName else studentName) else Seq.empty)
        }
        Ok.chunked(csvSource(headerRow +: dataRows)).as("text/csv")
      }
    } else { Future.successful(NotFound(views.html.errors.notFound())) }
  }


  private def formatUserForCsv(user: User): String = {
    Seq(
      user.name.full.orElse(Some(user.usercode.string)),
      user.email.map(e => s"<$e>")
    ).flatten.mkString(" ")
  }
}


