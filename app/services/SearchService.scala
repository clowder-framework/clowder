package services

import org.elasticsearch.common.xcontent.XContentBuilder
import scala.collection.immutable.List
import models.{Collection, Dataset, File, UUID, ResourceRef, Section, ElasticsearchResult, User}
import play.api.libs.json._


/**
 * Searching and indexing service.
 *
 */
trait SearchService {

  def onStart()

  def onStop()

  def connect(force:Boolean = false): Boolean

  def isEnabled(): Boolean

  def getInformation(): JsObject

  /** Prepare and execute Elasticsearch query, and return list of matching ResourceRefs */
  def search(query: List[JsValue], grouping: String, from: Option[Int], size: Option[Int], user: Option[User]): ElasticsearchResult

  /** Search using a simple text string, appending parameters from API to string if provided */
  def search(query: String, resource_type: Option[String], datasetid: Option[String], collectionid: Option[String],
             spaceid: Option[String], folderid: Option[String], field: Option[String], tag: Option[String],
             from: Option[Int], size: Option[Int], permitted: List[UUID], user: Option[User]): ElasticsearchResult

  /** Perform search, check permissions, and keep searching again if page isn't filled with permitted resources */
  def accumulatePageResult(queryObj: XContentBuilder, user: Option[User], from: Int, size: Int): ElasticsearchResult

  /** Return a filtered list of resources that user can actually access */
  def checkResultPermissions(results: List[ResourceRef], user: Option[User]): List[ResourceRef]

  def createIndex()

  /** Delete all indices */
  def deleteAll()

  /** Delete an index */
  def delete(id: String, docType: String = "clowder_object")

  /** Traverse metadata field mappings to get unique list for autocomplete */
  def getAutocompleteMetadataFields(query: String): List[String]

  /**
   * Reindex the given collection, if recursive is set to true it will
   * also reindex all datasets and files.
   */
  def index(collection: Collection, recursive: Boolean)

  /**
   * Reindex the given dataset, if recursive is set to true it will
   * also reindex all files.
   */
  def index(dataset: Dataset, recursive: Boolean)

  /** Reindex the given file. */
  def index(file: File)

  def index(section: Section)

  /** Index document using an arbitrary map of fields. */
  def index(esObj: Option[models.ElasticsearchObject])

  /** Return map of distinct value/count for tags **/
  def listTags(resourceType: String = ""): Map[String, Long]


  /** Take a JsObject and parse into an XContentBuilder JSON object for indexing into Elasticsearch */
  def convertJsObjectToBuilder(builder: XContentBuilder, json: JsObject): XContentBuilder

  /** Take a JsObject and list all unique fields under targetObject field, except those in ignoredFields */
  def convertJsMappingToFields(json: JsObject, parentKey: Option[String] = None,
                               targetObject: Option[String] = None, foundTarget: Boolean = false): List[String]

  /** Return string-encoded JSON object describing field types */
  def getElasticsearchObjectMappings(): String

  /**Attempt to cast String into Double, returning None if not possible**/
  def parseDouble(s: String): Option[Double]

  /** Create appropriate search object based on operator */
  def parseMustOperators(builder: XContentBuilder, key: String, value: String, operator: String): XContentBuilder

  /** Create appropriate search object based on operator */
  def parseMustNotOperators(builder: XContentBuilder, key: String, value: String, operator: String): XContentBuilder

  /**Convert list of search term JsValues into an Elasticsearch-ready JSON query object**/
  def prepareElasticJsonQuery(query: List[JsValue], grouping: String): XContentBuilder

  /**Convert search string into an Elasticsearch-ready JSON query object**/
  def prepareElasticJsonQuery(query: String, permitted: List[UUID]): XContentBuilder

  def wrapRegex(value: String, query_string: Boolean = false): String

}