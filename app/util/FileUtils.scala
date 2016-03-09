package util

object FileUtils {
  def getContentType(filename: Option[String], contentType: Option[String]): String = {
    getContentType(filename.getOrElse(""), contentType)
  }

  def getContentType(filename: String, contentType: Option[String]): String = {
    var ct = contentType.getOrElse(play.api.http.ContentTypes.BINARY)
    if (ct == play.api.http.ContentTypes.BINARY) {
      ct = play.api.libs.MimeTypes.forFileName(filename).getOrElse(play.api.http.ContentTypes.BINARY)
    }
    ct
  }
}
