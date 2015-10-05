package models

import java.net.URI
import java.util.Date
import play.api.libs.json.Json._
import play.api.libs.json._
import securesocial.core.Identity

/**
 * A Curation Object assists researchers and curators to identify sets of resources for publication.
 */
case class CurationObject (
  id: UUID = UUID.generate,
  name: String = "",
  author: Identity,
  description: String = "",
  created: Date,
  submittedDate: Option[Date],
  publishedDate: Option[Date],
  space: UUID,
  datasets: List[Dataset] =  List.empty,
  collections: List[Collection] = List.empty,
  //here we don't use a map to know the file belongs to which dataset, we find the fileByDataset from dataset.files._.id
  files: List[File] =  List.empty,
  repository: Option[String],
  status: String,
  externalIdentifier: Option[URI] = None
)

/**
 *  Class for mapping the response from Matchmaker into a Scala Object
 */
case class mmRule(
                   rule_name: String,
                   Score: Int,
                   Message: String)

object mmRule {

  /**
   * Serializer for Matchmaker Rule
   */
  implicit object mmRuleWrites extends Writes[mmRule] {
    def writes(mm: mmRule): JsValue = JsObject(Seq(
      "rule_name" -> JsString(mm.rule_name),
      "score" -> JsNumber(mm.Score),
      "message" -> JsString(mm.Message)
    ))
  }

  /**
   * Deserializer for Matchmaker Rule
   */
  implicit object mmRuleReads extends Reads[mmRule] {
    def reads(json: JsValue): JsResult[mmRule] = JsSuccess(new mmRule(
      (json \ "Rule Name").as[String],
      (json \ "Score").as[Int],
      (json \ "Message").as[String]
    ))
  }
}

/**
 * Class for mapping the response from Matchmaker into a Scala Object
 */
case class MatchMakerResponse(
                               orgidentifier: String,
                               repositoryName: String,
                               per_rule_score: List[mmRule],
                               total_score: Int)

object MatchMakerResponse{

  /**
   * Serializer for Matchmaker Response type
   */
  implicit object MatchmakerWrites extends Writes[MatchMakerResponse] {
    def writes(mm: MatchMakerResponse): JsValue = JsObject(Seq(
      "orgidentifier" -> JsString(mm.orgidentifier),
      "repositoryName" -> JsString(mm.repositoryName),
      "per_rule_score" -> JsArray(mm.per_rule_score.map(toJson(_))),
      "total_score" -> JsNumber(mm.total_score)
    ))
  }

  /**
   * Deserializer for Matchmaker Response type
   */
  implicit object MatchmakerReads extends Reads[MatchMakerResponse] {
    def reads(json: JsValue): JsResult[MatchMakerResponse] = JsSuccess(new MatchMakerResponse(

      (json \ "orgidentifier").as[String],
      (json \ "repositoryName").as[String],
      (json \ "Per Rule Scores").as[List[mmRule]],
      (json \ "Total Score").as[Int]
    ))
  }
}
