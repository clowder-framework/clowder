package services

import java.io.IOException
import java.net.{URI, URLEncoder}
import java.text.SimpleDateFormat

import akka.actor.{Actor, ActorRef, PoisonPill, Props}
import com.ning.http.client.Realm.AuthScheme
import com.rabbitmq.client.AMQP.BasicProperties
import com.rabbitmq.client.{Channel, Connection, ConnectionFactory, DefaultConsumer, Envelope, QueueingConsumer}
import models._
import play.api.Logger
import play.api.Play.current
import play.api.http.MimeTypes
import play.api.libs.json.{JsObject, JsValue, Json}
import play.api.libs.ws.{Response, WS}
import play.libs.Akka

import scala.concurrent.{Await, Future}
import scala.concurrent.duration._
import scala.util.Try

/**
 * Send/get messages from a message bus
 */

class RabbitMQMessageService extends MessageService {
  var channel: Option[Channel] = None
  var connection: Option[Connection] = None
  var factory: Option[ConnectionFactory] = None

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

  /** Open connection to broker. **/
  def connect(): Boolean = {
    if (channel.isDefined) return true
    if (!factory.isDefined) return true

    // Formerly onStart() block
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

  /** Submit a message to default broker. */
  def submit(message: ExtractorMessage) = {
    extractWorkQueue(message)
  }

  /** Submit a message to broker. */
  def submit(exchange: String, routing_key: String, message: JsValue) = {

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

  private def getRestEndPoint(path: String): Future[Response] = {
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
  private def getChannelsList: Future[Response] = {
    getRestEndPoint("/api/channels")
  }

  /**
   * Get queue details for a given queue
   */
  private def getQueueDetails(qname: String): Future[Response] = {
    connect
    getRestEndPoint("/api/queues/" + vhost + "/" + qname)
  }

  /**
   * Get queue bindings for a given host and queue from rabbitmq broker
   */
  private def getQueueBindings(qname: String): Future[Response] = {
    connect
    getRestEndPoint("/api/queues/" + vhost + "/" + qname + "/bindings")
  }

  /**
   * Get Channel information from rabbitmq broker for given channel id 'cid'
   */
  private def getChannelInfo(cid: String): Future[Response] = {
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
  private def getEmailNotificationEmailList(requestAPIKey: Option[String]): List[String] = {
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
class PendingRequestCancellationActor(exchange: String, connection: Option[Connection], cancellationDownloadQueueName: String,
                                      cancellationSearchTimeout: Long) extends Actor {
  val configuration = play.api.Play.configuration
  val CancellationSearchNumLimits: Integer = configuration.getString("submission.cancellation.search.numlimits").getOrElse("100").toInt
  def receive = {
    case CancellationMessage(id, queueName, msg_id) => {
      val extractions: ExtractionService = DI.injector.getInstance(classOf[ExtractionService])
      val messages: MessageService = DI.injector.getInstance(classOf[MessageService])

      val dateFormatter = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssX")
      var startDate = new java.util.Date()
      var user_id =  User.anonymous.id
      val job_id: Option[UUID] = extractions.get(msg_id) match {
        case Some(extraction) => {
          user_id = extraction.user_id
          extraction.job_id
        }
        case None => {
          Logger.warn("Failed to lookup jobId.. no extraction message found with id=" + msg_id)
          None
        }
      }
      extractions.insert(Extraction(UUID.generate(), id, job_id, queueName, "Cancel Requested", startDate, None, user_id))

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
      startDate = new java.util.Date()
      if(foundCancellationRequest) {
        extractions.insert(Extraction(UUID.generate(), id, job_id, queueName, "Cancel Success", startDate, None, user_id))
      } else {
        extractions.insert(Extraction(UUID.generate(), id, job_id, queueName, "Cancel Failed", startDate, None, user_id))
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

      messages.resubmitPendingRequests(cancellationQueueConsumer, channel, cancellationSearchTimeout)

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
  val messages: MessageService = DI.injector.getInstance(classOf[MessageService])

  def receive = {
    case ExtractorMessage(msgid, fileid, jobid, notifies, intermediateId, host, key, metadata, fileSize, datasetId, flags, secretKey, routingKey,
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
        "jobid" -> Json.toJson(jobid.get.stringify),
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
        Logger.debug(s"[$jobid] Sending $msg to $key")
        channel.basicPublish("", key, true, basicProperties, msg.toString().getBytes())
      } catch {
        case e: Exception => {
          Logger.error("Error connecting to rabbitmq broker", e)
          messages.close
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
      val user_id = (json \ "user_id").asOpt[String].fold(User.anonymous.id)(s => UUID(s))
      val job_id: Option[UUID] = (json \ "job_id").asOpt[String] match {
        case Some(jid) => { Some(UUID(jid)) }
        case None => { None }
      }
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
        extractions.insert(Extraction(UUID.generate(), file_id, job_id, extractor_id, "DONE", startDate.get, None, user_id))
      } else {
        val commKey = "key=" + play.Play.application().configuration().getString("commKey")
        val parsed_status = status.replace(commKey, "key=secretKey")
        extractions.insert(Extraction(UUID.generate(), file_id, job_id, extractor_id, parsed_status, startDate.get, None, user_id))
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
              extractorsService.updateExtractorInfo(info) match {
                case None => {}
                case Some(eInfo) => {
                  // Create (if needed) and assign default labels
                  eInfo.defaultLabels.foreach(labelStr => {
                    val segments = labelStr.split("/")
                    val (labelName, labelCategory) = if (segments.length > 1) {
                      (segments(1), segments(0))
                    } else {
                      (segments(0), "Other")
                    }
                    extractorsService.getExtractorsLabel(labelName) match {
                      case None => {
                        // Label does not exist - create and assign it
                        val createdLabel = extractorsService.createExtractorsLabel(labelName, Some(labelCategory), List[String](eInfo.name))
                      }
                      case Some(lbl) => {
                        // Label already exists, assign it
                        if (!lbl.extractors.contains(eInfo.name)) {
                          val label = ExtractorsLabel(lbl.id, lbl.name, lbl.category, lbl.extractors ++ List[String](eInfo.name))
                          val updatedLabel = extractorsService.updateExtractorsLabel(label)
                        }
                      }
                    }
                  })
                }
              }

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
