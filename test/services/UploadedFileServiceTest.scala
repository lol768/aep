package services

import domain.Fixtures.uploadedFiles._
import domain.dao.AbstractDaoTest
import helpers.CleanUpDatabaseAfterEachTest
import helpers.FileResourceUtils._
import warwick.core.system.AuditLogContext
import warwick.sso.Usercode

class UploadedFileServiceTest extends AbstractDaoTest with CleanUpDatabaseAfterEachTest {

  override implicit val auditLogContext: AuditLogContext = AuditLogContext.empty(timingContext.timingData)
    .copy(usercode = Some(Usercode("12345678")))

  private lazy val uploadedFileService = get[UploadedFileService]

  "UploadedFileService" should {
    "upload and then retrieve a file" in {
      val uploadedFile = uploadedFileService.store(specialJPG.temporaryUploadedFile.in, specialJPG.uploadedFileSave).futureValue

      uploadedFile.exists(_.fileName == specialJPG.uploadedFileSave.fileName) mustBe true
    }

    "upload multiple files then provide them as a list" in {
      uploadedFileService.store(specialJPG.temporaryUploadedFile.in, specialJPG.uploadedFileSave).futureValue
      uploadedFileService.store(homeOfficeStatementPDF.temporaryUploadedFile.in, homeOfficeStatementPDF.uploadedFileSave).futureValue

      val list = uploadedFileService.listWithoutOwner().serviceValue

      list must have size(2)
      list.map(_.fileName).toSet mustBe Set(specialJPG.path, homeOfficeStatementPDF.path)
    }

    "delete a file based on its ID" in {
      val uploadedFile = uploadedFileService.store(specialJPG.temporaryUploadedFile.in, specialJPG.uploadedFileSave).futureValue
      uploadedFileService.delete(uploadedFile.map(_.id).getOrElse(fileLookupFailed)).futureValue

      uploadedFileService.listWithoutOwner().futureValue.exists(_.isEmpty) mustBe true
    }
  }

}

