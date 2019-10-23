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
  stats: Statistics = new Statistics(),
  status: String = FileStatus.UNKNOWN.toString) // can't use enums in salat

// what is the status of the file
object FileStatus extends Enumeration {
  type FileStatus = Value
  val UNKNOWN, CREATED, UPLOADED, PROCESSED, ARCHIVED = Value
}

case class Versus(
  fileId: UUID,
  descriptors: Map[String,Any]= Map.empty
)

object File {
  implicit object FileWrites extends Writes[File] {
    def writes(file: File): JsObject = {
      val fileThumbnail = if(file.thumbnail_id.isEmpty) {
        null
      } else {
        file.thumbnail_id.toString().substring(5,file.thumbnail_id.toString().length-1)
      }
      Json.obj(
        "id" -> file.id,
        "name" -> file.filename,
        "status" -> file.status,
        "thumbnail" -> fileThumbnail,
        "created" -> file.uploadDate.toString,
        "resource_type" -> "file")
    }
  }
}
