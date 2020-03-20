package support

import java.io.{File, IOException}
import java.util.Base64

import com.google.common.base.Charsets
import com.google.common.io.{ByteSource, Files}
import org.openqa.selenium.WebDriver
import org.openqa.selenium.remote.ScreenshotException
import org.scalatestplus.selenium.WebBrowser
import org.scalatest.{Failed, Outcome, TestSuite, TestSuiteMixin}
import uk.ac.warwick.util.core.ExceptionUtils
import warwick.core.Logging

/**
  * Takes a screenshot whenever a test fails. The test might take a screenshot for you
  * and embed it in a ScreenshotException. We'll use that, otherwise we'll snap our own.
  */
trait FailureScreenshots extends TestSuiteMixin with Logging { this: TestSuite with WebBrowser =>

  implicit val webDriver: WebDriver // let the trait know this will be implemented
  val screenshotDirectory: File

  abstract override def withFixture(test: NoArgTest): Outcome = {
    val outcome = super.withFixture(test)

    outcome match {
      case failed: Failed =>
        val filename = s"FAILURE - ${test.name}.png"
        val e = ExceptionUtils.retrieveException(failed.exception, classOf[ScreenshotException])

        if (e != null) {
          val screenshot = Base64.getDecoder.decode(e.getBase64EncodedScreenshot.getBytes(Charsets.UTF_8))
          val outputFile = new File(screenshotDirectory, filename)
          try {
            ByteSource.wrap(screenshot).copyTo(Files.asByteSink(outputFile))
            logger.info("Screenshot written to " + outputFile.getAbsolutePath)
          } catch {
            case ex: IOException =>
              logger.error("Couldn't write screenshot", ex)
          }
        } else {
          capture to filename
        }
      case _ =>
    }

    outcome
  }

}
