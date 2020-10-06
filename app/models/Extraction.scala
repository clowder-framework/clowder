package models

import java.net.URL
import java.text.SimpleDateFormat
import java.util.{Calendar, Date, TimeZone}

import util.Parsers
import play.api.libs.functional.syntax._
import play.api.libs.json.Reads._
import play.api.libs.json._

/**
 * Status information about extractors and extractions.
 */
case class Extraction(
  id: UUID = UUID.generate,
  file_id: UUID,
  job_id: Option[UUID],
  extractor_id: String,
  status: String = "N/A",
  start: Date,
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
 * @param process events that should trigger this extractor to process
 * @param categories list of categories that apply to the extractor
 * @param parameters JSON schema representing allowed parameters
  *                    which can contain the following fields (and more):
  *                     * schema: {} a mapping of property key to type/title/validation data
  *                     * form: [] ordered form fields keyed by properties defined in the schema
  * @see See [[https://github.com/jsonform/jsonform/wiki]] for full documentation regarding parameters
 */
case class ExtractorInfo(
  id: UUID,
  name: String,
  version: String,
  updated: Date = Calendar.getInstance().getTime,
  description: String,
  author: String,
  contributors: List[String],
  contexts: JsValue,
  repository: List[Repository],
  external_services: List[String],
  libraries: List[String],
  bibtex: List[String],
  maturity: String = "Development",
  process: ExtractorProcessTriggers = new ExtractorProcessTriggers(),
  categories: List[String] = List[String](ExtractorCategory.EXTRACT.toString),
  parameters: JsValue = JsObject(Seq())
)

/** what are the categories of the extractor?
  * EXTRACT  - traditional extractor, typically adds metadata or derived outputs to the triggering file/dataset; default
  * CONVERT  - primary function is to convert file(s) from source format to another format, combine files, etc.
  * ARCHIVE  - eligible for triggering using Archive/Unarchive buttons, should expect one of those two parameters
  * PUBLISH  - intended to publish files or datasets to external repositories
  * WORKFLOW - primarily manages workflows, submits external jobs, triggers other extractors, e.g. extractors-rulechecker
  * SILENT   - if in this category, extractor will not send common status messages (e.g. STARTED)
  */
object ExtractorCategory extends Enumeration {
  type ExtractorCategory = Value
  val EXTRACT, CONVERT, ARCHIVE, PUBLISH, WORKFLOW, SILENT = Value
}

object ExtractorInfo {
  implicit val repositoryFormat = Json.format[Repository]
  implicit val extractorProcessTriggersWrites = Json.writes[ExtractorProcessTriggers]
  implicit val extractorProcessTriggersReads: Reads[ExtractorProcessTriggers] = (
    (JsPath \ "dataset").read[List[String]].orElse(Reads.pure(List.empty)) and
      (JsPath \ "file").read[List[String]].orElse(Reads.pure(List.empty)) and
      (JsPath \ "metadata").read[List[String]].orElse(Reads.pure(List.empty))
    )(ExtractorProcessTriggers.apply _)
  implicit val urlFormat = new Format[URL] {
    def reads(json: JsValue): JsResult[URL] = JsSuccess(new URL(json.toString()))
    def writes(url: URL): JsValue = Json.toJson(url.toExternalForm)
  }

  implicit val dateFormat = new Format[Date] {
    def reads(json: JsValue): JsResult[Date] = JsSuccess(json.as[Date])
    def writes(date: Date): JsValue = Json.toJson(Parsers.toISO8601(date))
  }

  implicit val extractorInfoWrites = Json.writes[ExtractorInfo]

  implicit val extractorInfoReads: Reads[ExtractorInfo] = (
    (JsPath \ "id").read[UUID].orElse(Reads.pure(UUID.generate())) and
      (JsPath \ "name").read[String] and
      (JsPath \ "version").read[String] and
      Reads.pure(Calendar.getInstance().getTime) and
      (JsPath \ "description").read[String] and
      (JsPath \ "author").read[String] and
      (JsPath \ "contributors").read[List[String]].orElse(Reads.pure(List.empty)) and
      (JsPath \ "contexts").read[JsValue] and
      (JsPath \ "repository").read[List[Repository]] and
      (JsPath \ "external_services").read[List[String]].orElse(Reads.pure(List.empty)) and
      (JsPath \ "libraries").read[List[String]].orElse(Reads.pure(List.empty)) and
      (JsPath \ "bibtex").read[List[String]].orElse(Reads.pure(List.empty)) and
      (JsPath \ "maturity").read[String].orElse(Reads.pure("Development")) and
      (JsPath \ "process").read[ExtractorProcessTriggers].orElse(Reads.pure(new ExtractorProcessTriggers())) and
      (JsPath \ "categories").read[List[String]].orElse(Reads.pure(List[String](ExtractorCategory.EXTRACT.toString))) and
      (JsPath \ "parameters").read[JsValue].orElse(Reads.pure(JsObject(Seq())))
    )(ExtractorInfo.apply _)
}



/**
 * Source code repository
 *
 * @param repType git, hg, svn
 * @param repUrl the url of the repository, for example https://opensource.ncsa.illinois.edu/stash/scm/cats/clowder.git
 */
case class Repository(repType: String, repUrl: String)



/**
  * Events that should trigger this extractor to begin to process.
  *
  * @see https://opensource.ncsa.illinois.edu/confluence/display/CATS/Extractors#Extractors-Extractorbasics for
  * a list of possible event Strings.
  *
  * @param dataset List of event Strings associated with dataset addition / removal
  * @param file List of event Strings associated with file uploads
  * @param metadata List of event Strings associated with metadata addition / removal
  */
case class ExtractorProcessTriggers(dataset: List[String] = List.empty,
                                    file: List[String] = List.empty,
                                    metadata: List[String] = List.empty)


case class ExtractionGroup(
                          firstMsgTime: String,
                          latestMsgTime: String,
                          latestMsg: String,
                          allMsgs: Map[UUID, List[Extraction]]
                          )

case class ExtractorsLabel(
                            id: UUID,
                            name: String,
                            category: Option[String],
                            extractors: List[String]
                         )

object ExtractorsLabel {
  implicit val extractorsLabelWrites = Json.writes[ExtractorsLabel]

  /*implicit val extractorsLabelReads: Reads[ExtractorsLabel] = (
    (JsPath \ "id").read[UUID] and
      (JsPath \ "name").read[String] and
      (JsPath \ "category").read[String] and
      (JsPath \ "extractors").read[List[String]].orElse(Reads.pure(List.empty))
    )(ExtractorsLabel.apply _)*/
}