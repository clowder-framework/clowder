package models

import java.net.URL
import java.util.Date
import play.api.libs.json._
import play.api.libs.json.Reads._
import play.api.libs.functional.syntax._

/**
 * Status information about extractors and extractions.
 */
case class Extraction(
  id: UUID = UUID.generate,
  file_id: UUID,
  extractor_id: String,
  status: String = "N/A",
  start: Option[Date],
  end: Option[Date])

/**
 * Currently running extractor name
 */
case class ExtractorNames(
  name: String = ""
)

/**
 * An input type supported by an extractor
 */
case class ExtractorInputType(
  inputType: String = ""
)

/**
 * Servers information running different extractors
 * and supported file formats
 *
 */
case class ExtractorServer(
  server: String = "N/A"
)

/**
 * Extractors' Servers IPs, Name and Count
 * This is a temporary fix for keeping track of number of extractors running in different servers
 * This class may be omitted once the design and implementation for BD-289 are done
 */
case class ExtractorDetail(
  ip: String = "",
  name: String = "",
  var count: Int = 0
)

/**
 * Information about individual extractors. An extractor should set this the first time it starts up.
 *
 * Modelled after node.js package.json
 *
 * @param id id internal to the system
 * @param name lower case, no spaces, can use dashes
 * @param version the version, for example 1.3.5
 * @param updated date when this information was last updated
 * @param description short description of what the extractor does
 * @param author First Last <username@somedomain.org>
 * @param contributors list of contributors with same format as author
 * @param contexts the ids of the contexts defining the metadata uploaded by the extractors
 * @param repository source code repository
 * @param external_services external services used by the extractor
 * @param libraries libraries on which the code depends
 * @param bibtex bibtext formatted citation of relevant papers
 */
case class ExtractorInfo(
  id: UUID,
  name: String,
  version: String,
  updated: Date,
  description: String,
  author: String,
  contributors: List[String],
  contexts: List[UUID],
  repository: Repository,
  external_services: List[String],
  libraries: List[String],
  bibtex: List[String]
)

object ExtractorInfo {
  implicit val repositoryFormat = Json.format[Repository]
  implicit val urlFormat = new Format[URL] {
    def reads(json: JsValue): JsResult[URL] = JsSuccess(new URL(json.toString()))
    def writes(url: URL): JsValue = Json.toJson(url.toExternalForm)
  }
  implicit val dateFormat = new Format[Date] {
    def reads(json: JsValue): JsResult[Date] = JsSuccess(json.as[Date])
    def writes(url: Date): JsValue = Json.toJson(url.toString)
  }
  implicit val extractorInfoWrites = Json.writes[ExtractorInfo]
  implicit val extractorInfoReads: Reads[ExtractorInfo] = (
    (JsPath \ "id").read[UUID].orElse(Reads.pure(UUID.generate())) and
      (JsPath \ "name").read[String] and
      (JsPath \ "version").read[String] and
      (JsPath \ "updated").read[Date].orElse(Reads.pure((new Date()))) and
      (JsPath \ "description").read[String] and
      (JsPath \ "author").read[String] and
      (JsPath \ "contributors").read[List[String]].orElse(Reads.pure(List.empty)) and
      (JsPath \ "contexts").read[List[UUID]].orElse(Reads.pure(List.empty)) and
      (JsPath \ "repository").read[Repository] and
      (JsPath \ "external_services").read[List[String]].orElse(Reads.pure(List.empty)) and
      (JsPath \ "libraries").read[List[String]].orElse(Reads.pure(List.empty)) and
      (JsPath \ "bibtex").read[List[String]].orElse(Reads.pure(List.empty))
    )(ExtractorInfo.apply _)
}

/**
 * Source code repository
 *
 * @param repType git, hg, svn
 * @param repUrl the url of the repository, for example https://opensource.ncsa.illinois.edu/stash/scm/cats/clowder.git
 */
case class Repository(repType: String, repUrl: String)


