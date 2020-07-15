package services

import java.io.IOException
import java.net.URI
import java.text.SimpleDateFormat
import java.net.URLEncoder

import akka.actor.{Actor, ActorRef, PoisonPill, Props}

import scala.concurrent.duration._
import scala.language.postfixOps
import com.ning.http.client.Realm.AuthScheme
import com.rabbitmq.client.AMQP.BasicProperties
import com.rabbitmq.client.AMQP.Queue
import com.rabbitmq.client._
import models._
import org.bson.types.ObjectId
import play.api.http.MimeTypes
import play.api.Play.current
import play.api.libs.json._
import play.api.libs.ws.{Response, WS}
import play.api.{Application, Logger, Plugin}
import play.libs.Akka
import securesocial.core.IdentityId

import scala.concurrent.{Await, Future}
import scala.util.Try

/**
  * Despite the `fileId` be named as such, it is currently serialized as `id` and used as the id of any resource in
  * question. So this will be the preview id in the case of messages operating on a preview, it will be the dataset
  * id in the case of dataset messages (yes there is also `datasetId`, pyclowder2 checks that the two are the same.
  * FIXME make optional fields Option[UUID]
  * FIXME rename fileId to id and add a resourceType field (dataset, file, preview, etc.)
  * TODO drop `intermediateId` or figure out how it works and use accordingly
  * @param fileId this should only be used as
  * @param intermediateId
  * @param host
  * @param queue
  * @param metadata
  * @param fileSize
  * @param datasetId
  * @param flags
  * @param secretKey
  */
case class ExtractorMessage(
  msgid: UUID,
  fileId: UUID,
  notifies: List[String],
  intermediateId: UUID,
  host: String,
  queue: String,
  metadata: Map[String, Any],
  fileSize: String,
  datasetId: UUID = null,
  flags: String = "",
  secretKey: String,
  // new fields
  routing_key: String,
  source: Entity,
  activity: String,
  target: Option[Entity]
)

// TODO add other optional fields
case class Entity(
  id: ResourceRef,
  mimeType: Option[String],
  extra: JsObject
)

case class CancellationMessage(id: UUID, queueName: String, msgid: UUID)

object Entity {
  implicit val implicitEntityWrites = Json.format[Entity]
}



/**
  * Rabbitmq service.
  */
class RabbitmqPlugin(application: Application) extends Plugin {
  val files: FileService = DI.injector.getInstance(classOf[FileService])
  val spacesService: SpaceService = DI.injector.getInstance(classOf[SpaceService])
  val extractorsService: ExtractorService = DI.injector.getInstance(classOf[ExtractorService])
  val datasetService: DatasetService = DI.injector.getInstance(classOf[DatasetService])
  val userService: UserService = DI.injector.getInstance(classOf[UserService])

  var channel: Option[Channel] = None
  var connection: Option[Connection] = None
  var factory: Option[ConnectionFactory] = None

  var extractQueue: Option[ActorRef] = None
  var cancellationQueue: Option[ActorRef] = None
  var event_filter: Option[ActorRef] = None
  var extractorsHeartbeats: Option[ActorRef] = None

  var bindings = List.empty[Binding]

  val cancellationDownloadQueueName: String = "clowder.jobs.temp"

  var restURL: Option[String] = None

  var vhost: String = ""
  var username: String = ""
  var password: String = ""
  var rabbitmquri: String = ""
  var exchange: String = ""
  var mgmtPort: String = ""

  var globalAPIKey = play.api.Play.configuration.getString("commKey").getOrElse("")

  /** On start connect to broker. */
  override def onStart() {
    Logger.info("Starting RabbitMQ Plugin")
    val configuration = play.api.Play.configuration
    rabbitmquri = configuration.getString("clowder.rabbitmq.uri").getOrElse("amqp://guest:guest@localhost:5672/%2f")
    exchange = configuration.getString("clowder.rabbitmq.exchange").getOrElse("clowder")
    mgmtPort = configuration.getString("clowder.rabbitmq.managmentPort").getOrElse("15672")
    Logger.debug("uri= "+ rabbitmquri)

    try {
      val uri = new URI(rabbitmquri)
      factory = Some(new ConnectionFactory())
      factory.get.setUri(uri)
    } catch {
      case t: Throwable => {
        factory = None
        Logger.error("Invalid URI for RabbitMQ", t)
      }
    }
  }

  /** On stop clean up connection. */
  override def onStop() {
    Logger.debug("Shutting down Rabbitmq Plugin")
    factory = None
    close()
  }

  /** Check if play plugin is enabled **/
  override lazy val enabled = {
    !application.configuration.getString("rabbitmqplugin").filter(_ == "disabled").isDefined
  }

  /** Close connection to broker. **/
  def close() {
    Logger.debug("Closing connection")
    restURL = None
    vhost = ""
    username = ""
    password = ""
    if (channel.isDefined) {
      Logger.debug("Channel closing")
      try {
        channel.get.close()
      } catch {
        case e: Exception => Logger.error("Error closing channel.", e)
      }
      channel = None
    }
    if (connection.isDefined) {
      Logger.debug("Connection closing")
      try {
        connection.get.close()
      } catch {
        case e: Exception => Logger.error("Error closing connection.", e)
      }
      connection = None
    }
    if (event_filter.isDefined) {
      event_filter.get ! PoisonPill
      event_filter = None
    }
    if (extractQueue.isDefined) {
      extractQueue.get ! PoisonPill
      extractQueue = None
    }
    if (cancellationQueue.isDefined) {
      cancellationQueue.get ! PoisonPill
      cancellationQueue = None
    }
    if (extractorsHeartbeats.isDefined) {
      extractorsHeartbeats.get ! PoisonPill
      extractorsHeartbeats = None
    }
  }

  def connect: Boolean = {
    if (channel.isDefined) return true
    if (!factory.isDefined) return true

    val configuration = play.api.Play.configuration

    try {
      val protocol = if (factory.get.isSSL) "https://" else "http://"
      restURL = Some(protocol + factory.get.getHost +  ":" + mgmtPort)
      vhost = URLEncoder.encode(factory.get.getVirtualHost)
      username = factory.get.getUsername
      password = factory.get.getPassword

      connection = Some(factory.get.newConnection())
      channel = Some(connection.get.createChannel())

      Logger.debug("vhost: "+ vhost)

      // setup exchange if provided
      if (exchange != "") {
        channel.get.exchangeDeclare(exchange, "topic", true)
      }

      // create an anonymous queue for replies
      val replyQueueName = channel.get.queueDeclare().getQueue
      Logger.debug("Reply queue name: " + replyQueueName)

      // get bindings stored in broker
      val queueBindingsFuture = getQueuesNamesForAnExchange(exchange)
      import scala.concurrent.ExecutionContext.Implicits.global
      queueBindingsFuture map { x =>
        implicit val bindingsReader = Json.reads[Binding]
        bindings = x.json.as[List[Binding]]
        Logger.debug("Bindings successufully retrieved")
      }
      Await.result(queueBindingsFuture, 5000 millis)

      // Start actor to listen for extractor status messages
      Logger.info("Starting extraction status receiver")
      event_filter = Some(Akka.system.actorOf(
        Props(new EventFilter(channel.get, replyQueueName)),
        name = "EventFilter"
      ))
      Logger.debug("Initializing a MsgConsumer for the EventFilter")
      channel.get.basicConsume(
        replyQueueName,
        false, // do not auto ack
        "event_filter", // tagging the consumer is important if you want to stop it later
        new MsgConsumer(channel.get, event_filter.get)
      )

      // Start actor to listen to extractor heartbeats
      Logger.info("Starting extractor heartbeat listener")
      // create fanout exchange if it doesn't already exist
      channel.get.exchangeDeclare("extractors", "fanout", true)
      // anonymous queue
      val heartbeatsQueue = channel.get.queueDeclare().getQueue
      // bind queue to exchange
      channel.get.queueBind(heartbeatsQueue, "extractors", "*")
      extractorsHeartbeats = Some(Akka.system.actorOf(
        Props(new ExtractorsHeartbeats(channel.get, heartbeatsQueue)), name = "ExtractorsHeartbeats"
      ))
      Logger.debug("Initializing a MsgConsumer for the ExtractorsHeartbeats")
      channel.get.basicConsume(
        heartbeatsQueue,
        false, // do not auto ack
        "ExtractorsHeartbeats", // tagging the consumer is important if you want to stop it later
        new MsgConsumer(channel.get, extractorsHeartbeats.get)
      )

      // Setup Actor to submit new extractions to broker
      extractQueue = Some(Akka.system.actorOf(Props(new PublishDirectActor(channel = channel.get,
        replyQueueName = replyQueueName))))

      // Setup cancellation queue. Pop messages from extraction queue until we find the one we want to cancel.
      // Store messages in the extraction queue temporarely in this queue.
      try {
        val cancellationSearchTimeout: Long = configuration.getString("submission.cancellation.search.timeout").getOrElse("500").toLong
        // create cancellation download queue to hold pending rabbitmq message
        channel.get.queueDeclare(cancellationDownloadQueueName, true, false, false, null)
        // Actor to connect to a rabbitmq queue to cancel the pending request
        cancellationQueue = Some(Akka.system.actorOf(Props(new PendingRequestCancellationActor(exchange, connection, cancellationDownloadQueueName, cancellationSearchTimeout))))
        // connect to cancellation download queue to re-dispatch the residual pending requests to their target rabbitmq queues.
        val redispatch_cancellation_requests_channel: Channel = connection.get.createChannel()
        val cancellationQueueConsumer: QueueingConsumer = new QueueingConsumer(redispatch_cancellation_requests_channel)
        val cancellationQueueConsumerTag: String = redispatch_cancellation_requests_channel.basicConsume(cancellationDownloadQueueName, false, cancellationQueueConsumer)
        resubmitPendingRequests(cancellationQueueConsumer, redispatch_cancellation_requests_channel, cancellationSearchTimeout)
        redispatch_cancellation_requests_channel.basicCancel(cancellationQueueConsumerTag)
        redispatch_cancellation_requests_channel.close()
      } catch {
        case e: Exception => {
          Logger.error(s"[CANCELLATION] failed to re-dispatch residual requests from $cancellationDownloadQueueName", e)
        }
      }
      true
    } catch {
      case t: Throwable => {
        Logger.error("Error connecting to rabbitmq broker", t)
        close()
        false
      }
    }
  }

  /**
    * Submit and Extractor message to the extraction queue. This is the low level call used by public methods in this
    * class.
    * @param message a model representing the JSON message to send to the queue
    */
  private def extractWorkQueue(message: ExtractorMessage) = {
    Logger.debug(s"Publishing $message directly to queue ${message.queue}")
    connect
    extractQueue match {
      case Some(x) => x ! message
      case None => Logger.warn("Could not send message over RabbitMQ")
    }
  }

  /**
    * Escape various characters in content type when creating a routing key
    * @param contentType original content type in standar form, for example text/csv
    * @return escaped routing key
    */
  private def contentTypeToRoutingKey(contentType: String) =
    contentType.replace(".", "_").replace("/", ".")

  /**
    * Given a dataset, return the union of all extractors registered for each space the dataset is in.
    * @param dataset
    * @return list of active extractors
    */
  private def getRegisteredExtractors(dataset: Dataset): List[String] = {
    dataset.spaces.flatMap(s => spacesService.getAllExtractors(s))
  }

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
  private def containsOperation(operations: List[String], operation: String): Boolean = {
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
    * Query list of global extractors for those enabled and filter by operation.
    */
  private def getGlobalExtractorsByOperation(operation: String): List[String] = {
    extractorsService.getEnabledExtractors().flatMap(exId =>
      extractorsService.getExtractorInfo(exId)).filter(exInfo =>
      containsOperation(exInfo.process.dataset, operation) ||
        containsOperation(exInfo.process.file, operation) ||
        containsOperation(exInfo.process.metadata, operation)
    ).map(_.name)
  }

  /**
    * Find all extractors enabled for the space the dataset belongs and the matched operation.
    * @param dataset  The dataset used to find which space to query for registered extractors.
    * @param operation The dataset operation requested.
    * @return A list of extractors IDs.
    */
  private def getSpaceExtractorsByOperation(dataset: Dataset, operation: String): List[String] = {
    dataset.spaces.flatMap(s =>
      spacesService.getAllExtractors(s).flatMap(exId =>
        extractorsService.getExtractorInfo(exId)).filter(exInfo =>
        containsOperation(exInfo.process.dataset, operation) || containsOperation(exInfo.process.file, operation)).map(_.name))
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
    // While the routing key includes the instance name the rabbitmq bindings has a *.
    // TODO this code could be improved by having less options in how routes and keys are represented
    val fragments = routingKey.split('.')
    val key1 = "*."+fragments.slice(1,fragments.size).mkString(".")
    val key2 = "*."+fragments.slice(1,fragments.size - 1).mkString(".") + ".#"
    val key3 = "*."+fragments(1)+".#"
    val key4 = "*."+fragments.slice(1,fragments.size).mkString(".") + ".#"
    bindings.filter(x => Set(key1, key2, key3, key4).contains(x.routing_key)).map(_.destination)
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
    // drop the first fragment from the routing key and replace characters to create operation id
    val fragments = routingKey.split('.')
    val operation =
      if (fragments(1) == "dataset")
        fragments(2) + "." + fragments(3)
      else if (fragments(1) == "metadata")
        fragments(2) + "." + fragments(3)
      else if (fragments(1) == "file")
        fragments(2) + "/" + fragments(3)
      else ""
    // get extractors enabled at the global level
    val globalExtractors = getGlobalExtractorsByOperation(operation)
    // get extractors enabled at the space level
    val spaceExtractors = getSpaceExtractorsByOperation(dataset, operation)
    // get queues based on RabbitMQ bindings (old method).
    val queuesFromBindings = getQueuesFromBindings(routingKey)
    // take the union of queues so that we publish to a specific queue only once
    globalExtractors.toSet union spaceExtractors.toSet union queuesFromBindings.toSet
  }

  /**
    * Post the event of SUBMITTED
    * @param file_id the UUID of file
    * @param extractor_id the extractor queue name to be submitted
    */
  def postSubmissionEven(file_id: UUID, extractor_id: String): UUID = {
    val extractions: ExtractionService = DI.injector.getInstance(classOf[ExtractionService])

    import java.text.SimpleDateFormat
    val dateFormatter = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssX")
    val submittedDateConvert = Some(new java.util.Date())
    extractions.insert(Extraction(UUID.generate(), file_id, extractor_id, "SUBMITTED", submittedDateConvert, None)) match {
      case Some(objectid) => UUID(objectid.toString)
      case None => UUID("")
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
    if (requestAPIKey.isDefined)
      requestAPIKey.get
    else if (user.isDefined)
      userService.getExtractionApiKey(user.get.identityId).key
    else
      globalAPIKey
  }

  /**
    * Publish to the proper queues when a new file is uploaded to the system.
    * @param file the file that was just uploaded
    * @param dataset the dataset the file belongs to
    * @param host the Clowder host URL for sharing extractors across instances
    */
  def fileCreated(file: File, dataset: Option[Dataset], host: String, requestAPIKey: Option[String]): Unit = {
    val routingKey = exchange + "." + "file." + contentTypeToRoutingKey(file.contentType)
    val extraInfo = Map("filename" -> file.filename)
    val apiKey = requestAPIKey.getOrElse(globalAPIKey)
    val sourceExtra = JsObject((Seq("filename" -> JsString(file.filename))))
    Logger.debug(s"Sending message $routingKey from $host with extraInfo $extraInfo")
    dataset match {
      case Some(d) =>
        getQueues(d, routingKey, file.contentType).foreach{ queue =>
          val source = Entity(ResourceRef(ResourceRef.file, file.id), Some(file.contentType), sourceExtra)

          val notifies = getEmailNotificationEmailList(requestAPIKey)

          val id = postSubmissionEven(file.id, queue)

          val msg = ExtractorMessage(id, file.id, notifies, file.id, host, queue, extraInfo, file.length.toString,
            d.id, "", apiKey, routingKey, source, "created", None)
          extractWorkQueue(msg)
        }
      case None =>
        Logger.debug("RabbitMQPlugin: No dataset associated with this file")
    }
  }

  /**
    * Send message when a new file is uploaded to the system. This is the same as the method above but
    * it supports TempFile instead of File. This is currently only used for multimedia queries.
    * @param file the file that was just uploaded
    * @param host the Clowder host URL for sharing extractors across instances
    */
  def fileCreated(file: TempFile, host: String, requestAPIKey: Option[String]): Unit = {
    val routingKey = exchange + "." + "file." + contentTypeToRoutingKey(file.contentType)
    val extraInfo = Map("filename" -> file.filename)
    val apiKey = requestAPIKey.getOrElse(globalAPIKey)
    Logger.debug(s"Sending message $routingKey from $host with extraInfo $extraInfo")
    val sourceExtra = JsObject((Seq("filename" -> JsString(file.filename))))
    val source = Entity(ResourceRef(ResourceRef.file, file.id), Some(file.contentType), sourceExtra)
    val notifies = getEmailNotificationEmailList(requestAPIKey)
    val id = postSubmissionEven(file.id, routingKey)
    val msg = ExtractorMessage(id, file.id, notifies, file.id, host, routingKey, extraInfo, file.length.toString, null,
      "", apiKey, routingKey, source, "created", None)
    extractWorkQueue(msg)
  }

  /**
    * Send message when a file is added to a dataset. Use both old method using topic queues and new method using work
    * queues and extractors registration in Clowder.
    * @param file the file that was added to the dataset
    * @param dataset the dataset it was added to
    * @param host the Clowder host URL for sharing extractors across instances
    */
  def fileAddedToDataset(file: File, dataset: Dataset, host: String, requestAPIKey: Option[String]): Unit = {
    val routingKey = s"$exchange.dataset.file.added"
    Logger.debug(s"Sending message $routingKey from $host")
    val queues = getQueues(dataset, routingKey, file.contentType)
    val apiKey = requestAPIKey.getOrElse(globalAPIKey)
    val sourceExtra = JsObject((Seq("filename" -> JsString(file.filename))))
    queues.foreach{ extractorId =>
      val source = Entity(ResourceRef(ResourceRef.file, file.id), Some(file.contentType), sourceExtra)
      val target = Entity(ResourceRef(ResourceRef.dataset, dataset.id), None, JsObject(Seq.empty))

      val notifies = getEmailNotificationEmailList(requestAPIKey)

      val id = postSubmissionEven(file.id, extractorId)

      val msg = ExtractorMessage(id, file.id, notifies, file.id, host, extractorId, Map.empty, file.length.toString, dataset.id,
        "", apiKey, routingKey, source, "added", Some(target))
      extractWorkQueue(msg)
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
    val routingKey = s"$exchange.dataset.files.added"
    Logger.debug(s"Sending message $routingKey from $host")
    val queues = getQueues(dataset, routingKey, "")
    val apiKey = requestAPIKey.getOrElse(globalAPIKey)
    val sourceExtra = JsObject((Seq("filenames" -> JsArray(filelist.map(f=>JsString(f.filename)).toSeq))))
    val msgExtra = Map("filenames" -> filelist.map(f=>f.filename))
    queues.foreach{ extractorId =>
      val source = Entity(ResourceRef(ResourceRef.dataset, dataset.id), None, sourceExtra)

      val notifies = getEmailNotificationEmailList(requestAPIKey)

      val id = postSubmissionEven(dataset.id, extractorId)

      var totalsize: Long = 0
      filelist.map(f => totalsize += f.length)
      val msg = ExtractorMessage(id, dataset.id, notifies, dataset.id, host, extractorId, msgExtra, totalsize.toString, dataset.id,
        "", apiKey, routingKey, source, "added", None)
      extractWorkQueue(msg)
    }
  }

  /**
    * Send message when file is removed from a dataset and deleted.
    * @param file
    * @param dataset
    * @param host
    */
  def fileRemovedFromDataset(file: File, dataset: Dataset, host: String, requestAPIKey: Option[String]): Unit = {
    val routingKey = s"$exchange.dataset.file.removed"
    Logger.debug(s"Sending message $routingKey from $host")
    val queues = getQueues(dataset, routingKey, file.contentType)
    val apiKey = requestAPIKey.getOrElse(globalAPIKey)
    val sourceExtra = JsObject((Seq("filename" -> JsString(file.filename))))
    queues.foreach{ extractorId =>
      val source = Entity(ResourceRef(ResourceRef.file, file.id), Some(file.contentType), sourceExtra)
      val target = Entity(ResourceRef(ResourceRef.dataset, dataset.id), None, JsObject(Seq.empty))

      val notifies = getEmailNotificationEmailList(requestAPIKey)
      val id = postSubmissionEven(file.id, extractorId)
      val msg = ExtractorMessage(id, file.id, notifies, file.id, host, extractorId, Map.empty, file.length.toString, dataset.id,
        "", apiKey, routingKey, source, "removed", Some(target))
      extractWorkQueue(msg)
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
    datasetId: UUID, newFlags: String, requestAPIKey: Option[String], user: Option[User]): Unit = {
    Logger.debug(s"Sending message to $queue from $host with extraInfo $extraInfo")
    val apiKey = getApiKey(requestAPIKey, user)
    val sourceExtra = JsObject((Seq("filename" -> JsString(file.filename))))
    val source = Entity(ResourceRef(ResourceRef.file, originalId), Some(file.contentType), sourceExtra)

    val notifies = getEmailNotificationEmailList(requestAPIKey)
    val id = postSubmissionEven(file.id, queue)
    val msg = ExtractorMessage(id, file.id, notifies, file.id, host, queue, extraInfo, file.length.toString, datasetId,
      "", apiKey, "extractors." + queue, source, "submitted", None)
    extractWorkQueue(msg)
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
    requestAPIKey: Option[String], user: Option[User]): Unit = {
    Logger.debug(s"Sending message $queue from $host with extraInfo $extraInfo")
    val apiKey = getApiKey(requestAPIKey, user)
    val source = Entity(ResourceRef(ResourceRef.dataset, datasetId), None, JsObject(Seq.empty))

    val notifies = getEmailNotificationEmailList(requestAPIKey)

    val id = postSubmissionEven(datasetId, queue)
    val msg = ExtractorMessage(id, datasetId, notifies, datasetId, host, queue, extraInfo, 0.toString, datasetId,
      "", apiKey, "extractors." + queue, source, "submitted", None)
    extractWorkQueue(msg)
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
    val routingKey = s"$exchange.metadata.added.${resourceRef.resourceType.name}"
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

              val id = postSubmissionEven(resourceRef.id, extractorId)
              val msg = ExtractorMessage(id, resourceRef.id, notifies, resourceRef.id, host, extractorId, extraInfo, 0.toString, resourceRef.id,
                "", apiKey, routingKey, source, "added", Some(target))
              extractWorkQueue(msg)
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

            val id = postSubmissionEven(resourceRef.id, extractorId)
            val msg = ExtractorMessage(id, resourceRef.id, notifies, resourceRef.id, host, extractorId, extraInfo, 0.toString, null,
              "", apiKey, routingKey, source, "added", Some(target))
            extractWorkQueue(msg)
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
    val routingKey = s"$exchange.metadata.removed.${resourceRef.resourceType.name}"
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

              val id = postSubmissionEven(resourceRef.id, extractorId)
              val msg = ExtractorMessage(id, resourceRef.id, notifies, resourceRef.id, host, extractorId, extraInfo, 0.toString, resourceRef.id,
                "", apiKey, routingKey, source, "removed", Some(target))
              extractWorkQueue(msg)
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

            val id = postSubmissionEven(resourceRef.id, extractorId)
            val msg = ExtractorMessage(id, resourceRef.id, notifies, resourceRef.id, host, extractorId, extraInfo, 0.toString, null,
              "", apiKey, routingKey, source, "removed", Some(target))
            extractWorkQueue(msg)
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
    val routingKey = exchange +".query." + contentType.replace("/", ".")
    val apiKey = requestAPIKey.getOrElse(globalAPIKey)
    Logger.debug(s"Sending message $routingKey from $host")

    val notifies = getEmailNotificationEmailList(requestAPIKey)
    val source = Entity(ResourceRef(ResourceRef.file, tempFileId), Some(contentType), JsObject(Seq.empty))

    val id = postSubmissionEven(tempFileId, routingKey)
    val msg = ExtractorMessage(id, tempFileId, notifies, tempFileId, host, routingKey, Map.empty[String, Any], length, null,
      "", apiKey, routingKey, source, "created", None)
    extractWorkQueue(msg)
  }

  /**
    * Preview creted for section.
    * @param preview
    * @param sectionId
    * @param host
    */
  def submitSectionPreviewManually(preview: Preview, sectionId: UUID, host: String, requestAPIKey: Option[String]): Unit = {
    val routingKey = exchange + ".index."+ contentTypeToRoutingKey(preview.contentType)
    val apiKey = requestAPIKey.getOrElse(globalAPIKey)
    val extraInfo = Map("section_id"->sectionId)
    val source = Entity(ResourceRef(ResourceRef.preview, preview.id), None, JsObject(Seq.empty))
    val target = Entity(ResourceRef(ResourceRef.section, sectionId), None, JsObject(Seq.empty))
    val notifies = getEmailNotificationEmailList(requestAPIKey)

    val id = postSubmissionEven(sectionId, routingKey)
    val msg = ExtractorMessage(id, sectionId, notifies, sectionId, host, routingKey, extraInfo, 0.toString, null,
      "", apiKey, routingKey, source, "added", Some(target))
    extractWorkQueue(msg)
  }


  // ----------------------------------------------------------------------
  // RABBITMQ MANAGEMENT ENDPOINTS
  // ----------------------------------------------------------------------
  def getRestEndPoint(path: String): Future[Response] = {
    connect

    restURL match {
      case Some(x) => {
        val url = x + path
        Logger.trace("RESTURL: "+ url)
        WS.url(url).withHeaders("Accept" -> MimeTypes.JSON).withAuth(username, password, AuthScheme.BASIC).get()
      }
      case None => {
        Logger.warn("Could not get bindings")
        Future.failed(new IOException("Not connected"))
      }
    }
  }

  /**
    * Get the exchange list for a given host
    */
  def getExchanges : Future[Response] = {
    connect
    getRestEndPoint("/api/exchanges/" + vhost )
  }

  /**
    * get list of queues attached to an exchange
    */
  def getQueuesNamesForAnExchange(exchange: String): Future[Response] = {
    connect
    getRestEndPoint("/api/exchanges/"+ vhost +"/"+ exchange +"/bindings/source")
  }

  /**
    * Get the binding lists (lists of routing keys) from the rabbitmq broker
    */
  def getBindings: Future[Response] = {
    getRestEndPoint("/api/bindings")
  }

  /**
    * Get Channel list from rabbitmq broker
    */
  def getChannelsList: Future[Response] = {
    getRestEndPoint("/api/channels")
  }

  /**
    * Get queue details for a given queue
    */
  def getQueueDetails(qname: String): Future[Response] = {
    connect
    getRestEndPoint("/api/queues/" + vhost + "/" + qname)
  }


  /**
    * Get queue bindings for a given host and queue from rabbitmq broker
    */
  def getQueueBindings(qname: String): Future[Response] = {
    connect
    getRestEndPoint("/api/queues/" + vhost + "/" + qname + "/bindings")
  }

  /**
    * Get Channel information from rabbitmq broker for given channel id 'cid'
    */
  def getChannelInfo(cid: String): Future[Response] = {
    getRestEndPoint("/api/channels/" + cid)
  }

  def cancelPendingSubmission(id: UUID, queueName: String, msg_id: UUID): Unit = {
    connect
    cancellationQueue match {
      case Some(x) => x ! new CancellationMessage(id, queueName, msg_id)
      case None => Logger.warn("Could not send message over RabbitMQ")
    }
  }

  /**
    * a helper function to get user email address from user's request api key.
    * @param requestAPIKey user request apikey
    * @return a list of email address
    */
  def getEmailNotificationEmailList(requestAPIKey: Option[String]): List[String] = {
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
    * loop through the queue and dispatch the message via the routing key.
    *
    * @param cancellationQueueConsumer  the queue consumer to download the requests from the cancellation downloaded queue
    * @param channel                    the channel connecting to the rabbitmq
    * @param cancellationSearchTimeout  the timeout of downloading the requests from the rabbitmq
    */
  def resubmitPendingRequests(cancellationQueueConsumer: QueueingConsumer, channel: Channel, cancellationSearchTimeout: Long) = {
    var loop = true
    while( loop ) {
      val delivery: QueueingConsumer.Delivery = cancellationQueueConsumer.nextDelivery(cancellationSearchTimeout)
      delivery match {
        case null => {
          Logger.debug(s"[CANCELLATION] read $cancellationDownloadQueueName timeout, exit the looping on the cancellation download queue")
          loop = false
        }
        case _ => {
          val body = delivery.getBody()
          val delivery_tag = delivery.getEnvelope.getDeliveryTag
          val body_text = new String(body)
          val json = Json.parse(body_text)
          val routing_key: String = (json \ "routing_key").as[String]
          val basicProperties = new BasicProperties().builder()
            .contentType(MimeTypes.JSON)
            .deliveryMode(2)
            .build()
          try {
            val request_id = (json \ "msgid").asOpt[String] match {
              case Some(id)=> Some(UUID(id))
              case None => None
            }

            channel.basicAck(delivery_tag, false)
            Logger.debug(s"[CANCELLATION] ACK $request_id to be removed from $cancellationDownloadQueueName")
            channel.basicPublish(exchange, routing_key, true, basicProperties, body)
            Logger.debug(s"[CANCELLATION] resubmit to $request_id, $routing_key, $body_text ")
          } catch {
            case e: Exception => {
              Logger.error(s"[CANCELLATION] failed to publish, $routing_key", e)
            }
          }
        }
      }
    }
  }
}

/**
  * First, it will connect to the target rabbitmq queue to download each pending submission request and search the
  * cancellation submission by comparing the message id of each pending submission request.
  * This search will terminate when any of the following condition stands:
  *   1. the cancellation submission has been found.
  *   2. within the certain timeout, the target queue has no new pending submission.
  *   3. the number of downloaded pending submission requests exceeds the Threshold.
  *
  * each downloaded submission(except the cancellation submission) will be forwarded to a named rabbitmq queue.
  *
  * Second, when the searching is terminated, it will remove and resubmit each submission from the named rabbitmq queue
  * to the extractor queue(s) based on the routing key of each submission.
  *
  * @param exchange                       the exchange of the rabbitmq
  * @param connection                     the connection to the rabbitmq
  * @param cancellationDownloadQueueName  the queue name of the cancellation downloaded queue
  */
class PendingRequestCancellationActor(exchange: String, connection: Option[Connection], cancellationDownloadQueueName: String, cancellationSearchTimeout: Long) extends Actor {
  val configuration = play.api.Play.configuration
  val CancellationSearchNumLimits: Integer = configuration.getString("submission.cancellation.search.numlimits").getOrElse("100").toInt
  def receive = {
    case CancellationMessage(id, queueName, msg_id) => {
      val extractions: ExtractionService = DI.injector.getInstance(classOf[ExtractionService])
      val dateFormatter = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssX")
      var startDate = Some(new java.util.Date())
      extractions.insert(Extraction(UUID.generate(), id, queueName, "Cancel Requested", startDate, None))

      val channel: Channel = connection.get.createChannel()
      //1. connect to the target rabbitmq queue
      val queueConsumer: QueueingConsumer = new QueueingConsumer(channel)
      val queueConsumerTag: String = channel.basicConsume(queueName, false, queueConsumer)
      val pendingMessages: Integer = channel.queueDeclarePassive(queueName).getMessageCount()
      val maxSearchNum = math.max(CancellationSearchNumLimits, pendingMessages)
      Logger.debug(s"[CANCELLATION] receive the cancellation request, $queueName, $msg_id search num limits: $maxSearchNum timeout: $cancellationSearchTimeout")
      var loop: Boolean = true
      // 2. parse each pending request and search for the cancellation request
      var counts = 0
      var foundCancellationRequest = false
      while( loop ) {
        val delivery: QueueingConsumer.Delivery = queueConsumer.nextDelivery(cancellationSearchTimeout)
        delivery match {
          case null => {
            Logger.debug(s"[CANCELLATION] read, $queueName timeout, exit the searching of cancellation submission")
            loop = false
          }
          case _ => {
            val body = delivery.getBody()
            val delivery_tag = delivery.getEnvelope.getDeliveryTag
            val body_text = new String(body)
            val json = Json.parse(body_text)

            val request_id = (json \ "msgid").asOpt[String] match {
              case Some(id)=> Some(UUID(id))
              case None => None
            }

            if(request_id.isDefined && msg_id.toString() == request_id.get.toString()) {
              Logger.debug(s"found cancellation request and then skip $request_id")
              loop = false
              foundCancellationRequest = true
            } else {
              // upload parsed pending requests to the cancellation download queue
              try {
                val basicProperties = new BasicProperties().builder()
                  .contentType(MimeTypes.JSON)
                  .deliveryMode(2)
                  .build()
                channel.basicPublish("", cancellationDownloadQueueName, basicProperties, body)
                Logger.debug(s"[CANCELLATION] publish $request_id to the queue: $cancellationDownloadQueueName, $body_text")
              } catch {
                case e: Exception => {
                  Logger.error(s"[CANCELLATION] failed to publish to queue: $cancellationDownloadQueueName", e)
                }
              }
            }
            try {
              channel.basicAck(delivery_tag, false)
              Logger.debug(s"[CANCELLATION] ACK $request_id to be removed from $queueName")
            } catch {
              case e: Exception => {
                Logger.error(s"[CANCELLATION] failed to ACK $request_id, $body_text", e)
              }
            }
          }
        }
        counts += 1;
        if (counts > maxSearchNum) {
          loop = false
        }
      }
      // update extraction event
      startDate = Some(new java.util.Date())
      if(foundCancellationRequest) {
        extractions.insert(Extraction(UUID.generate(), id, queueName, "Cancel Success", startDate, None))
      } else {
        extractions.insert(Extraction(UUID.generate(), id, queueName, "Cancel Failed", startDate, None))
      }

      try {
        channel.basicCancel(queueConsumerTag)

      } catch {
        case e: Exception => {
          Logger.error(s"[CANCELLATION] failed to cancel, $queueConsumerTag", e)
        }
      }

      //3. resubmit pending requests from cancellation download queue to the target queue.
      val cancellationQueueConsumer: QueueingConsumer = new QueueingConsumer(channel)
      val cancellationQueueConsumerTag: String = channel.basicConsume(cancellationDownloadQueueName, false, cancellationQueueConsumer)
      current.plugin[RabbitmqPlugin] match {
        case Some(p) => p.resubmitPendingRequests(cancellationQueueConsumer, channel, cancellationSearchTimeout)
        case None => Logger.error(s"[CANCELLATION] RabbitmqPlugin not enabled")
      }

      try {
        channel.basicCancel(cancellationQueueConsumerTag)
        channel.close()
      } catch {
        case e: Exception => {
          Logger.error(s"[CANCELLATION] failed to cancel $queueConsumerTag", e)
        }
      }
      Logger.debug(s"[CANCELLATION] finish cancellation request $queueName, $msg_id")

    }
    case _ => {
      Logger.error("[CANCELLATION] Unknown message type submitted.")
    }
  }
}

/**
  * Send message on specified channel directly to a queue and tells receiver to reply
  * on specified queue.
  */
class PublishDirectActor(channel: Channel, replyQueueName: String) extends Actor {
  val appHttpPort = play.api.Play.configuration.getString("http.port").getOrElse("")
  val appHttpsPort = play.api.Play.configuration.getString("https.port").getOrElse("")
  val clowderurl = play.api.Play.configuration.getString("clowder.rabbitmq.clowderurl")

  def receive = {
    case ExtractorMessage(msgid, fileid, notifies, intermediateId, host, key, metadata, fileSize, datasetId, flags, secretKey, routingKey,
    source, activity, target) => {
      var theDatasetId = ""
      if (datasetId != null)
        theDatasetId = datasetId.stringify

      val actualHost = clowderurl match {
        case Some(url) => url
        case None => {
          if (!appHttpsPort.equals("")) {
            host.replaceAll("^http:", "https:").replaceFirst(":" + appHttpPort, ":" + appHttpsPort)
          } else {
            host
          }
        }
      }

      val msgMap = scala.collection.mutable.Map(
        "notifies" -> Json.toJson(notifies),
        "msgid" -> Json.toJson(msgid.stringify),
        "id" -> Json.toJson(fileid.stringify),
        "intermediateId" -> Json.toJson(intermediateId.stringify),
        "fileSize" -> Json.toJson(fileSize),
        "host" -> Json.toJson(actualHost),
        "datasetId" -> Json.toJson(theDatasetId),
        "flags" -> Json.toJson(flags),
        "secretKey" -> Json.toJson(secretKey),
        "routing_key" -> Json.toJson(routingKey),
        "source" -> Json.toJson(source),
        "activity" -> Json.toJson(activity),
        "target" -> target.map{Json.toJson(_)}.getOrElse(Json.toJson("""{}"""))

      )
      // add extra fields
      // FIXME use Play JSON libraries / JSON Formatter / Readers / Writers
      metadata.foreach(kv =>
        kv._2 match {
          case x: JsValue => msgMap.put(kv._1, x)
          case x: String => msgMap.put(kv._1, Json.toJson(x))
          case _ => msgMap.put(kv._1, Json.toJson(kv._2.toString))
        }

      )
      val msg = Json.toJson(msgMap.toMap)
      // correlation id used for rpc call
      val corrId = java.util.UUID.randomUUID().toString() // TODO switch to models.UUID?
      // setup properties
      val basicProperties = new BasicProperties().builder()
        .contentType(MimeTypes.JSON)
        .correlationId(corrId)
        .replyTo(replyQueueName)
        .deliveryMode(2)
        .build()
      try {
        Logger.debug(s"Sending $msg to $key")
        channel.basicPublish("", key, true, basicProperties, msg.toString().getBytes())
      } catch {
        case e: Exception => {
          Logger.error("Error connecting to rabbitmq broker", e)
          current.plugin[RabbitmqPlugin].foreach {
            _.close()
          }
        }
      }
    }
    case _ => {
      Logger.error("Unknown message type submitted.")
    }
  }
}

/**
  * Listen for responses coming back on replyQueue
  */
class MsgConsumer(channel: Channel, target: ActorRef) extends DefaultConsumer(channel) {
  override def handleDelivery(consumer_tag: String,
                              envelope: Envelope,
                              properties: BasicProperties,
                              body: Array[Byte]) {
    val delivery_tag = envelope.getDeliveryTag
    val body_text = new String(body)

    target ! body_text
    channel.basicAck(delivery_tag, false)
  }
}

/**
  * Actual message on reply queue.
  */
class EventFilter(channel: Channel, queue: String) extends Actor {
  val extractions: ExtractionService = DI.injector.getInstance(classOf[ExtractionService])

  def receive = {
    case statusBody: String =>
      Logger.debug("Received extractor status: " + statusBody)
      val json = Json.parse(statusBody)
      val file_id = UUID((json \ "file_id").as[String])
      val extractor_id = (json \ "extractor_id").as[String]
      val status = (json \ "status").as[String]
      val startDate = (json \ "start").asOpt[String].map(x =>
        Try(new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssX").parse(x)).getOrElse {
          new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss").parse(x)
        })
      val updatedStatus = status.toUpperCase()
      //TODO : Enforce consistent status updates: STARTED, DONE, ERROR and
      //       other detailed status updates to logs when we start implementing
      //       distributed logging
      if (updatedStatus.contains("DONE")) {
        extractions.insert(Extraction(UUID.generate(), file_id, extractor_id, "DONE", startDate, None))
      } else {
        val commKey = "key=" + play.Play.application().configuration().getString("commKey")
        val parsed_status = status.replace(commKey, "key=secretKey")
        extractions.insert(Extraction(UUID.generate(), file_id, extractor_id, parsed_status, startDate, None))
      }
      Logger.debug("updatedStatus=" + updatedStatus + " status=" + status + " startDate=" + startDate)
      models.ExtractionInfoSetUp.updateDTSRequests(file_id, extractor_id)
  }
}

/**
  * Listen for heartbeats messages sent by extractors.
  * @param channel
  * @param queue
  */
class ExtractorsHeartbeats(channel: Channel, queue: String) extends Actor {
  val extractions: ExtractionService = DI.injector.getInstance(classOf[ExtractionService])
  val extractorsService: ExtractorService = DI.injector.getInstance(classOf[ExtractorService])

  def receive = {
    case statusBody: String =>
      Logger.debug("Received extractor heartbeat: " + statusBody)
      val json = Json.parse(statusBody)
      // TODO store running extractors ids
      val id = UUID((json \ "id").as[String])
      val queue = (json \ "queue").as[String]
      val extractor_info = json \ "extractor_info"

      // Validate document
      val extractionInfoResult = extractor_info.validate[ExtractorInfo]

      // Update database
      extractionInfoResult.fold(
        errors => {
          Logger.debug("Received extractor heartbeat with bad format: " + extractor_info)
        },
        info => {
          extractorsService.getExtractorInfo(info.name) match {
            case Some(infoFromDB) => {
              // TODO only update if new semantic version is greater than old semantic version
              if (infoFromDB.version != info.version) {
                // TODO keep older versions of extractor info instead of just the latest one
                extractorsService.updateExtractorInfo(info)
                Logger.info("Updated extractor definition for " + info.name)
              }
            }
            case None => {
              extractorsService.updateExtractorInfo(info)
              Logger.info(s"New extractor ${info.name} registered from heartbeat")
            }
          }
        }
      )
  }
}

/** RabbitMQ Bindings retrieve from management API **/
case class Binding(source: String, vhost: String, destination: String, destination_type: String, routing_key: String,
                   arguments: JsObject, properties_key: String)