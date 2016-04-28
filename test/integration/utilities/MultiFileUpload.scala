package integration

import play.api.test.FakeRequest
import org.apache.http.entity.mime.MultipartEntityBuilder
import org.apache.commons.io.output.ByteArrayOutputStream
import org.apache.http.entity.mime.content.FileBody
import java.io.File
import play.api.http.Writeable
import play.api.mvc.MultipartFormData
import play.api.mvc.MultipartFormData.FilePart
import play.api.mvc.Codec
import org.apache.http.entity.ContentType

trait FakeMultipartUpload {
  implicit def writeableOf_multiPartFormData(implicit codec: Codec): Writeable[MultipartFormData[File]] = {
    val builder = MultipartEntityBuilder.create().setBoundary("--boundary---")

    def transform(multipart: MultipartFormData[File]): Array[Byte] = {
      multipart.files.foreach { file =>
        val part = new FileBody(file.ref)
        builder.addPart(file.key, part)
      }

      val outputStream = new ByteArrayOutputStream
      builder.build.writeTo(outputStream)
      outputStream.toByteArray
    }

    new Writeable[MultipartFormData[File]](transform, Some(builder.build.getContentType.getValue))
  }

  def fileUpload(key: String, file: File, contentType: String): MultipartFormData[File] = {
    MultipartFormData(
      dataParts = Map(),
      files = Seq(FilePart[File](key, file.getName, Some(contentType), file)),
      badParts = Seq(),
      missingFileParts = Seq())
  }

  case class WrappedFakeRequest[A](fr: FakeRequest[A]) {
    def withFileUpload(key: String, file: File, contentType: String) = {
      fr.withBody(fileUpload(key, file, contentType))
    }

    def withDatasetUpload(key: String, file: File, contentType: String) = {
      fr.withBody(fileUpload(key, file, contentType))
    }
  }

  implicit def toWrappedFakeRequest[A](fr: FakeRequest[A]) = WrappedFakeRequest(fr)
}


