package services

import java.io.IOException
import java.net.{URI, URLEncoder}
import java.text.SimpleDateFormat
import javax.inject.Singleton

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

@Singleton
class RabbitMQMessageService extends MessageService {
  var channel: Option[Channel] = None
  var connection: Option[Connection] = None
  var factory: Option[ConnectionFactory] = Some(new ConnectionFactory())

  var event_filter: Option[ActorRef] = None
  var extractorsHeartbeats: Option[ActorRef] = None

  val cancellationDownloadQueueName: String = "clowder.jobs.temp"

  var restURL: Option[String] = None

  var vhost: String = ""
  var username: String = ""
  var password: String = ""
  var rabbitmquri: String = ""
  var exchange: String = ""
  var mgmtPort: String = ""

  def getInfo(isServerAdmin: Boolean): JsObject = {
    val status = if (connect()) {
      "connected"
    } else {
      "disconnected"
    }
    if (isServerAdmin) {
      return Json.obj("uri" -> rabbitmquri,
        "exchange" -> exchange,
        "status" -> status)
    } else {
      return Json.obj("status" -> status)
    }
  }

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
  override def close() {
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
  override def submit(message: ExtractorMessage) = {
    extractWorkQueue(message)
  }

  /** Submit a message to broker. */
  override def submit(exchange: String, routing_key: String, message: JsValue, exchange_type: String = "topic") = {
    // This probably isn't going to extract queue (use other submit() for that) so make a new broker
    val tempChannel = connection.get.createChannel()
    tempChannel.exchangeDeclare(exchange, exchange_type, true)
    tempChannel.queueDeclare(routing_key, true, false, false, null)
    tempChannel.queueBind(routing_key, exchange, routing_key)
    tempChannel.basicPublish(exchange, routing_key, null, message.toString.getBytes)
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
  override def getExchanges : Future[Response] = {
    connect
    getRestEndPoint("/api/exchanges/" + vhost )
  }

  /**
   * get list of queues attached to an exchange
   */
  override def getQueuesNamesForAnExchange(exchange: String): Future[Response] = {
    connect
    getRestEndPoint("/api/exchanges/"+ vhost +"/"+ exchange +"/bindings/source")
  }

  /**
   * Get the binding lists (lists of routing keys) from the rabbitmq broker
   */
  override def getBindings: Future[Response] = {
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
  override def getQueueDetails(qname: String): Future[Response] = {
    connect
    getRestEndPoint("/api/queues/" + vhost + "/" + qname)
  }

  /**
   * Get queue bindings for a given host and queue from rabbitmq broker
   */
  override def getQueueBindings(qname: String): Future[Response] = {
    connect
    getRestEndPoint("/api/queues/" + vhost + "/" + qname + "/bindings")
  }

  /**
   * Get Channel information from rabbitmq broker for given channel id 'cid'
   */
  private def getChannelInfo(cid: String): Future[Response] = {
    getRestEndPoint("/api/channels/" + cid)
  }

  override def cancelPendingSubmission(id: UUID, queueName: String, msg_id: UUID): Unit = {
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
    val userService: UserService = DI.injector.getInstance(classOf[UserService])

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
  override def resubmitPendingRequests(cancellationQueueConsumer: QueueingConsumer, channel: Channel, cancellationSearchTimeout: Long) = {
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
