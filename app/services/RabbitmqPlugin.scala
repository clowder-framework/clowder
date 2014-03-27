package services

import play.api.{ Plugin, Logger, Application }
import com.typesafe.config.ConfigFactory
import com.rabbitmq.client.AMQP.BasicProperties
import com.rabbitmq.client.ConnectionFactory
import com.rabbitmq.client.Connection
import com.rabbitmq.client.Channel
import play.libs.Akka
import akka.actor.Props
import akka.actor.Actor
import akka.actor.ActorRef
import play.api.libs.json.Json
import play.api.Play.current
import com.rabbitmq.client.QueueingConsumer
import com.rabbitmq.client.DefaultConsumer
import com.rabbitmq.client.Envelope
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global
import akka.routing.SmallestMailboxRouter
import models.Extraction
import java.text.SimpleDateFormat
import org.bson.types.ObjectId
   
/**
 * Rabbitmq service.
 *
 * @author Luigi Marini
 *
 */
class RabbitmqPlugin(application: Application) extends Plugin {

  var extractQueue: Option[ActorRef] = None  
  
  override def onStart() {
    Logger.debug("Starting Rabbitmq Plugin")
    
    val configuration = play.api.Play.configuration
    val host = configuration.getString("rabbitmq.host").getOrElse("")
    val port = configuration.getString("rabbitmq.port").getOrElse("")
    val exchange = configuration.getString("rabbitmq.exchange").getOrElse("")
    val user = configuration.getString("rabbitmq.user").getOrElse("")
    val password = configuration.getString("rabbitmq.password").getOrElse("")
    
    try {
      val factory = new ConnectionFactory()
      if (!host.equals("")) factory.setHost(host)
      if (!port.equals("")) factory.setPort(port.toInt)
      if (!user.equals("")) factory.setUsername(user)
      if (!password.equals("")) factory.setPassword(password)
      val connection: Connection = factory.newConnection()
      val channel = connection.createChannel()
      val replyQueueName = channel.queueDeclare().getQueue()
      Logger.info("Reply queue name: " + replyQueueName)
      // extraction queue
      channel.exchangeDeclare(exchange, "topic", true)
      extractQueue =  Some(Akka.system.actorOf(Props(new SendingActor(channel = channel, exchange = exchange, replyQueueName = replyQueueName))))
      // status consumer
      Logger.info("Starting extraction status receiver")
	    val event_filter = Akka.system.actorOf(
	      Props(new EventFilter(channel, replyQueueName)),
	      name="EventFilter"
	    )
 
	    Logger.debug("Initializing a MsgConsumer for the EventFilter")
	    channel.basicConsume(
	      replyQueueName,
	      false, // do not auto ack
	      "event_filter", // tagging the consumer is important if you want to stop it later
	      new MsgConsumer(channel, event_filter)
	    )
    } catch {
      case ioe: java.io.IOException => Logger.error("Error connecting to rabbitmq broker " + ioe.toString)
      case _:Throwable => Logger.error("Unknown error connecting to rabbitmq broker ")
    }
  }

  override def onStop() {
    Logger.debug("Shutting down Rabbitmq Plugin")
  }

  override lazy val enabled = {
    !application.configuration.getString("rabbitmqplugin").filter(_ == "disabled").isDefined
  }

  def extract(message: ExtractorMessage) = {
    Logger.debug("Sending message " + message)
    extractQueue match {
      case Some(x) => x ! message
      case None => Logger.warn("Could not send message over RabbitMQ")
    }
  }
}

class SendingActor(channel: Channel, exchange: String, replyQueueName: String) extends Actor {
  
  val appHttpPort = play.api.Play.configuration.getString("http.port").get
  val appHttpsPort = play.api.Play.configuration.getString("https.port").getOrElse("")
 
  def receive = {
      case ExtractorMessage(id, intermediateId, host, key, metadata, fileSize, datasetId, flags) => {
        var actualHost = host
        //Tell the extractors to use https if webserver is so configured
        if(!appHttpsPort.equals("")){
          actualHost = host.replaceAll("^http:", "https:").replaceFirst(":"+appHttpPort, ":"+appHttpsPort)
        }
        
        Logger.debug("actualHost: "+ actualHost)
        Logger.debug("http: "+ appHttpPort)
        Logger.debug("https: "+ appHttpsPort)

        val msgMap = scala.collection.mutable.Map(
            "id" -> Json.toJson(id),
            "intermediateId" -> Json.toJson(intermediateId),
            "fileSize" -> Json.toJson(fileSize),
            "host" -> Json.toJson(actualHost),
            "datasetId" -> Json.toJson(datasetId),
            "flags" -> Json.toJson(flags)
            )
        // add extra fields
        metadata.foreach(kv => msgMap.put(kv._1,Json.toJson(kv._2)))
        val msg = Json.toJson(msgMap.toMap)
        Logger.info(msg.toString())
        // correlation id used for rpc call
        val corrId = java.util.UUID.randomUUID().toString()
        // setup properties
        val basicProperties = new BasicProperties().builder()
        	.contentType("application\\json")
        	.correlationId(corrId)
            .replyTo(replyQueueName)
        	.build()
        channel.basicPublish(exchange, key, true, basicProperties, msg.toString().getBytes())
      }
      
      case _ => {
        Logger.error("Unknown message type submitted.")
      }
  }
}

case class ExtractorMessage (
    id: String,
    intermediateId: String,
    host: String,
    key: String,
    metadata: Map[String, String],
    fileSize: String,
    datasetId: String,
    flags: String
)

class MsgConsumer(channel: Channel, target: ActorRef) extends DefaultConsumer(channel) {
 
  override def handleDelivery(consumer_tag: String,
                              envelope: Envelope,
                              properties: BasicProperties,
                              body: Array[Byte])
  {
    val delivery_tag = envelope.getDeliveryTag
    val body_text = new String(body)

      target ! body_text
      channel.basicAck(delivery_tag, false)

  }
 
}

class EventFilter(channel: Channel, queue: String) extends Actor {
   def receive = {
     case statusBody: String => 
            Logger.info("Received extractor status: " + statusBody)
            val json = Json.parse(statusBody)
            val file_id = new ObjectId((json \ "file_id").as[String])
            val extractor_id = (json \ "extractor_id").as[String]
            val status = (json \ "status").as[String]
            val start = (json \ "start").asOpt[String]
            val formatter = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss")
            val startDate = formatter.parse(start.get)
            Logger.info("Status start: " + startDate)
            Extraction.insert(Extraction(new ObjectId(), file_id, extractor_id, status, Some(startDate), None))
          
   }
}




