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
  path: Option[String] = None,
  filename: String,
  author: Identity,
  uploadDate: Date,
  contentType: String,
  length: Long = 0,
  showPreviews: String = "DatasetLevel",
  sections: List[Section] = List.empty,
  previews: List[Preview] = List.empty,
  tags: List[Tag] = List.empty,
  thumbnail_id: Option[String] = None,
  jsonldMetadata : List[Metadata] = List.empty,
  @deprecated("use Metadata","since the use of jsonld") metadata: List[Map[String, Any]] = List.empty,
  @deprecated("will not be used in the future","since the use of jsonld") isIntermediate: Option[Boolean] = None,
  @deprecated("use Metadata","since the use of jsonld") userMetadata: Map[String, Any] = Map.empty,
  @deprecated("use Metadata","since the use of jsonld") xmlMetadata: Map[String, Any] = Map.empty,
  @deprecated("use Metadata","since the use of jsonld") userMetadataWasModified: Option[Boolean] = None,
  licenseData: LicenseData = new LicenseData(),
  notesHTML: Option[String] = None,
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
