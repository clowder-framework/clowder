package services

import models.{Dataset, Extraction, File, Preview, ResourceRef, ResourceType, TempFile, UUID, User}
import play.api.Logger
import play.api.libs.json.{JsArray, JsObject, JsString, JsValue}

import scala.collection.mutable.ListBuffer

/**
 * Determine automated extraction messages to send based on Clowder events such as file upload.
 */

class ExtractorRoutingService {

  /**
   * Escape various characters in content type when creating a routing key
   * @param contentType original content type in standar form, for example text/csv
   * @return escaped routing key
   */
  def contentTypeToRoutingKey(contentType: String) =
    contentType.replace(".", "_").replace("/", ".")

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
  def containsOperation(operations: List[String], operation: String): Boolean = {
    val optypes: Array[String] = operation.split("[/.]")
    (optypes.length == 2) && {
      val opmaintype: String = optypes(0)
      val opsubtype: String = optypes(1)
      operations.exists {
        elem => {
          val types: Array[String] = elem.split("[/.]")
          (types.length == 2 && (types(0) == "*" || types(0) == opmaintype) && (types(1) == "*" || types(1) == opsubtype))
        }
      }
    }
  }

  /**
   * a helper function to get user email address from user's request api key.
   * @param requestAPIKey user request apikey
   * @return a list of email address
   */
  def getEmailNotificationEmailList(requestAPIKey: Option[String]): List[String] = {
    val userService = DI.injector.getInstance(classOf[UserService])

    (for {
      apiKey <- requestAPIKey
      user <- userService.findByKey(apiKey)
      email <- user.email
    } yield {
      Logger.debug(s"[getEmailNotificationEmailList] $email")
      List[String](email)
    }).getOrElse(List[String]())
  }

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
  private def getMatchingExtractors(extractorIds: List[String], operation: String, resourceType: ResourceType.Value): List[String] = {
    val extractorsService = DI.injector.getInstance(classOf[ExtractorService])

    extractorsService.getEnabledExtractors().flatMap(exId =>
      extractorsService.getExtractorInfo(exId)).filter(exInfo =>
      resourceType match {
        case ResourceType.dataset =>
          containsOperation(exInfo.process.dataset, operation)
        case ResourceType.file =>
          containsOperation(exInfo.process.file, operation)
        case ResourceType.metadata =>
          containsOperation(exInfo.process.metadata, operation)
        case _ =>
          false
      }
    ).map(_.name)
  }

  /**
   * Find all extractors enabled/disabled for the space the dataset belongs and the matched operation.
   * @param dataset  The dataset used to find which space to query for registered extractors.
   * @param operation The dataset operation requested.
   * @return A list of extractors IDs.
   */
  private def getSpaceExtractorsByOperation(dataset: Dataset, operation: String, resourceType: ResourceType.Value): (List[String], List[String]) = {
    val spacesService = DI.injector.getInstance(classOf[SpaceService])

    var enabledExtractors = new ListBuffer[String]()
    var disabledExtractors = new ListBuffer[String]()
    dataset.spaces.map(space => {
      spacesService.getAllExtractors(space).foreach { extractors =>
        enabledExtractors.appendAll(getMatchingExtractors(extractors.enabled, operation, resourceType))
        disabledExtractors.appendAll(getMatchingExtractors(extractors.disabled, operation, resourceType))
      }
    })
    (enabledExtractors.toList, disabledExtractors.toList)
  }

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
  private def getQueuesFromBindings(routingKey: String): List[String] = {
    val messages = DI.injector.getInstance(classOf[MessageService])

    // While the routing key includes the instance name the rabbitmq bindings has a *.
    // TODO this code could be improved by having less options in how routes and keys are represented
    val fragments = routingKey.split('.')
    val key1 = "*."+fragments.slice(1,fragments.size).mkString(".")
    val key2 = "*."+fragments.slice(1,fragments.size - 1).mkString(".") + ".#"
    val key3 = "*."+fragments(1)+".#"
    val key4 = "*."+fragments.slice(1,fragments.size).mkString(".") + ".#"
    messages.bindings.filter(x => Set(key1, key2, key3, key4).contains(x.routing_key)).map(_.destination)
  }

  /**
   * Establish which queues a message should be sent to based on which extractors are enabled for a space/instance
   * and the old topic exchanges. Eventually this the topic bindings will go away and the queues will only be selected
   * based on extractors enabled for a space/instance.
   * @param dataset the datasets used to figure out what space this resource belongs to
   * @param routingKey old routing key, still used to identify event type
   * @param contentType the content type of the file in the case of a file
   * @return a set of unique rabbitmq queues
   */
  private def getQueues(dataset: Dataset, routingKey: String, contentType: String): Set[String] = {
    val extractorsService = DI.injector.getInstance(classOf[ExtractorService])

    // drop the first fragment from the routing key and replace characters to create operation id
    val fragments = routingKey.split('.')
    val (resourceType, operation) =
      if (fragments(1) == "dataset")
        (ResourceType.dataset, fragments(2) + "." + fragments(3))
      else if (fragments(1) == "metadata")
        (ResourceType.metadata, fragments(2) + "." + fragments(3))
      else if (fragments(1) == "file")
        (ResourceType.file, fragments(2) + "/" + fragments(3))
      else
        return Set.empty[String]
    // get extractors enabled at the global level
    val globalExtractors = getMatchingExtractors(extractorsService.getEnabledExtractors(), operation, resourceType)
    // get extractors enabled/disabled at the space level
    val (enabledExtractors, disabledExtractors) = getSpaceExtractorsByOperation(dataset, operation, resourceType)
    // get queues based on RabbitMQ bindings (old method).
    val queuesFromBindings = getQueuesFromBindings(routingKey)
    // take the union of queues so that we publish to a specific queue only once
    val selected = (globalExtractors.toSet -- disabledExtractors.toSet) union enabledExtractors.toSet union queuesFromBindings.toSet
    Logger.debug("Extractors selected for submission: " + selected)
    selected
  }

  /**
   * Post the event of SUBMITTED
   * @param file_id the UUID of file
   * @param extractor_id the extractor queue name to be submitted
   */
  def postSubmissionEvent(file_id: UUID, extractor_id: String, user_id: UUID): (UUID, Option[UUID]) = {
    val extractions: ExtractionService = DI.injector.getInstance(classOf[ExtractionService])

    import java.text.SimpleDateFormat
    val dateFormatter = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssX")
    val submittedDateConvert = new java.util.Date()
    val job_id = Some(UUID.generate())
    extractions.insert(Extraction(UUID.generate(), file_id, job_id, extractor_id, "SUBMITTED", submittedDateConvert, None, user_id)) match {
      case Some(objectid) => (UUID(objectid.toString), job_id)
      case None => (UUID(""), job_id)
    }
  }


  /** Return the API key to use in the submission. If the one in the key is not set in the request then get the default
   * extraction key for the user. If the user is not defined default to the global key.
   * for the user
   *
   * @param requestAPIKey the API key from the request
   * @param user the user from the request
   * @return the API key to use
   */
  def getApiKey(requestAPIKey: Option[String], user: Option[User]): String = {
    val userService: UserService = DI.injector.getInstance(classOf[UserService])
    val messages: MessageService = DI.injector.getInstance(classOf[MessageService])

    if (requestAPIKey.isDefined)
      requestAPIKey.get
    else if (user.isDefined)
      userService.getExtractionApiKey(user.get.identityId).key
    else
      messages.getGlobalKey
  }

  /**
   * Publish to the proper queues when a new file is uploaded to the system.
   * @param file the file that was just uploaded
   * @param dataset the dataset the file belongs to
   * @param host the Clowder host URL for sharing extractors across instances
   */
  def fileCreated(file: File, dataset: Option[Dataset], host: String, requestAPIKey: Option[String]): Option[UUID] = {
    val userService = DI.injector.getInstance(classOf[UserService])
    val messages = DI.injector.getInstance(classOf[MessageService])

    val routingKey = messages.getExchange + "." + "file." + contentTypeToRoutingKey(file.contentType)
    val extraInfo = Map("filename" -> file.filename)
    val apiKey = requestAPIKey.getOrElse(messages.getGlobalKey)
    val user = userService.findByKey(apiKey).getOrElse(User.anonymous)
    val sourceExtra = JsObject((Seq("filename" -> JsString(file.filename))))
    Logger.debug(s"Sending message $routingKey from $host with extraInfo $extraInfo")
    var jobId: Option[UUID] = None
    dataset match {
      case Some(d) => {
        getQueues(d, routingKey, file.contentType).foreach { queue =>
          val source = Entity(ResourceRef(ResourceRef.file, file.id), Some(file.contentType), sourceExtra)

          val notifies = getEmailNotificationEmailList(requestAPIKey)

          val (id, job_id) = postSubmissionEvent(file.id, queue, user.id)

          val msg = ExtractorMessage(id, file.id, job_id, notifies, file.id, host, queue, extraInfo, file.length.toString,
            d.id, "", apiKey, routingKey, source, "created", None)
          messages.submit(msg)
          jobId = job_id
        }
        jobId
      }
      case None => {
        Logger.debug("RabbitMQPlugin: No dataset associated with this file")
        None
      }
    }
  }

  /**
   * Send message when a new file is uploaded to the system. This is the same as the method above but
   * it supports TempFile instead of File. This is currently only used for multimedia queries.
   * @param file the file that was just uploaded
   * @param host the Clowder host URL for sharing extractors across instances
   */
  def fileCreated(file: TempFile, host: String, requestAPIKey: Option[String]): Unit = {
    val userService = DI.injector.getInstance(classOf[UserService])
    val messages = DI.injector.getInstance(classOf[MessageService])

    val routingKey = messages.getExchange + "." + "file." + contentTypeToRoutingKey(file.contentType)
    val extraInfo = Map("filename" -> file.filename)
    val apiKey = requestAPIKey.getOrElse(messages.getGlobalKey)
    val user = userService.findByKey(apiKey).getOrElse(User.anonymous)
    Logger.debug(s"Sending message $routingKey from $host with extraInfo $extraInfo")
    val sourceExtra = JsObject((Seq("filename" -> JsString(file.filename))))
    val source = Entity(ResourceRef(ResourceRef.file, file.id), Some(file.contentType), sourceExtra)
    val notifies = getEmailNotificationEmailList(requestAPIKey)
    val (id, job_id) = postSubmissionEvent(file.id, routingKey, user.id)
    val msg = ExtractorMessage(id, file.id, job_id, notifies, file.id, host, routingKey, extraInfo, file.length.toString, null,
      "", apiKey, routingKey, source, "created", None)
    messages.submit(msg)
  }

  /**
   * Send message when a file is added to a dataset. Use both old method using topic queues and new method using work
   * queues and extractors registration in Clowder.
   * @param file the file that was added to the dataset
   * @param dataset the dataset it was added to
   * @param host the Clowder host URL for sharing extractors across instances
   */
  def fileAddedToDataset(file: File, dataset: Dataset, host: String, requestAPIKey: Option[String]): Unit = {
    val userService = DI.injector.getInstance(classOf[UserService])
    val messages = DI.injector.getInstance(classOf[MessageService])

    val routingKey = s"${messages.getExchange}.dataset.file.added"
    Logger.debug(s"Sending message $routingKey from $host")
    val queues = getQueues(dataset, routingKey, file.contentType)
    val apiKey = requestAPIKey.getOrElse(messages.getGlobalKey)
    val user = userService.findByKey(apiKey).getOrElse(User.anonymous)
    val sourceExtra = JsObject((Seq("filename" -> JsString(file.filename))))
    queues.foreach{ extractorId =>
      val source = Entity(ResourceRef(ResourceRef.file, file.id), Some(file.contentType), sourceExtra)
      val target = Entity(ResourceRef(ResourceRef.dataset, dataset.id), None, JsObject(Seq.empty))

      val notifies = getEmailNotificationEmailList(requestAPIKey)

      val (id, job_id) = postSubmissionEvent(file.id, extractorId, user.id)

      val msg = ExtractorMessage(id, file.id, job_id, notifies, file.id, host, extractorId, Map.empty, file.length.toString, dataset.id,
        "", apiKey, routingKey, source, "added", Some(target))
      messages.submit(msg)
    }
  }

  /**
   * Send message when a group of files is added to a dataset via UI.
   * Use both old method using topic queues and new method using work queues and extractors registration in Clowder.
   * @param filelist the list of files that were added to the dataset
   * @param dataset the dataset it was added to
   * @param host the Clowder host URL for sharing extractors across instances
   */
  def fileSetAddedToDataset(dataset: Dataset, filelist: List[File], host: String, requestAPIKey: Option[String]): Unit = {
    val userService = DI.injector.getInstance(classOf[UserService])
    val messages = DI.injector.getInstance(classOf[MessageService])

    val routingKey = s"${messages.getExchange}.dataset.files.added"
    Logger.debug(s"Sending message $routingKey from $host")
    val queues = getQueues(dataset, routingKey, "")
    val apiKey = requestAPIKey.getOrElse(messages.getGlobalKey)
    val user = userService.findByKey(apiKey).getOrElse(User.anonymous)
    val sourceExtra = JsObject((Seq("filenames" -> JsArray(filelist.map(f=>JsString(f.filename)).toSeq))))
    val msgExtra = Map("filenames" -> filelist.map(f=>f.filename))
    queues.foreach{ extractorId =>
      val source = Entity(ResourceRef(ResourceRef.dataset, dataset.id), None, sourceExtra)

      val notifies = getEmailNotificationEmailList(requestAPIKey)

      val (id, job_id) = postSubmissionEvent(dataset.id, extractorId, user.id)

      var totalsize: Long = 0
      filelist.map(f => totalsize += f.length)
      val msg = ExtractorMessage(id, dataset.id, job_id, notifies, dataset.id, host, extractorId, msgExtra, totalsize.toString, dataset.id,
        "", apiKey, routingKey, source, "added", None)
      messages.submit(msg)
    }
  }

  /**
   * Send message when file is removed from a dataset and deleted.
   * @param file
   * @param dataset
   * @param host
   */
  def fileRemovedFromDataset(file: File, dataset: Dataset, host: String, requestAPIKey: Option[String]): Unit = {
    val userService = DI.injector.getInstance(classOf[UserService])
    val messages = DI.injector.getInstance(classOf[MessageService])

    val routingKey = s"${messages.getExchange}.dataset.file.removed"
    Logger.debug(s"Sending message $routingKey from $host")
    val queues = getQueues(dataset, routingKey, file.contentType)
    val apiKey = requestAPIKey.getOrElse(messages.getGlobalKey)
    val user = userService.findByKey(apiKey).getOrElse(User.anonymous)
    val sourceExtra = JsObject((Seq("filename" -> JsString(file.filename))))
    queues.foreach{ extractorId =>
      val source = Entity(ResourceRef(ResourceRef.file, file.id), Some(file.contentType), sourceExtra)
      val target = Entity(ResourceRef(ResourceRef.dataset, dataset.id), None, JsObject(Seq.empty))

      val notifies = getEmailNotificationEmailList(requestAPIKey)
      val (id, job_id) = postSubmissionEvent(file.id, extractorId, user.id)
      val msg = ExtractorMessage(id, file.id, job_id, notifies, file.id, host, extractorId, Map.empty, file.length.toString, dataset.id,
        "", apiKey, routingKey, source, "removed", Some(target))
      messages.submit(msg)
    }
  }

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
                         datasetId: UUID, newFlags: String, requestAPIKey: Option[String], user: Option[User]): Option[UUID] = {
    Logger.debug(s"Sending message to $queue from $host with extraInfo $extraInfo")
    val messages = DI.injector.getInstance(classOf[MessageService])

    val apiKey = getApiKey(requestAPIKey, user)
    val sourceExtra = JsObject((Seq("filename" -> JsString(file.filename))))
    val source = Entity(ResourceRef(ResourceRef.file, originalId), Some(file.contentType), sourceExtra)

    val notifies = getEmailNotificationEmailList(requestAPIKey)
    val (id, job_id) = postSubmissionEvent(file.id, queue, user.getOrElse(User.anonymous).id)
    val msg = ExtractorMessage(id, file.id, job_id, notifies, file.id, host, queue, extraInfo, file.length.toString, datasetId,
      "", apiKey, "extractors." + queue, source, "submitted", None)
    messages.submit(msg)
    job_id
  }

  /**
   * An existing dataset was manually submitted to the extraction bus by a user.
   * @param host
   * @param queue
   * @param extraInfo
   * @param datasetId
   * @param newFlags
   */
  def submitDatasetManually(host: String, queue: String, extraInfo: Map[String, Any], datasetId: UUID, newFlags: String,
                            requestAPIKey: Option[String], user: Option[User]): Option[UUID] = {
    Logger.debug(s"Sending message $queue from $host with extraInfo $extraInfo")
    val messages = DI.injector.getInstance(classOf[MessageService])

    val apiKey = getApiKey(requestAPIKey, user)
    val source = Entity(ResourceRef(ResourceRef.dataset, datasetId), None, JsObject(Seq.empty))

    val notifies = getEmailNotificationEmailList(requestAPIKey)

    val (id, job_id) = postSubmissionEvent(datasetId, queue, user.getOrElse(User.anonymous).id)
    val msg = ExtractorMessage(id, datasetId, job_id, notifies, datasetId, host, queue, extraInfo, 0.toString, datasetId,
      "", apiKey, "extractors." + queue, source, "submitted", None)
    messages.submit(msg)
    job_id
  }

  /**
   * Metadata added to a resource (file or dataset).
   * @param resourceRef
   * @param extraInfo
   * @param host
   */
  // FIXME check if extractor is enabled in space or global
  def metadataAddedToResource(metadataId: UUID, resourceRef: ResourceRef, extraInfo: Map[String, Any], host: String,
                              requestAPIKey: Option[String], user: Option[User]): Unit = {
    val datasetService = DI.injector.getInstance(classOf[DatasetService])
    val files = DI.injector.getInstance(classOf[FileService])
    val messages = DI.injector.getInstance(classOf[MessageService])

    val routingKey = s"${messages.getExchange}.metadata.added.${resourceRef.resourceType.name}"
    Logger.debug(s"Sending message $routingKey from $host with extraInfo $extraInfo")
    val apiKey = getApiKey(requestAPIKey, user)

    val notifies = getEmailNotificationEmailList(requestAPIKey)

    resourceRef.resourceType match {
      // metadata added to dataset
      case ResourceRef.dataset =>
        datasetService.get(resourceRef.id) match {
          case Some(dataset) =>
            getQueues(dataset, routingKey, "").foreach { extractorId =>
              val source = Entity(ResourceRef(ResourceRef.metadata, metadataId), None, JsObject(Seq.empty))
              val target = Entity(resourceRef, None, JsObject(Seq.empty))

              val (id, job_id) = postSubmissionEvent(resourceRef.id, extractorId, user.getOrElse(User.anonymous).id)
              val msg = ExtractorMessage(id, resourceRef.id, job_id, notifies, resourceRef.id, host, extractorId, extraInfo, 0.toString, resourceRef.id,
                "", apiKey, routingKey, source, "added", Some(target))
              messages.submit(msg)
            }
          case None =>
            Logger.error(s"Resource ${resourceRef.id} of type ${resourceRef.resourceType} not found.")
        }
      // metadata added to file
      case ResourceRef.file =>
        val datasets = datasetService.findByFileIdAllContain(resourceRef.id)
        val fileType = files.get(resourceRef.id) match { case Some(f)=> f.contentType case None => ""}
        datasets.foreach { dataset =>
          getQueues(dataset, routingKey, fileType).foreach { extractorId =>
            val source = Entity(ResourceRef(ResourceRef.metadata, metadataId), None, JsObject(Seq.empty))
            val target = Entity(resourceRef, None, JsObject(Seq.empty))

            val (id, job_id) = postSubmissionEvent(resourceRef.id, extractorId, user.getOrElse(User.anonymous).id)
            val msg = ExtractorMessage(id, resourceRef.id, job_id, notifies, resourceRef.id, host, extractorId, extraInfo, 0.toString, null,
              "", apiKey, routingKey, source, "added", Some(target))
            messages.submit(msg)
          }
        }
      case _ =>
        Logger.error(s"Unrecognized resource type ${resourceRef.resourceType} when sending out metadata added event.")
    }
  }

  /**
   * Metadata removed from a resource (file or dataset).
   * @param resourceRef
   * @param host
   */
  // FIXME check if extractor is enabled in space or global
  def metadataRemovedFromResource(metadataId: UUID, resourceRef: ResourceRef, host: String, requestAPIKey: Option[String], user: Option[User]): Unit = {
    val datasetService = DI.injector.getInstance(classOf[DatasetService])
    val files = DI.injector.getInstance(classOf[FileService])
    val messages = DI.injector.getInstance(classOf[MessageService])

    val routingKey = s"${messages.getExchange}.metadata.removed.${resourceRef.resourceType.name}"
    val apiKey = getApiKey(requestAPIKey, user)
    val extraInfo = Map[String, Any](
      "resourceType"->resourceRef.resourceType.name,
      "resourceId"->resourceRef.id.toString)
    Logger.debug(s"Sending message $routingKey from $host with extraInfo $extraInfo")
    val notifies = getEmailNotificationEmailList(requestAPIKey)

    resourceRef.resourceType match {
      // metadata added to dataset
      case ResourceRef.dataset =>
        datasetService.get(resourceRef.id) match {
          case Some(dataset) =>
            getQueues(dataset, routingKey, "").foreach { extractorId =>
              val source = Entity(ResourceRef(ResourceRef.metadata, metadataId), None, JsObject(Seq.empty))
              val target = Entity(resourceRef, None, JsObject(Seq.empty))

              val (id, job_id) = postSubmissionEvent(resourceRef.id, extractorId, user.getOrElse(User.anonymous).id)
              val msg = ExtractorMessage(id, resourceRef.id, job_id, notifies, resourceRef.id, host, extractorId, extraInfo, 0.toString, resourceRef.id,
                "", apiKey, routingKey, source, "removed", Some(target))
              messages.submit(msg)
            }
          case None =>
            Logger.error(s"Resource ${resourceRef.id} of type ${resourceRef.resourceType} not found.")
        }
      // metadata added to file
      case ResourceRef.file =>
        val datasets = datasetService.findByFileIdAllContain(resourceRef.id)
        val fileType = files.get(resourceRef.id) match { case Some(f)=> f.contentType case None => ""}
        datasets.foreach { dataset =>
          getQueues(dataset, routingKey, fileType).foreach { extractorId =>
            val source = Entity(ResourceRef(ResourceRef.metadata, metadataId), None, JsObject(Seq.empty))
            val target = Entity(resourceRef, None, JsObject(Seq.empty))

            val (id, job_id) = postSubmissionEvent(resourceRef.id, extractorId, user.getOrElse(User.anonymous).id)
            val msg = ExtractorMessage(id, resourceRef.id, job_id, notifies, resourceRef.id, host, extractorId, extraInfo, 0.toString, null,
              "", apiKey, routingKey, source, "removed", Some(target))
            messages.submit(msg)
          }
        }
      case _ =>
        Logger.error(s"Unrecognized resource type ${resourceRef.resourceType} when sending out metadata added event.")
    }
  }

  /**
   * File upladed for multimedia query. Not a common used feature.
   * @param tempFileId
   * @param contentType
   * @param length
   * @param host
   */
  def multimediaQuery(tempFileId: UUID, contentType: String, length: String, host: String, requestAPIKey: Option[String]): Unit = {
    //key needs to contain 'query' when uploading a query
    //since the thumbnail extractor during processing will need to upload to correct mongo collection.
    val userService = DI.injector.getInstance(classOf[UserService])
    val messages = DI.injector.getInstance(classOf[MessageService])

    val routingKey = messages.getExchange +".query." + contentType.replace("/", ".")
    val apiKey = requestAPIKey.getOrElse(messages.getGlobalKey)
    val user = userService.findByKey(apiKey).getOrElse(User.anonymous)
    Logger.debug(s"Sending message $routingKey from $host")

    val notifies = getEmailNotificationEmailList(requestAPIKey)
    val source = Entity(ResourceRef(ResourceRef.file, tempFileId), Some(contentType), JsObject(Seq.empty))

    val (id, job_id) = postSubmissionEvent(tempFileId, routingKey, user.id)
    val msg = ExtractorMessage(id, tempFileId, job_id, notifies, tempFileId, host, routingKey, Map.empty[String, Any], length, null,
      "", apiKey, routingKey, source, "created", None)
    messages.submit(msg)
  }

  /**
   * Preview creted for section.
   * @param preview
   * @param sectionId
   * @param host
   */
  def submitSectionPreviewManually(preview: Preview, sectionId: UUID, host: String, requestAPIKey: Option[String]): Unit = {
    val userService = DI.injector.getInstance(classOf[UserService])
    val messages = DI.injector.getInstance(classOf[MessageService])

    val routingKey = messages.getExchange + ".index."+ contentTypeToRoutingKey(preview.contentType)
    val apiKey = requestAPIKey.getOrElse(messages.getGlobalKey)
    val user = userService.findByKey(apiKey).getOrElse(User.anonymous)
    val extraInfo = Map("section_id"->sectionId)
    val source = Entity(ResourceRef(ResourceRef.preview, preview.id), None, JsObject(Seq.empty))
    val target = Entity(ResourceRef(ResourceRef.section, sectionId), None, JsObject(Seq.empty))
    val notifies = getEmailNotificationEmailList(requestAPIKey)

    val (id, job_id) = postSubmissionEvent(sectionId, routingKey, user.id)
    val msg = ExtractorMessage(id, sectionId, job_id, notifies, sectionId, host, routingKey, extraInfo, 0.toString, null,
      "", apiKey, routingKey, source, "added", Some(target))
    messages.submit(msg)
  }

}