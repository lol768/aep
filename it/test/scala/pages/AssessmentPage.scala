package pages

import java.util.UUID

import org.openqa.selenium.{By, WebDriver, WebElement}
import support.ServerInfo

import scala.jdk.CollectionConverters._

case class UploadedFile(name: String, url: String, deleteButton: WebElement)
case class AssessmentFile(name: String, url: String, link: WebElement)

class AssessmentPage(id: UUID)(implicit driver: WebDriver, server: ServerInfo) extends AbstractPage("Assessment", driver, server) {
  override val path: String = s"/assessment/$id"

  def startButton: WebElement = driver.findElement(By.id("startAssessment"))

  def fileInput: WebElement = driver.findElement(By.name("file"))

  def uploadFilesButton: WebElement = driver.findElement(By.cssSelector("form.upload-progress button[type=submit]"))

  def finishExamCheckbox: WebElement = driver.findElement(By.cssSelector(".finish-exam-panel input[type=checkbox]"))

  def finishExamButton: WebElement = driver.findElement(By.cssSelector(".finish-exam-panel button[type=submit]"))

  def assessmentFiles: Seq[AssessmentFile] = driver.findElements(By.cssSelector("a.assessment-brief-file")).asScala.map { a =>
    AssessmentFile(
      name = a.getText,
      url = a.getAttribute("href"),
      link = a
    )
  }.toSeq

  def uploadedFiles: Seq[UploadedFile] = driver.findElements(By.cssSelector(".uploaded-file")).asScala.map { div =>
    UploadedFile(
      name = div.findElement(By.className("media-heading")).getText,
      url = div.findElement(By.cssSelector("a[download]")).getAttribute("href"),
      deleteButton = div.findElement(By.cssSelector("button[delete]"))
    )
  }.toSeq
}
