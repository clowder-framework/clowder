package models

import java.util.Date
import play.api.libs.json.Json._
import play.api.libs.json._


case class ElasticsearchTag (
  creator: String,
  created: Date,
  name: String
)
object ElasticsearchTag {
  /**
    * Serializer for ElasticsearchTag
    */
  implicit object ElasticsearchTagWrites extends Writes[ElasticsearchTag] {
    def writes(est: ElasticsearchTag): JsValue = JsObject(Seq(
      "creator" -> JsString(est.creator),
      "created" -> JsString(est.created.toString),
      "tag" -> JsString(est.name)
    ))
  }

  /**
    * Deserializer for ElasticsearchTag
    */
  implicit object ElasticsearchTagReads extends Reads[ElasticsearchTag] {
    def reads(json: JsValue): JsResult[ElasticsearchTag] = JsSuccess(new ElasticsearchTag(
      (json \ "creator").as[String],
      (json \ "created").as[Date],
      (json \ "name").as[String]
    ))
  }
}

case class ElasticsearchComment (
  creator: String,
  created: Date,
  text: String
)
object ElasticsearchComment {
  /**
    * Serializer for ElasticsearchComment
    */
  implicit object ElasticsearchCommentWrites extends Writes[ElasticsearchComment] {
    def writes(esc: ElasticsearchComment): JsValue = JsObject(Seq(
      "creator" -> JsString(esc.creator),
      "created" -> JsString(esc.created.toString),
      "text" -> JsString(esc.text)
    ))
  }

  /**
    * Deserializer for ElasticsearchComment
    */
  implicit object ElasticsearchCommentReads extends Reads[ElasticsearchComment] {
    def reads(json: JsValue): JsResult[ElasticsearchComment] = JsSuccess(new ElasticsearchComment(
      (json \ "creator").as[String],
      (json \ "created").as[Date],
      (json \ "text").as[String]
    ))
  }
}

case class ElasticsearchObject (
  resource: ResourceRef,
  name: String,
  creator: String,
  created: Date,
  parent_of: List[String] = List.empty,
  child_of: List[String] = List.empty,
  description: String,
  tags: List[ElasticsearchTag] = List.empty,
  comments: List[ElasticsearchComment] = List.empty,
  metadata: Map[String, JsValue] = Map()
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
      "parent_of" -> JsArray(eso.parent_of.toSeq.map( (p:String) => Json.toJson(p)): Seq[JsValue]),
      "child_of" -> JsArray(eso.child_of.toSeq.map( (c:String) => Json.toJson(c)): Seq[JsValue]),
      "description" -> JsString(eso.description),
      "tags" -> JsArray(eso.tags.toSeq.map( (t:ElasticsearchTag) => Json.toJson(t)): Seq[JsValue]),
      "comments" -> JsArray(eso.comments.toSeq.map( (c:ElasticsearchComment) => Json.toJson(c)): Seq[JsValue]),
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
      (json \ "parent_of").as[List[String]],
      (json \ "child_of").as[List[String]],
      (json \ "description").as[String],
      (json \ "tags").as[List[ElasticsearchTag]],
      (json \ "comments").as[List[ElasticsearchComment]],
      (json \ "metadata").as[Map[String, JsValue]]
    ))
  }
}
