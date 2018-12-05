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
import com.rabbitmq.client._
import models._
import play.api.Play.current
import play.api.libs.json._
import play.api.libs.ws.{Response, WS}
import play.api.{Application, Logger, Plugin}
import play.libs.Akka

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
  fileId: UUID,
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

  var extractQueue: Option[ActorRef] = None
  var channel: Option[Channel] = None
  var connection: Option[Connection] = None
  var factory: Option[ConnectionFactory] = None
  var restURL: Option[String] = None
  var event_filter: Option[ActorRef] = None
  var vhost: String = ""
  var username: String = ""
  var password: String = ""
  var rabbitmquri: String = ""
  var exchange: String = ""
  var mgmtPort: String = ""
  var bindings = List.empty[Binding]

  var apiKey = play.api.Play.configuration.getString("commKey").getOrElse("")

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

  override def onStop() {
    Logger.debug("Shutting down Rabbitmq Plugin")
    factory = None
    close()
  }

  override lazy val enabled = {
    !application.configuration.getString("rabbitmqplugin").filter(_ == "disabled").isDefined
  }

  def close() {
    Logger.debug("Closing connection")
    extractQueue = None
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

      val queueBindingsFuture = getQueuesNamesForAnExchange(exchange)
      import scala.concurrent.ExecutionContext.Implicits.global
      queueBindingsFuture map { x =>
        implicit val peopleReader = Json.reads[Binding]
        bindings = x.json.as[List[Binding]]
        Logger.debug("Bindings successufully retrieved")
      }
      Await.result(queueBindingsFuture, 5000 millis)

      // status consumer
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

      // Actor to submit to the rabbitmq broker
      extractQueue = Some(Akka.system.actorOf(Props(new PublishDirectActor(channel = channel.get,
        replyQueueName = replyQueueName))))

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
    // get gueues based on RabbitMQ bindings (old method).
    val queuesFromBindings = getQueuesFromBindings(routingKey)
    // take the union of queues so that we publish to a specific queue only once
    globalExtractors.toSet union spaceExtractors.toSet union queuesFromBindings.toSet
  }

  /**
    * Publish to the proper queues when a new file is uploaded to the system.
    * @param file the file that was just uploaded
    * @param dataset the dataset the file belongs to
    * @param host the Clowder host URL for sharing extractors across instances
    */
  def fileCreated(file: File, dataset: Option[Dataset], host: String): Unit = {
    val routingKey = exchange + "." + "file." + contentTypeToRoutingKey(file.contentType)
    val extraInfo = Map("filename" -> file.filename)
    val sourceExtra = JsObject((Seq("filename" -> JsString(file.filename))))
    Logger.debug(s"Sending message $routingKey from $host with extraInfo $extraInfo")
    dataset match {
      case Some(d) =>
        getQueues(d, routingKey, file.contentType).foreach{ queue =>
          val source = Entity(ResourceRef(ResourceRef.file, file.id), Some(file.contentType), sourceExtra)
          val msg = ExtractorMessage(file.id, file.id, host, queue, extraInfo, file.length.toString,
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
  def fileCreated(file: TempFile, host: String): Unit = {
    val routingKey = exchange + "." + "file." + contentTypeToRoutingKey(file.contentType)
    val extraInfo = Map("filename" -> file.filename)
    Logger.debug(s"Sending message $routingKey from $host with extraInfo $extraInfo")
    val sourceExtra = JsObject((Seq("filename" -> JsString(file.filename))))
    val source = Entity(ResourceRef(ResourceRef.file, file.id), Some(file.contentType), sourceExtra)
    val msg = ExtractorMessage(file.id, file.id, host, routingKey, extraInfo, file.length.toString, null,
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
  def fileAddedToDataset(file: File, dataset: Dataset, host: String): Unit = {
    val routingKey = s"$exchange.dataset.file.added"
    Logger.debug(s"Sending message $routingKey from $host")
    val queues = getQueues(dataset, routingKey, file.contentType)
    val sourceExtra = JsObject((Seq("filename" -> JsString(file.filename))))
    queues.foreach{ extractorId =>
      val source = Entity(ResourceRef(ResourceRef.file, file.id), Some(file.contentType), sourceExtra)
      val target = Entity(ResourceRef(ResourceRef.dataset, dataset.id), None, JsObject(Seq.empty))
      val msg = ExtractorMessage(file.id, file.id, host, extractorId, Map.empty, file.length.toString, dataset.id,
        "", apiKey, routingKey, source, "added", Some(target))
      extractWorkQueue(msg)
    }
  }

  /**
    * Send message when file is removed from a dataset and deleted.
    * @param file
    * @param dataset
    * @param host
    */
  def fileRemovedFromDataset(file: File, dataset: Dataset, host: String): Unit = {
    val routingKey = s"$exchange.dataset.file.removed"
    Logger.debug(s"Sending message $routingKey from $host")
    val queues = getQueues(dataset, routingKey, file.contentType)
    val sourceExtra = JsObject((Seq("filename" -> JsString(file.filename))))
    queues.foreach{ extractorId =>
      val source = Entity(ResourceRef(ResourceRef.file, file.id), Some(file.contentType), sourceExtra)
      val target = Entity(ResourceRef(ResourceRef.dataset, dataset.id), None, JsObject(Seq.empty))
      val msg = ExtractorMessage(file.id, file.id, host, extractorId, Map.empty, file.length.toString, dataset.id,
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
    datasetId: UUID, newFlags: String): Unit = {
    Logger.debug(s"Sending message to $queue from $host with extraInfo $extraInfo")
    val sourceExtra = JsObject((Seq("filename" -> JsString(file.filename))))
    val source = Entity(ResourceRef(ResourceRef.file, originalId), Some(file.contentType), sourceExtra)
    val msg = ExtractorMessage(file.id, file.id, host, queue, extraInfo, file.length.toString, datasetId,
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
  def submitDatasetManually(host: String, queue: String, extraInfo: Map[String, Any], datasetId: UUID, newFlags: String): Unit = {
    Logger.debug(s"Sending message $queue from $host with extraInfo $extraInfo")
    val source = Entity(ResourceRef(ResourceRef.dataset, datasetId), None, JsObject(Seq.empty))
    val msg = ExtractorMessage(datasetId, datasetId, host, queue, extraInfo, 0.toString, datasetId,
      "", apiKey, "extractors." + queue, source, "submitted", None)
    extractWorkQueue(msg)
  }


  /**
    * Metadata added to a resource (file or dataset).
    * @param resourceRef
    * @param extraInfo
    * @param host
    */
  def metadataAddedToResource(metadataId: UUID, resourceRef: ResourceRef, extraInfo: Map[String, Any], host: String): Unit = {
    val routingKey = s"$exchange.metadata.added.${resourceRef.resourceType.name}"
    Logger.debug(s"Sending message $routingKey from $host with extraInfo $extraInfo")

    resourceRef.resourceType match {
      // metadata added to dataset
      case ResourceRef.dataset =>
        datasetService.get(resourceRef.id) match {
          case Some(dataset) =>
            getQueues(dataset, routingKey, "").foreach { extractorId =>
              val source = Entity(ResourceRef(ResourceRef.metadata, metadataId), None, JsObject(Seq.empty))
              val target = Entity(resourceRef, None, JsObject(Seq.empty))
              val msg = ExtractorMessage(resourceRef.id, resourceRef.id, host, extractorId, extraInfo, 0.toString, resourceRef.id,
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
            val msg = ExtractorMessage(resourceRef.id, resourceRef.id, host, extractorId, extraInfo, 0.toString, null,
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
  def metadataRemovedFromResource(metadataId: UUID, resourceRef: ResourceRef, host: String): Unit = {
    val routingKey = s"$exchange.metadata.removed.${resourceRef.resourceType.name}"
    val extraInfo = Map[String, Any](
      "resourceType"->resourceRef.resourceType.name,
      "resourceId"->resourceRef.id.toString)
    Logger.debug(s"Sending message $routingKey from $host with extraInfo $extraInfo")

    resourceRef.resourceType match {
      // metadata added to dataset
      case ResourceRef.dataset =>
        datasetService.get(resourceRef.id) match {
          case Some(dataset) =>
            getQueues(dataset, routingKey, "").foreach { extractorId =>
              val source = Entity(ResourceRef(ResourceRef.metadata, metadataId), None, JsObject(Seq.empty))
              val target = Entity(resourceRef, None, JsObject(Seq.empty))
              val msg = ExtractorMessage(resourceRef.id, resourceRef.id, host, extractorId, extraInfo, 0.toString, resourceRef.id,
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
            val msg = ExtractorMessage(resourceRef.id, resourceRef.id, host, extractorId, extraInfo, 0.toString, null,
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
  def multimediaQuery(tempFileId: UUID, contentType: String, length: String, host: String): Unit = {
    //key needs to contain 'query' when uploading a query
    //since the thumbnail extractor during processing will need to upload to correct mongo collection.
    val routingKey = exchange +".query." + contentType.replace("/", ".")
    Logger.debug(s"Sending message $routingKey from $host")
    val source = Entity(ResourceRef(ResourceRef.file, tempFileId), Some(contentType), JsObject(Seq.empty))
    val msg = ExtractorMessage(tempFileId, tempFileId, host, routingKey, Map.empty[String, Any], length, null,
      "", apiKey, routingKey, source, "created", None)
    extractWorkQueue(msg)
  }

  /**
    * Preview creted for section.
    * @param preview
    * @param sectionId
    * @param host
    */
  def submitSectionPreviewManually(preview: Preview, sectionId: UUID, host: String): Unit = {
    val routingKey = exchange + ".index."+ contentTypeToRoutingKey(preview.contentType)
    val extraInfo = Map("section_id"->sectionId)
    val source = Entity(ResourceRef(ResourceRef.preview, preview.id), None, JsObject(Seq.empty))
    val target = Entity(ResourceRef(ResourceRef.section, sectionId), None, JsObject(Seq.empty))
    val msg = ExtractorMessage(sectionId, sectionId, host, routingKey, extraInfo, 0.toString, null,
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
        WS.url(url).withHeaders("Accept" -> "application/json").withAuth(username, password, AuthScheme.BASIC).get()
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
    case ExtractorMessage(id, intermediateId, host, key, metadata, fileSize, datasetId, flags, secretKey, routingKey,
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
        "id" -> Json.toJson(id.stringify),
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
        .contentType("application\\json")
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
 * Actual message on reply queue
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

case class Binding(source: String, vhost: String, destination: String, destination_type: String, routing_key: String,
  arguments: JsObject, properties_key: String)
