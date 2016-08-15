package models

import java.util.Date
import play.api.libs.json.Json._
import play.api.libs.json._

case class ElasticSearchObject (
  doctype: ResourceRef,
  creator: String,
  created: Date,
  tags: List[ElasticSearchTag] = List.empty,
  comments: List[ElasticSearchComment] = List.empty,
  metadata: List[JsObject] = List.empty
)

object ElasticSearchObject {
  /**
    * Serializer for ElasticSearchObject
    */
  implicit object ElasticSearchWrites extends Writes[ElasticSearchObject] {
    def writes(eso: ElasticSearchObject): JsValue = Json.obj(
      "type" -> JsString(eso.doctype.toString),
      "creator" -> JsString(eso.creator),
      "created" -> JsString(eso.created.toString),
      "tags" -> JsArray(eso.tags.toSeq.map( (t:ElasticSearchTag) => Json.toJson(t)): Seq[JsValue]),
      "comments" -> JsArray(eso.comments.toSeq.map( (c:ElasticSearchComment) => Json.toJson(c)): Seq[JsValue]),
      "metadata" -> JsArray(eso.metadata)
    )
  }

  /**
    * Deserializer for ElasticSearchObject
    */
  implicit object ElasticSearchReads extends Reads[ElasticSearchObject] {
    def reads(json: JsValue): JsResult[ElasticSearchObject] = JsSuccess(new ElasticSearchObject(
      (json \ "type").as[ResourceRef],
      (json \ "creator").as[String],
      (json \ "created").as[Date],
      (json \ "tags").as[List[ElasticSearchTag]],
      (json \ "comments").as[List[ElasticSearchComment]],
      (json \ "metadata").as[List[JsObject]]
    ))
  }
}


case class ElasticSearchTag (
  creator: String,
  created: Date,
  tag: String
)
object ElasticSearchTag {
  /**
    * Serializer for ElasticSearchTag
    */
  implicit object ElasticSearchTagWrites extends Writes[ElasticSearchTag] {
    def writes(est: ElasticSearchTag): JsValue = JsObject(Seq(
      "creator" -> JsString(est.creator),
      "created" -> JsString(est.created.toString),
      "tag" -> JsString(est.tag)
    ))
  }

  /**
    * Deserializer for ElasticSearchTag
    */
  implicit object ElasticSearchTagReads extends Reads[ElasticSearchTag] {
    def reads(json: JsValue): JsResult[ElasticSearchTag] = JsSuccess(new ElasticSearchTag(
      (json \ "creator").as[String],
      (json \ "created").as[Date],
      (json \ "tag").as[String]
    ))
  }
}

case class ElasticSearchComment (
  creator: String,
  created: Date,
  text: String
)
object ElasticSearchComment {
  /**
    * Serializer for ElasticSearchComment
    */
  implicit object ElasticSearchCommentWrites extends Writes[ElasticSearchComment] {
    def writes(esc: ElasticSearchComment): JsValue = JsObject(Seq(
      "creator" -> JsString(esc.creator),
      "created" -> JsString(esc.created.toString),
      "text" -> JsString(esc.text)
    ))
  }

  /**
    * Deserializer for ElasticSearchComment
    */
  implicit object ElasticSearchCommentReads extends Reads[ElasticSearchComment] {
    def reads(json: JsValue): JsResult[ElasticSearchComment] = JsSuccess(new ElasticSearchComment(
      (json \ "creator").as[String],
      (json \ "created").as[Date],
      (json \ "text").as[String]
    ))
  }
}
