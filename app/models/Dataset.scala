package models

import com.mongodb.casbah.Imports._
import java.util.Date
import play.api.libs.json.{JsObject, Writes, Json}
import securesocial.core.Identity
import play.api.libs.json._
import play.api.libs.functional.syntax._

/**
 * A dataset is a collection of files, and streams.
 */
case class Dataset(
  id: UUID = UUID.generate,
  name: String = "N/A",
  author: Identity,
  description: String = "N/A",
  created: Date,
  files: List[UUID] = List.empty,
  streams_id: List[ObjectId] = List.empty,
  tags: List[Tag] = List.empty,
  metadataCount: Long = 0,
  @deprecated("use Metadata","since the use of jsonld") jsonldMetadata: List[Metadata] = List.empty,
  @deprecated("use Metadata","since the use of jsonld") metadata: Map[String, Any] = Map.empty,
  @deprecated("use Metadata","since the use of jsonld") userMetadata: Map[String, Any] = Map.empty,
  collections: List[UUID] = List.empty,
  thumbnail_id: Option[String] = None,
  @deprecated("use Metadata","since the use of jsonld") datasetXmlMetadata: List[DatasetXMLMetadata] = List.empty,
  @deprecated("use Metadata","since the use of jsonld") userMetadataWasModified: Option[Boolean] = None,
  licenseData: LicenseData = new LicenseData(),
  notesHTML: Option[String] = None,
  spaces: List[UUID] = List.empty,
  lastModifiedDate: Date = new Date(),
  followers: List[UUID] = List.empty)

object Dataset {
  implicit val datasetWrites = new Writes[Dataset] {
    def writes(dataset: Dataset): JsValue = {
      val datasetThumbnail = if(dataset.thumbnail_id.isEmpty) {
        "None"
      } else {
        dataset.thumbnail_id.toString().substring(5,dataset.thumbnail_id.toString().length-1)
      }
      Json.obj("id" -> dataset.id.toString, "name" -> dataset.name, "description" -> dataset.description,
        "created" -> dataset.created.toString, "thumbnail" -> datasetThumbnail, "authorId" -> dataset.author.identityId.userId)
    }
  }
}