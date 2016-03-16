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
  files: List[UUID] =  List.empty,  //id of curationFile, different from live object
  folders: List[UUID] =  List.empty,  //id of curationFolder, different from live object
  repository: Option[String],
  status: String,
  externalIdentifier: Option[URI] = None,
  metadataCount: Long = 0
)

case class StatusFromRepository(date: String, reporter: String, message: String, stage: String)
object StatusFromRepository {
  implicit object StatusFromRepositoryWrites extends Writes[StatusFromRepository] {
  def writes(s: StatusFromRepository): JsValue = JsObject(Seq(
    "date" -> JsString(s.date),
    "reporter" ->JsString(s.reporter),
    "message" -> JsString(s.message),
    "stage" ->JsString(s.stage)
  ))
}

  implicit object StatusFromRepositoryReads extends Reads[StatusFromRepository]
  {
    def reads(json: JsValue): JsResult[StatusFromRepository] = JsSuccess(new StatusFromRepository(
      (json \ "date").as[String],
      (json \ "reporter").as[String],
      (json \ "message").as[String],
      (json \ "stage").as[String]
    ))
  }

}
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


case class CurationFile(
  id: UUID = UUID.generate,
  fileId: UUID,
  loader_id: String = "",
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
  metadataCount: Long = 0,
  licenseData: LicenseData = new LicenseData(),
  notesHTML: Option[String] = None,
  sha512: String = "" )


object CurationFile {
  implicit object CurationFileWrites extends Writes[CurationFile] {
    def writes(cf: CurationFile): JsObject = {
      Json.obj(
        "id" -> cf.id.toString(),
        "fileId" -> cf.fileId.toString(),
        "name" -> cf.filename,
        "length" -> cf.length,
        "contentType" -> cf.contentType)
    }
  }
}

case class CurationFolder(
  id: UUID = UUID.generate,
  folderId: UUID,
  author: MiniUser,
  created: Date,
  name: String = "N/A",
  displayName: String = "N/A",
  files: List[UUID] = List.empty,
  folders: List[UUID] = List.empty,
  parentId: UUID,
  parentType: String,
  parentCurationObjectId: UUID)