package models

import com.mongodb.casbah.Imports._
import java.util.Date
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
  metadata: Map[String, Any] = Map.empty,
  userMetadata: Map[String, Any] = Map.empty,
  collections: List[String] = List.empty,
  thumbnail_id: Option[String] = None,
  datasetXmlMetadata: List[DatasetXMLMetadata] = List.empty,
  userMetadataWasModified: Option[Boolean] = None,
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
      Json.obj("id" -> dataset.id.toString, "datasetname" -> dataset.name, "description" -> dataset.description,
        "created" -> dataset.created.toString, "thumbnail" -> datasetThumbnail, "authorId" -> dataset.author.identityId.userId)
    }
  }
}