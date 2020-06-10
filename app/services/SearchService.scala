package services

import org.elasticsearch.common.xcontent.XContentBuilder
import scala.collection.immutable.List
import models.{Collection, Dataset, File, TempFile, UUID, ResourceRef, Section, SearchResult, User}
import play.api.libs.json._


/**
 * Searching and indexing service.
 *
 */
trait SearchService {
  val queueName = "search"

  def isEnabled(): Boolean

  def getInformation(): JsObject

  /** Prepare and execute Elasticsearch query, and return list of matching ResourceRefs */
  def search(query: List[JsValue], grouping: String, from: Option[Int], size: Option[Int], user: Option[User]): SearchResult

  /** Search using a simple text string, appending parameters from API to string if provided */
  def search(query: String, resource_type: Option[String], datasetid: Option[String], collectionid: Option[String],
             spaceid: Option[String], folderid: Option[String], field: Option[String], tag: Option[String],
             from: Option[Int], size: Option[Int], permitted: List[UUID], user: Option[User]): SearchResult

  def createIndex(index: String)

  // Swap default index with provided temporary reindex
  def swapIndex()

  /** Delete all indices */
  def deleteAll(idx: String)

  /** Delete an index */
  def delete(id: String, docType: String = "clowder_object")

  /** Traverse metadata field mappings to get unique list for autocomplete */
  def getAutocompleteMetadataFields(query: String): List[String]

  /**
   * Reindex using a resource reference and route to correct handler
   */
  def index(resource: ResourceRef, recursive: Boolean = true, idx: Option[String])

  /**
   * Reindex the given collection, if recursive is set to true it will
   * also reindex all datasets and files.
   */
  def index(collection: Collection, recursive: Boolean, idx: Option[String])

  /**
   * Reindex the given dataset, if recursive is set to true it will
   * also reindex all files.
   */
  def index(dataset: Dataset, recursive: Boolean, idx: Option[String])

  /** Reindex the given file. */
  def index(file: File, idx: Option[String])

  def index(file: TempFile, idx: Option[String])

  def index(section: Section, idx: Option[String])

  def indexAll(): String

  /** Return map of distinct value/count for tags **/
  def listTags(resourceType: String = ""): Map[String, Long]
}