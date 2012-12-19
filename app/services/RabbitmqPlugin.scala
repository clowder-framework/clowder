package services

import play.api.{ Plugin, Logger, Application }
import com.typesafe.config.ConfigFactory
import com.rabbitmq.client.ConnectionFactory
import com.rabbitmq.client.Connection
import com.rabbitmq.client.Channel
import play.libs.Akka
import akka.actor.Props
import akka.actor.Actor

/**
 * Rabbitmq service.
 *
 * @author Luigi Marini
 *
 */
class RabbitmqPlugin(application: Application) extends Plugin {

  val host = ConfigFactory.load().getString("rabbitmq.host");
  val queue = ConfigFactory.load().getString("rabbitmq.queue");
  val exchange = ConfigFactory.load().getString("rabbitmq.exchange");
  var sendingChannel: Channel = null

  override def onStart() {
    Logger.debug("Starting up Rabbitmq plugin.")
    try {
      val factory = new ConnectionFactory();
      factory.setHost(host);
      val connection: Connection = factory.newConnection()
      sendingChannel = connection.createChannel()
    } catch {
      case ioe: java.io.IOException => Logger.error("Error connecting to rabbitmq broker")
      case _ => Logger.error("Unknown error connecting to rabbitmq broker")
    }
  }

  override def onStop() {
    Logger.debug("Shutting down Rabbitmq plugin.")
  }

  override lazy val enabled = {
    !application.configuration.getString("rabbitmqplugin").filter(_ == "disabled").isDefined
  }

  def extract(msg: String) = {
    sendingChannel.queueDeclare(queue, false, false, false, null)
    var extract = Akka.system.actorOf(Props(new SendingActor(channel = sendingChannel, queue = queue))) ! msg
  }
}

class SendingActor(channel: Channel, queue: String) extends Actor {
 
  def receive = {
    case some: String => {
      val msg = (some + " : " + System.currentTimeMillis());
      channel.basicPublish("", queue, null, msg.getBytes());
      Logger.info(msg);
    }
    case _ => {}
  }
}
