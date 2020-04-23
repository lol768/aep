package services.healthcheck

import java.nio.charset.StandardCharsets

import akka.actor.ActorSystem
import com.google.common.io.CharSource
import javax.inject.{Inject, Named, Singleton}
import org.jclouds.blobstore.domain.Blob
import services.healthcheck.EncryptedObjectStorageHealthCheck._
import uk.ac.warwick.util.service.ServiceHealthcheck.Status
import uk.ac.warwick.util.service.ServiceHealthcheck.Status._
import uk.ac.warwick.util.service.{ServiceHealthcheck, ServiceHealthcheckProvider}
import warwick.core.Logging
import warwick.core.helpers.JavaTime.{localDateTime => now}
import warwick.objectstore.{EncryptedObjectStorageService, ObjectStorageService}

import scala.jdk.CollectionConverters._
import scala.concurrent.ExecutionContext
import scala.concurrent.duration._
import scala.io.Source
import scala.util.Try

object EncryptedObjectStorageHealthCheck {
  val ObjectKey = "healthcheck-test"
  val ObjectKeyCTR = "healthcheck-test.ctr"
  val Contents = "Here is a short sentence that we'll store in the encrypted object storage."
}

@Singleton
class EncryptedObjectStorageHealthCheck @Inject()(
  objectStorageService: ObjectStorageService,
  system: ActorSystem,
  @Named("fileUploadsExecutionContext") ec: ExecutionContext
) extends ServiceHealthcheckProvider(new ServiceHealthcheck("encrypted-storage", Status.Unknown, now)) with Logging {

  private val name: String = "encrypted-storage"

  override def run(): Unit = update({
    Try {
      val startTime = System.currentTimeMillis()

      def fetchOrCreateBlob(objectKey: String): Option[Blob] =
        objectStorageService.fetchBlob(objectKey)
          .orElse {
            val byteSource = CharSource.wrap(Contents).asByteSource(StandardCharsets.UTF_8)
            objectStorageService.put(objectKey, byteSource, ObjectStorageService.Metadata(
              contentLength = byteSource.size(),
              contentType = "text/plain",
              fileHash = None
            ))

            objectStorageService.fetchBlob(ObjectKey)
          }

      fetchOrCreateBlob(ObjectKey)
        .map { blob =>
          // Do we have an IV stored in the user metadata?
          if (!blob.getMetadata.getUserMetadata.containsKey(EncryptedObjectStorageService.MetadataIVKey)) {
            new ServiceHealthcheck(
              name,
              Error,
              now,
              s"Couldn't find ${EncryptedObjectStorageService.MetadataIVKey} in the metadata for object $ObjectKey - object not encrypted?"
            )
          } else {
            // Check the decrypted contents match the original
            val actualContents = Source.fromInputStream(blob.getPayload.openStream()).mkString
            if (actualContents != Contents) {
              new ServiceHealthcheck(
                name,
                Error,
                now,
                s"Encrypted contents $actualContents didn't match expected $Contents"
              )
            } else {
              if (EncryptedObjectStorageService.StreamingTransformation != blob.getMetadata.getUserMetadata.get(EncryptedObjectStorageService.MetadataAlgorithm)) {
                // Do the same but for the streaming algorithm
                fetchOrCreateBlob(ObjectKeyCTR)
                  .map { ctrBlob =>
                    // Do we have an IV stored in the user metadata?
                    if (!ctrBlob.getMetadata.getUserMetadata.containsKey(EncryptedObjectStorageService.MetadataIVKey)) {
                      new ServiceHealthcheck(
                        name,
                        Error,
                        now,
                        s"Couldn't find ${EncryptedObjectStorageService.MetadataIVKey} in the metadata for object $ObjectKeyCTR - object not encrypted?"
                      )
                    } else if (EncryptedObjectStorageService.StreamingTransformation != ctrBlob.getMetadata.getUserMetadata.get(EncryptedObjectStorageService.MetadataAlgorithm)) {
                      new ServiceHealthcheck(
                        name,
                        Error,
                        now,
                        s"Couldn't find ${EncryptedObjectStorageService.MetadataAlgorithm} in the metadata for object $ObjectKeyCTR - was ${ctrBlob.getMetadata.getUserMetadata.get(EncryptedObjectStorageService.MetadataAlgorithm)}"
                      )
                    } else {
                      val actualContentsCTR = Source.fromInputStream(ctrBlob.getPayload.openStream()).mkString
                      if (actualContentsCTR != Contents) {
                        new ServiceHealthcheck(
                          name,
                          Error,
                          now,
                          s"Encrypted contents $actualContentsCTR didn't match expected $Contents for $ObjectKeyCTR"
                        )
                      } else {
                        val endTime = System.currentTimeMillis()
                        val timeTakenMs = endTime - startTime

                        new ServiceHealthcheck(
                          name,
                          Okay,
                          now,
                          s"Fetched and decrypted $ObjectKey and $ObjectKeyCTR in ${timeTakenMs}ms",
                          Seq[ServiceHealthcheck.PerformanceData[_]](new ServiceHealthcheck.PerformanceData("time_taken_ms", timeTakenMs)).asJava
                        )
                      }
                    }
                  }
                  .getOrElse {
                    new ServiceHealthcheck(
                      name,
                      Error,
                      now,
                      s"Couldn't find object with key $ObjectKeyCTR in the object store"
                    )
                  }
              } else {
                val endTime = System.currentTimeMillis()
                val timeTakenMs = endTime - startTime

                new ServiceHealthcheck(
                  name,
                  Okay,
                  now,
                  s"Fetched and decrypted $ObjectKey in ${timeTakenMs}ms",
                  Seq[ServiceHealthcheck.PerformanceData[_]](new ServiceHealthcheck.PerformanceData("time_taken_ms", timeTakenMs)).asJava
                )
              }
            }
          }
        }
        .getOrElse {
          new ServiceHealthcheck(
            name,
            Error,
            now,
            s"Couldn't find object with key $ObjectKey in the object store"
          )
        }
    }.recover { case t =>
      logger.error("Error performing encrypted object storage health check", t)

      new ServiceHealthcheck(
        name,
        Unknown,
        now,
        s"Error performing health check: ${t.getMessage}"
      )
    }.get
  })

  system.scheduler.scheduleAtFixedRate(0.seconds, interval = 1.minute)(() => {
    try run()
    catch {
      case e: Throwable =>
        logger.error("Error in health check", e)
    }
  })(ec)

}
