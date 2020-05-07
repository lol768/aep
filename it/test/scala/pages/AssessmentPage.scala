package pages

import java.util.UUID

import org.openqa.selenium.{By, WebDriver, WebElement}
import support.ServerInfo

import scala.jdk.CollectionConverters._
import scala.util.Try

case class UploadedFile(name: String, url: String, deleteButton: WebElement)
case class AssessmentFile(name: String, downloadUrl: String, downloadLink: WebElement, openInBrowserUrl: Option[String], openInBrowserLink: Option[WebElement])

class AssessmentPage(id: UUID)(implicit driver: WebDriver, server: ServerInfo) extends AbstractPage("Assessment", driver, server) {
  override val path: String = s"/assessment/$id"

  def startButton: WebElement = driver.findElement(By.id("startAssessment"))

  def agreeAuthorshipCheckbox: WebElement = driver.findElement(By.id("agreeAuthorship"))

  def authorshipDeclarationButton: WebElement = driver.findElement(By.cssSelector(".authorship-declaration-btn"))

  def hasRACheckbox: WebElement = driver.findElement(By.cssSelector("input[type=radio][value=hasRA]"))

  def hasNoRACheckbox: WebElement = driver.findElement(By.cssSelector("input[type=radio][value=hasNoRA]"))

  def raDeclarationButton: WebElement = driver.findElement(By.cssSelector(".ra-declaration-btn"))

  def fileInput: WebElement = driver.findElement(By.name("file"))

  def uploadFilesButton: WebElement = driver.findElement(By.cssSelector("form.upload-progress button[type=submit]"))

  def finishExamCheckbox: WebElement = driver.findElement(By.cssSelector(".finish-exam-panel input[type=checkbox]"))

  def finishExamButton: WebElement = driver.findElement(By.cssSelector(".finish-exam-panel button[type=submit]"))

  def assessmentFiles: Seq[AssessmentFile] = driver.findElements(By.cssSelector("li.assessment-brief-file")).asScala.map { li =>
    val downloadLink = li.findElement(By.partialLinkText("Download"))
    val openInBrowserLink = Try(li.findElement(By.partialLinkText("Open in browser"))).toOption

    AssessmentFile(
      name = li.findElement(By.className("media-heading")).getText,
      downloadUrl = downloadLink.getAttribute("href"),
      downloadLink = downloadLink,
      openInBrowserUrl = openInBrowserLink.map(_.getAttribute("href")),
      openInBrowserLink = openInBrowserLink
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
