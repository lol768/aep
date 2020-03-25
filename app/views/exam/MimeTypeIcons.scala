package views.exam

object MimeTypeIcons {
  def toIcon(mimeType: String): String = {
    mimeType match {
      case "application/pdf" => "fa-file-pdf"
      case _: String => "fa-file"
    }
  }
}
