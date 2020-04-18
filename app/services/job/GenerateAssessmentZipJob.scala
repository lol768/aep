package services.job

import java.io.InputStream
import java.time.format.DateTimeFormatter
import java.util.UUID
import java.util.zip.Deflater

import akka.Done
import com.github.tototoshi.csv.CSVWriter
import com.google.common.io.{ByteSource, Files}
import domain.UploadedFileOwner
import helpers.ServiceResultUtils.traverseSerial
import helpers.StringUtils._
import javax.inject.Inject
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream.UnicodeExtraFieldPolicy
import org.apache.commons.compress.archivers.zip.{ZipArchiveEntry, ZipArchiveOutputStream}
import org.apache.commons.io.FilenameUtils
import org.quartz.{JobExecutionContext, Scheduler}
import play.api.libs.Files.TemporaryFileCreator
import services.{StudentAssessmentService, UploadedFileService}
import warwick.core.helpers.ServiceResults.Implicits._
import warwick.core.helpers.{JavaTime, ServiceResults}
import warwick.core.system.AuditLogContext
import warwick.fileuploads.UploadedFileSave
import warwick.objectstore.ObjectStorageService
import warwick.sso.Usercode

import scala.concurrent.{ExecutionContext, Future}

class GenerateAssessmentZipJob @Inject()(
  scheduler: Scheduler,
  studentAssessmentService: StudentAssessmentService,
  uploadedFileService: UploadedFileService,
  objectStorageService: ObjectStorageService,
  temporaryFileCreator: TemporaryFileCreator,
)(implicit ec: ExecutionContext) extends AbstractJob(scheduler) {

  val csvDateTimeFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")

  override def run(implicit context: JobExecutionContext, audit: AuditLogContext): Future[JobResult] = {
    val assessmentId = UUID.fromString(context.getMergedJobDataMap.getString("id"))
    val usercode = Usercode(context.getMergedJobDataMap.getString("usercode"))
    val generationStarted = JavaTime.offsetDateTime

    studentAssessmentService.sittingsByAssessmentId(assessmentId).successFlatMapTo { sittings =>
      // Don't try and create a zip bigger than 4gb
      if (sittings.flatMap(_.studentAssessment.uploadedFiles).map(_.contentLength).sum > 4L * 1024 * 1024 * 1024) {
        Future.successful(ServiceResults.error("Uploaded files sum to greater than 4gb"))
      } else {
        val tempFile = temporaryFileCreator.create(assessmentId.toString, ".zip")

        // Write items to the zip file
        val zip = new ZipArchiveOutputStream(tempFile)
        zip.setLevel(Deflater.BEST_COMPRESSION)

        // HFC-70 Windows compatible, but fixes filenames in good apps like 7-zip
        zip.setCreateUnicodeExtraFields(UnicodeExtraFieldPolicy.NOT_ENCODEABLE)

        // Write a CSV of the assessment data
        zip.putArchiveEntry(new ZipArchiveEntry("submissions.csv"))
        val csv = CSVWriter.open(zip)
        // Header
        csv.writeRow(Seq("University ID", "State", "Signed statement of authorship", "Reasonable adjustments declared", "Start time", "Finalise time", "Submission time", "Files"))
        csv.writeAll(sittings.map { sitting =>
          Seq(
            sitting.studentAssessment.studentId.string,
            sitting.getSummaryStatusLabel.getOrElse(""),
            sitting.declarations.acceptsAuthorship.toString,
            if (sitting.declarations.completedRA) sitting.declarations.selfDeclaredRA.toString else "",
            sitting.studentAssessment.startTime.map(csvDateTimeFormat.format).getOrElse(""),
            sitting.studentAssessment.explicitFinaliseTime.map(csvDateTimeFormat.format).getOrElse(""),
            sitting.studentAssessment.submissionTime.map(csvDateTimeFormat.format).getOrElse(""),
            sitting.studentAssessment.uploadedFiles.map(_.fileName).mkString(", ")
          )
        })
        zip.closeArchiveEntry()

        traverseSerial(sittings.flatMap(s => s.studentAssessment.uploadedFiles.map(s -> _))) { case (sitting, file) =>
          val source: ByteSource = new ByteSource {
            override def openStream(): InputStream = objectStorageService.fetch(file.id.toString).orNull
          }

          Future {
            def trunc(name: String, limit: Int): String =
              if (name.length() <= limit) name
              else {
                val ext = FilenameUtils.getExtension(name)
                if (ext.hasText) FilenameUtils.getBaseName(name).safeSubstring(0, limit) + "." + ext
                else name.substring(0, limit)
              }

            zip.putArchiveEntry(new ZipArchiveEntry(s"${sitting.studentAssessment.studentId.string} - ${trunc(file.fileName, 100)}"))
            source.copyTo(zip)
            zip.closeArchiveEntry()

            ServiceResults.success(Done)
          }
        }.successMapTo { _ =>
          zip.close()
          tempFile
        }.successFlatMapTo { _ =>
          uploadedFileService.store(
            Files.asByteSource(tempFile),
            UploadedFileSave(
              "submissions.zip",
              tempFile.length(),
              "application/zip",
              generationStarted
            ),
            assessmentId,
            UploadedFileOwner.AssessmentSubmissions
          )(audit.copy(usercode = Some(usercode))).successMapTo { file =>
            logger.info(s"Generated uploaded file for assessment: $file")
            temporaryFileCreator.delete(tempFile)
            file
          }
        }
      }
    }.map { result =>
      if (result.isLeft) {
        logger.error(s"Errors generating zip: ${result.left.getOrElse(Nil)}")
      }

      JobResult.quiet
    }
  }

  override def getDescription(context: JobExecutionContext): String = "Generate assessment zip"

}
