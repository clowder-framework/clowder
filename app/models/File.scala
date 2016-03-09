package models

import java.util.Date

import play.api.libs.json.{JsObject, Json, Writes}
import securesocial.core.Identity


/**
 * Uploaded files.
 *
 * @author Luigi Marini
 *
 */
case class File(
  id: UUID = UUID.generate,
  loader_id: String = "",
  filename: String,
  author: Identity,
  uploadDate: Date,
  contentType: String,
  length: Long = 0,
  sha512: String = "",
  loader: String = "",
  showPreviews: String = "DatasetLevel",
  sections: List[Section] = List.empty,
  previews: List[Preview] = List.empty,
  tags: List[Tag] = List.empty,
  thumbnail_id: Option[String] = None,
  metadataCount: Long = 0,
  description : String = "",
  @deprecated("will not be used in the future","since the use of jsonld") isIntermediate: Option[Boolean] = None,
  @deprecated("use Metadata","since the use of jsonld") xmlMetadata: Map[String, Any] = Map.empty,
  licenseData: LicenseData = new LicenseData(),
  followers: List[UUID] = List.empty )

case class Versus(
  fileId: UUID,
  descriptors: Map[String,Any]= Map.empty
)

object File {
  implicit object FileWrites extends Writes[File] {
    def writes(file: File): JsObject = {
      Json.obj(
        "id" -> file.id,
        "name" -> file.filename)
    }
  }
}
