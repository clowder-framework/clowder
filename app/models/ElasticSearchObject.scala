package models

import java.util.Date
import play.api.libs.json.Json._
import play.api.libs.json._

import scala.List


case class ElasticsearchObject (
  resource: ResourceRef,
  name: String,
  creator: String,
  created: Date,
  created_as: String = "",
  parent_of: List[String] = List.empty,
  child_of: List[String] = List.empty,
  description: String,
  tags: List[String] = List.empty,
  comments: List[String] = List.empty,
  metadata: Map[String, JsValue] = Map()
)

case class ElasticsearchResult (
 results: List[ResourceRef],
 from: Int = 0,           // Starting index of results
 size: Int = 240,         // Requested page size of query
 scanned_size: Int = 240, // Number of records scanned to fill 'size' results after permission check
 total_size: Long = 0     // Number of records across all pages
)


object ElasticsearchObject {
  /**
    * Serializer for ElasticsearchObject
    */
  implicit object ElasticsearchWrites extends Writes[ElasticsearchObject] {
    def writes(eso: ElasticsearchObject): JsValue = Json.obj(
      "resource" -> JsString(eso.resource.toString),
      "name" -> JsString(eso.name),
      "creator" -> JsString(eso.creator),
      "created" -> JsString(eso.created.toString),
      "created_as" -> JsString(eso.created_as.toString),
      "parent_of" -> JsArray(eso.parent_of.toSeq.map( (p:String) => Json.toJson(p)): Seq[JsValue]),
      "child_of" -> JsArray(eso.child_of.toSeq.map( (c:String) => Json.toJson(c)): Seq[JsValue]),
      "description" -> JsString(eso.description),
      "tags" -> JsArray(eso.tags.toSeq.map( (t:String) => Json.toJson(t)): Seq[JsValue]),
      "comments" -> JsArray(eso.comments.toSeq.map( (c:String) => Json.toJson(c)): Seq[JsValue]),
      "metadata" -> JsArray(eso.metadata.toSeq.map(
        (m:(String,JsValue)) => new JsObject(Seq(m._1 -> m._2)) )
      )
    )
  }

  /**
    * Deserializer for ElasticsearchObject
    */
  implicit object ElasticsearchReads extends Reads[ElasticsearchObject] {
    def reads(json: JsValue): JsResult[ElasticsearchObject] = JsSuccess(new ElasticsearchObject(
      (json \ "resource").as[ResourceRef],
      (json \ "name").as[String],
      (json \ "creator").as[String],
      (json \ "created").as[Date],
      (json \ "created_as").as[String],
      (json \ "parent_of").as[List[String]],
      (json \ "child_of").as[List[String]],
      (json \ "description").as[String],
      (json \ "tags").as[List[String]],
      (json \ "comments").as[List[String]],
      (json \ "metadata").as[Map[String, JsValue]]
    ))
  }
}
