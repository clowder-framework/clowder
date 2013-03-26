package services

import play.api.{ Plugin, Logger, Application }
import com.typesafe.config.ConfigFactory
import com.rabbitmq.client.ConnectionFactory
import com.rabbitmq.client.Connection
import com.rabbitmq.client.Channel
import play.libs.Akka
import akka.actor.Props
import akka.actor.Actor
import akka.actor.ActorRef
import play.api.libs.json.Json
import play.api.Play.current
   
case class ExtractorMessage (
    id: String,
    host: String,
    key: String,
    metadata: Map[String, String]
)

/**
 * Rabbitmq service.
 *
 * @author Luigi Marini
 *
 */
class RabbitmqPlugin(application: Application) extends Plugin {

  var messageQueue: Option[ActorRef] = None
  
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
      val sendingChannel = connection.createChannel()
      sendingChannel.exchangeDeclare(exchange, "topic", true)
      messageQueue =  Some(Akka.system.actorOf(Props(new SendingActor(channel = sendingChannel, exchange = exchange))))
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
    messageQueue match {
      case Some(x) => x ! message
      case None => Logger.warn("Could not send message over RabbitMQ")
    }
  }
}

class SendingActor(channel: Channel, exchange: String) extends Actor {
 
  def receive = {
      case ExtractorMessage(id, host, key, metadata) => {
        val msgMap = scala.collection.mutable.Map(
            "id" -> Json.toJson(id),
            "host" -> Json.toJson(host)
            )
        metadata.foreach(kv => msgMap.put(kv._1,Json.toJson(kv._2)))
        val msg = Json.toJson(msgMap.toMap)
        Logger.info(msg.toString())
        channel.basicPublish(exchange, key, true, null, msg.toString().getBytes())
      }
      
      case _ => {
        Logger.error("Unknown message type submitted.")
      }
  }
}
