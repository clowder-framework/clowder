package integration

import play.api.test.FakeApplication
import play.api.test.FakeRequest
import play.api.test.Helpers._
import play.api.libs.concurrent.Execution.Implicits._
import play.api.libs.json.Json
import play.api.Logger
import org.scalatestplus.play.OneAppPerSuite
import org.scalatestplus.play.PlaySpec
import play.api.Play
import org.apache.http.entity.mime.content.ContentBody
import org.apache.http.entity.mime.MultipartEntity
import org.apache.commons.io.output.ByteArrayOutputStream
import org.apache.http.entity.mime.content.FileBody
import java.io.File
import play.api.http.Writeable
import play.api.mvc.MultipartFormData
import play.api.libs.Files.TemporaryFile
import play.api.mvc.MultipartFormData.FilePart
import play.api.mvc.Codec
import org.apache.http.entity.mime.MultipartEntityBuilder
import org.apache.http.entity.mime.content.StringBody
import org.apache.http.entity.ContentType
import play.api.http._
/*
 * Based on http://stackoverflow.com/questions/15133794/writing-a-test-case-for-file-uploads-in-play-2-1-and-scala
 *
 * Functional Test for DTS File Upload
 * 
 * @author Smruti Padhy
 */

trait FakeMultipartUpload {
  implicit def writeableOf_multiPartFormData(implicit codec: Codec): Writeable[MultipartFormData[TemporaryFile]] = {
    val builder = MultipartEntityBuilder.create().setBoundary("--boundary---")

    def transform(multipart: MultipartFormData[TemporaryFile]): Array[Byte] = {
      multipart.files.foreach { file =>
        val part = new FileBody(file.ref.file, ContentType.create(file.contentType.getOrElse("application/octet-stream")), file.filename)
        builder.addPart(file.key, part)
      }

      val outputStream = new ByteArrayOutputStream
      builder.build.writeTo(outputStream)
      outputStream.toByteArray
    }

    new Writeable[MultipartFormData[TemporaryFile]](transform, Some(builder.build.getContentType.getValue))
  }

  def fileUpload(key: String, file: File, contentType: String): MultipartFormData[TemporaryFile] = {
    MultipartFormData(
      dataParts = Map(),
      files = Seq(FilePart[TemporaryFile](key, file.getName, Some(contentType), TemporaryFile(file))),
      badParts = Seq(),
      missingFileParts = Seq())
  }

  case class WrappedFakeRequest[A](fr: FakeRequest[A]) {
    def withFileUpload(key: String, file: File, contentType: String) = {
      fr.withBody(fileUpload(key, file, contentType))
    }
  }

  implicit def toWrappedFakeRequest[A](fr: FakeRequest[A]) = WrappedFakeRequest(fr)
}

class ExtractionFileUploadFunctionSpec extends PlaySpec with OneAppPerSuite with FakeMultipartUpload {
  val excludedPlugins = List(
    "services.RabbitmqPlugin",
    "services.VersusPlugin")

  implicit override lazy val app: FakeApplication = FakeApplication(withoutPlugins = excludedPlugins)

  "The OneAppPerSuite for Extraction API Controller Router test" must {
    "respond to the Upload File URL" in {
      val secretKey = play.api.Play.configuration.getString("commKey").getOrElse("")
      val fileurl = "http://www.ncsa.illinois.edu/assets/img/logos_ncsa.png"
      val request = FakeRequest(POST, "/api/extractions/upload_url?key=" + secretKey).withJsonBody(Json.toJson(Map("fileurl" -> fileurl)))
      val result = route(request).get
      info("Status=" + status(result))
      status(result) mustEqual OK
      info("contentType=" + contentType(result))
      contentType(result) mustEqual Some("application/json")
      contentAsString(result) must include("id")
      info("contentAsString" + contentAsString(result))

    }
    "respond to the Upload File" in {
      val secretKey = play.api.Play.configuration.getString("commKey").getOrElse("")
      val workingDir = System.getProperty("user.dir")
      info("Working Directory: " + workingDir)
      val file1 = new java.io.File(workingDir + "/test/data/morrowplots.jpg")
      if (file1.isFile && file1.exists) {
        Logger.debug("File1 is File:True")
      }
      val req = FakeRequest(POST, "/api/extractions/upload_file?key=" + secretKey).
        withFileUpload("File", file1, "image/jpg")
      val result = route(req).get

      info("Status=" + status(result))
      status(result) mustEqual OK
      info("contentType=" + contentType(result))
      contentType(result) mustEqual Some("application/json")
      contentAsString(result) must include("id")
      info("contentAsString" + contentAsString(result))
    }

  }
}