package services.job

import java.io.InputStream
import java.time.format.DateTimeFormatter
import java.util.UUID
import java.util.zip.Deflater

import akka.Done
import com.github.tototoshi.csv.CSVWriter
import com.google.common.io.{ByteSource, Files}
import com.typesafe.config.ConfigFactory
import domain.Assessment.Platform
import domain.{Assessment, Sitting, UploadedFileOwner}
import helpers.ServiceResultUtils.traverseSerial
import helpers.StringUtils._
import javax.inject.Inject
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream.UnicodeExtraFieldPolicy
import org.apache.commons.compress.archivers.zip.{ZipArchiveEntry, ZipArchiveOutputStream}
import org.apache.commons.io.FilenameUtils
import org.quartz.{JobExecutionContext, Scheduler}
import play.api.libs.Files.TemporaryFileCreator
import services.{AssessmentService, StudentAssessmentService, UploadedFileService}
import warwick.core.helpers.ServiceResults.Implicits._
import warwick.core.helpers.{JavaTime, ServiceResults}
import warwick.core.system.AuditLogContext
import warwick.fileuploads.UploadedFileSave
import warwick.objectstore.ObjectStorageService
import warwick.sso.Usercode

import scala.concurrent.{ExecutionContext, Future}

class GenerateAssessmentZipJob @Inject()(
  scheduler: Scheduler,
  assessmentService: AssessmentService,
  studentAssessmentService: StudentAssessmentService,
  uploadedFileService: UploadedFileService,
  objectStorageService: ObjectStorageService,
  temporaryFileCreator: TemporaryFileCreator,
)(implicit ec: ExecutionContext) extends AbstractJob(scheduler) {

  override def run(implicit context: JobExecutionContext, audit: AuditLogContext): Future[JobResult] = {
    val assessmentId = UUID.fromString(context.getMergedJobDataMap.getString("id"))
    val usercode = Usercode(context.getMergedJobDataMap.getString("usercode"))
    val generationStarted = JavaTime.offsetDateTime

    logger.info(s"[$assessmentId] Generating a zip file of submissions")

    ServiceResults.zip(
      assessmentService.get(assessmentId),
      studentAssessmentService.sittingsByAssessmentId(assessmentId),
    ).successFlatMapTo { case (assessment, sittings) =>
      val uploadedFiles = sittings.flatMap(_.studentAssessment.uploadedFiles)
      val totalFileSize = uploadedFiles.map(_.contentLength).sum

      // Don't try and create a zip bigger than 4gb
      if (totalFileSize > 4L * 1024 * 1024 * 1024) {
        Future.successful(ServiceResults.error("Uploaded files sum to greater than 4gb"))
      } else {
        logger.info(s"[$assessmentId] Zipping up ${uploadedFiles.size} files (total size ${helpers.humanReadableSize(totalFileSize)})")

        val tempFile = temporaryFileCreator.create(assessmentId.toString, ".zip")

        // Write items to the zip file
        val zip = new ZipArchiveOutputStream(tempFile)
        zip.setLevel(Deflater.BEST_COMPRESSION)

        // HFC-70 Windows compatible, but fixes filenames in good apps like 7-zip
        zip.setCreateUnicodeExtraFields(UnicodeExtraFieldPolicy.NOT_ENCODEABLE)

        // Write a CSV of the assessment data
        zip.putArchiveEntry(new ZipArchiveEntry("submissions.csv"))
        val csv = CSVWriter.open(zip)
        csv.writeAll(GenerateAssessmentZipJob.submissionsCSV(assessment, sittings))
        zip.closeArchiveEntry()

        traverseSerial(sittings.flatMap(s => s.studentAssessment.uploadedFiles.zipWithIndex.map(s -> _))) { case (sitting, (file, index)) =>
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

            val fileName = s"${sitting.studentAssessment.studentId.string} - ${trunc(file.fileName, 100)}"
            zip.putArchiveEntry(new ZipArchiveEntry(fileName))
            source.copyTo(zip)
            zip.closeArchiveEntry()
            logger.info(f"[$assessmentId] [${(index * 100) / uploadedFiles.size}%03d] Wrote zip entry for $fileName")

            ServiceResults.success(Done)
          }
        }.successMapTo { _ =>
          zip.close()
          tempFile
        }.successFlatMapTo { _ =>
          logger.info(s"[$assessmentId] Storing zip file of size ${helpers.humanReadableSize(tempFile.length())} in object storage")
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
            logger.info(s"[$assessmentId] Generated uploaded file for assessment: $file")
            temporaryFileCreator.delete(tempFile)
            file
          }
        }
      }
    }.map { result =>
      result.left.foreach { errors =>
        val message = errors.map(_.message).mkString("; ")
        logger.error(s"Errors generating zip: $message")
        errors.find(_.cause.nonEmpty).flatMap(_.cause).map(throwable =>
          throw new UnsupportedOperationException(throwable)
        ).getOrElse(
          throw new UnsupportedOperationException(message)
        )
      }
      JobResult.quiet
    }
  }

  override def getDescription(context: JobExecutionContext): String = "Generate assessment zip"

}

object GenerateAssessmentZipJob  {
  lazy private val csvDateTimeFormat: DateTimeFormatter = DateTimeFormatter.ofPattern(ConfigFactory.load().getString("app.csvDateTimeFormat"))
  /**
    * Writes CSV information about sittings to the output stream, *without* closing it.
    */
  def submissionsCSV(assessment: Assessment, sittings: Seq[Sitting]): Seq[Seq[String]] = {
    // Header
    val header: Seq[String] =
      Seq(
        "University ID",
        "State",
        "Signed statement of authorship",
        "Reasonable adjustments declared",
        "Start time",
      ) ++ (
        if (assessment.platform.contains(Platform.OnlineExams))
          Seq(
            "Finalise time",
            "Submission time",
            "Files"
          )
        else Seq.empty
      )

    val rows: Seq[Seq[String]] = sittings.map { sitting =>
      Seq(
        sitting.studentAssessment.studentId.string,
        sitting.getSummaryStatusLabel.getOrElse(""),
        sitting.declarations.acceptsAuthorship.toString,
        if (sitting.declarations.completedRA) sitting.declarations.selfDeclaredRA.toString else "",
        sitting.studentAssessment.startTime.map(csvDateTimeFormat.format).getOrElse(""),
      ) ++ (
        if (assessment.platform.contains(Platform.OnlineExams))
          Seq(
            sitting.studentAssessment.explicitFinaliseTime.map(csvDateTimeFormat.format).getOrElse(""),
            sitting.studentAssessment.submissionTime.map(csvDateTimeFormat.format).getOrElse(""),
            sitting.studentAssessment.uploadedFiles.map(_.fileName).mkString(", ")
          )
        else Seq.empty
      )
    }

    header +: rows
  }
}
