package models

import java.util.Date
import play.api.libs.json.{Json, JsValue, Writes}

case class Collection(
  id: UUID = UUID.generate,
  name: String = "N/A",
  author: MiniUser,
  description: String = "N/A",
  created: Date,
  datasetCount: Integer,
  thumbnail_id: Option[String] = None,
  previews: List[Preview] = List.empty,
  spaces: List[UUID] = List.empty,
  lastModifiedDate: Date = new Date(),
  followers: List[UUID] = List.empty,
  parent_collection_ids : List[UUID] = List.empty,
  child_collection_ids : List[UUID] = List.empty,
  root_spaces: List[UUID] = List.empty,
  metadataCount: Long = 0,
  childCollectionsCount: Integer = 0,
  @deprecated("use Metadata","since the use of jsonld") jsonldMetadata : List[Metadata]= List.empty)

object Collection {
  implicit val collectionWrites = new Writes[Collection] {
    def writes(collection: Collection): JsValue = {
      val collectionThumbnail = if(collection.thumbnail_id.isEmpty) {
        "None"
      } else {
        collection.thumbnail_id.toString().substring(5,collection.thumbnail_id.toString().length-1)
      }
      val collectionAuthor = collection.author.id

      Json.obj("id" -> collection.id.toString, "collectionname" -> collection.name, "description" -> collection.description,
        "created" -> collection.created.toString, "thumbnail" -> collectionThumbnail, "authorId" -> collectionAuthor)
    }
  }

  implicit def collectionToElasticSearchObject(c: Collection): ElasticSearchObject = {
    new ElasticSearchObject(
      ResourceRef('collection, c.id),
      c.author.id.toString,
      c.created,
      c.parent_collection_ids.map( pc_id => pc_id.toString ),
      c.child_collection_ids.map( cc_id => cc_id.toString),
      List.empty,
      List.empty,
      Map()
    )
  }
}
