package services

import models.{Dataset, File, Preview, ResourceRef, ResourceType, TempFile, UUID, User}
import play.api.Logger
import play.api.libs.json.{JsObject, JsValue}

import scala.collection.mutable.ListBuffer

/**
 * Determine automated extraction messages to send based on Clowder events such as file upload.
 */

trait ExtractorRoutingService {

  /**
   * Escape various characters in content type when creating a routing key
   * @param contentType original content type in standar form, for example text/csv
   * @return escaped routing key
   */
  def contentTypeToRoutingKey(contentType: String)

  /**
   * Check if given operation matches any existing records cached in ExtractorInfo.
   * Note, dataset operation is in the format of "x.y",
   *       mimetype of files is in the format of "x/y"
   *       this functino expects to parse on delimeters: . or /
   *       this function will return false in case of wrong format
   * @param operations mimetypes cached in ExtractorInfo, either operations of dataset or mimetypes of files
   * @param operation dataset operation like "file.added" or mimetype of files, like "image/bmp"
   * @return true if matches any existing recorder. otherwise, false.
   */
  def containsOperation(operations: List[String], operation: String): Boolean

  /**
   * Given a list of extractor ids, an operation and a resource type return the list of extractor ids that match.
   *
   * TODO Should operation and resourceType be combined?
   *
   * @param extractorIds list of extractors to filter
   * @param operation the extraction even to filter on
   * @param resourceType the type of resource to check
   * @return filtered list of extractors
   */
  def getMatchingExtractors(extractorIds: List[String], operation: String, resourceType: ResourceType.Value): List[String]

  /**
   * Find all extractors enabled/disabled for the space the dataset belongs and the matched operation.
   * @param dataset  The dataset used to find which space to query for registered extractors.
   * @param operation The dataset operation requested.
   * @return A list of extractors IDs.
   */
  def getSpaceExtractorsByOperation(dataset: Dataset, operation: String, resourceType: ResourceType.Value): (List[String], List[String])

  /**
   * Query the list of bindings loaded at startup for which binding matches the routing key and extract the destination
   * queues. This is used for files bindings. For a mime type such as image/jpeg it will match the following routing keys:
   * *.file.image.jpeg
   * *.file.image.#
   * *.file.#
   *
   * key4 is to match mime type for zip file's routing keys: e.g,
   * *.file.multi.files-zipped.#
   *
   * @param routingKey The binding routing key.
   * @return The list of queue matching the routing key.
   */
  def getQueuesFromBindings(routingKey: String): List[String]

  /**
   * Establish which queues a message should be sent to based on which extractors are enabled for a space/instance
   * and the old topic exchanges. Eventually this the topic bindings will go away and the queues will only be selected
   * based on extractors enabled for a space/instance.
   * @param dataset the datasets used to figure out what space this resource belongs to
   * @param routingKey old routing key, still used to identify event type
   * @param contentType the content type of the file in the case of a file
   * @return a set of unique rabbitmq queues
   */
  def getQueues(dataset: Dataset, routingKey: String, contentType: String): Set[String]

  /**
   * Post the event of SUBMITTED
   * @param file_id the UUID of file
   * @param extractor_id the extractor queue name to be submitted
   */
  def postSubmissionEvent(file_id: UUID, extractor_id: String, user_id: UUID): (UUID, Option[UUID])

  /** Return the API key to use in the submission. If the one in the key is not set in the request then get the default
   * extraction key for the user. If the user is not defined default to the global key.
   * for the user
   *
   * @param requestAPIKey the API key from the request
   * @param user the user from the request
   * @return the API key to use
   */
  def getApiKey(requestAPIKey: Option[String], user: Option[User]): String

  /**
   * Publish to the proper queues when a new file is uploaded to the system.
   * @param file the file that was just uploaded
   * @param dataset the dataset the file belongs to
   * @param host the Clowder host URL for sharing extractors across instances
   */
  def fileCreated(file: File, dataset: Option[Dataset], host: String, requestAPIKey: Option[String]): Option[UUID]

  /**
   * Send message when a new file is uploaded to the system. This is the same as the method above but
   * it supports TempFile instead of File. This is currently only used for multimedia queries.
   * @param file the file that was just uploaded
   * @param host the Clowder host URL for sharing extractors across instances
   */
  def fileCreated(file: TempFile, host: String, requestAPIKey: Option[String])

  /**
   * Send message when a file is added to a dataset. Use both old method using topic queues and new method using work
   * queues and extractors registration in Clowder.
   * @param file the file that was added to the dataset
   * @param dataset the dataset it was added to
   * @param host the Clowder host URL for sharing extractors across instances
   */
  def fileAddedToDataset(file: File, dataset: Dataset, host: String, requestAPIKey: Option[String])

  /**
   * Send message when a group of files is added to a dataset via UI.
   * Use both old method using topic queues and new method using work queues and extractors registration in Clowder.
   * @param filelist the list of files that were added to the dataset
   * @param dataset the dataset it was added to
   * @param host the Clowder host URL for sharing extractors across instances
   */
  def fileSetAddedToDataset(dataset: Dataset, filelist: List[File], host: String, requestAPIKey: Option[String])

  /**
   * Send message when file is removed from a dataset and deleted.
   * @param file
   * @param dataset
   * @param host
   */
  def fileRemovedFromDataset(file: File, dataset: Dataset, host: String, requestAPIKey: Option[String])

  /**
   * An existing file was manually submitted to the extraction bus by a user.
   * @param originalId
   * @param file
   * @param host
   * @param queue
   * @param extraInfo
   * @param datasetId
   * @param newFlags
   */
  def submitFileManually(originalId: UUID, file: File, host: String, queue: String, extraInfo: Map[String, Any],
                         datasetId: UUID, newFlags: String, requestAPIKey: Option[String], user: Option[User]): Option[UUID]

  /**
   * An existing dataset was manually submitted to the extraction bus by a user.
   * @param host
   * @param queue
   * @param extraInfo
   * @param datasetId
   * @param newFlags
   */
  def submitDatasetManually(host: String, queue: String, extraInfo: Map[String, Any], datasetId: UUID, newFlags: String,
                            requestAPIKey: Option[String], user: Option[User]): Option[UUID]

  /**
   * Metadata added to a resource (file or dataset).
   * @param resourceRef
   * @param extraInfo
   * @param host
   */
  // FIXME check if extractor is enabled in space or global
  def metadataAddedToResource(metadataId: UUID, resourceRef: ResourceRef, extraInfo: Map[String, Any], host: String,
                              requestAPIKey: Option[String], user: Option[User])

  /**
   * Metadata removed from a resource (file or dataset).
   * @param resourceRef
   * @param host
   */
  // FIXME check if extractor is enabled in space or global
  def metadataRemovedFromResource(metadataId: UUID, resourceRef: ResourceRef, host: String, requestAPIKey: Option[String], user: Option[User])

  /**
   * File upladed for multimedia query. Not a common used feature.
   * @param tempFileId
   * @param contentType
   * @param length
   * @param host
   */
  def multimediaQuery(tempFileId: UUID, contentType: String, length: String, host: String, requestAPIKey: Option[String])

  /**
   * Preview creted for section.
   * @param preview
   * @param sectionId
   * @param host
   */
  def submitSectionPreviewManually(preview: Preview, sectionId: UUID, host: String, requestAPIKey: Option[String])

}