package models

import java.util.Date

import models.FileStatus.FileStatus
import play.api.libs.json.{JsObject, Json, Writes}

/**
 * Uploaded files.
 *
 *
 */
case class File(
  id: UUID = UUID.generate,
  loader_id: String = "",
  filename: String,
  originalname: String = "",
  author: MiniUser,
  uploadDate: Date,
  contentType: String,
  length: Long = 0,
  loader: String = "",
  showPreviews: String = "DatasetLevel",
  sections: List[Section] = List.empty,
  previews: List[Preview] = List.empty,
  tags: List[Tag] = List.empty,
  thumbnail_id: Option[String] = None,
  metadataCount: Long = 0,
  description : String = "",
  isIntermediate: Boolean = false,
  @deprecated("use Metadata","since the use of jsonld") xmlMetadata: Map[String, Any] = Map.empty,
  licenseData: LicenseData = new LicenseData(),
  followers: List[UUID] = List.empty,
  status: String = FileStatus.UNKNOWN.toString) // can't use enums in salat

// what is the status of the file
object FileStatus extends Enumeration {
  type FileStatus = Value
  val UNKNOWN, CREATED, UPLOADED, PROCESSED = Value
}

case class Versus(
  fileId: UUID,
  descriptors: Map[String,Any]= Map.empty
)

object File {
  implicit object FileWrites extends Writes[File] {
    def writes(file: File): JsObject = {
      Json.obj(
        "id" -> file.id,
        "name" -> file.filename,
        "status" -> file.status)
    }
  }
}
