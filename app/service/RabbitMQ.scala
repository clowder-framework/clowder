package service
import com.rabbitmq.client.Connection
import com.rabbitmq.client.ConnectionFactory
import com.typesafe.config.ConfigFactory
import play.libs.Akka
import akka.util.duration._
import akka.actor.Props
import com.rabbitmq.client.Channel
import akka.actor.Actor
import play.Logger
import com.rabbitmq.client.QueueingConsumer
import akka.actor.ActorSystem
import akka.actor.PoisonPill

/**
 * RabbitMQ configuration.
 * 
 * Based on http://www.smartjava.org/content/connect-rabbitmq-amqp-using-scala-play-and-akka
 */
object RabbitMQ {
 
  val host = ConfigFactory.load().getString("rabbitmq.host");
  val queue = ConfigFactory.load().getString("rabbitmq.queue");
  val exchange = ConfigFactory.load().getString("rabbitmq.exchange");
  val factory = new ConnectionFactory();
  factory.setHost(host);
  val sendingChannel: Option[Channel] = None
  try {
    val connection: Connection = factory.newConnection()
    val sendingChannel = Some(connection.createChannel())
  } catch {
    case ioe: java.io.IOException => Logger.error("Error connecting to rabbitmq broker")
    case _ => Logger.error("Unknown error connecting to rabbitmq broker")
  }
  
  def extract(msg: String) = {
    sendingChannel match {
      case Some(channel) => {
        channel.queueDeclare(queue, false, false, false, null)
        val extract = Akka.system.actorOf(Props(new SendingActor(channel = channel, queue = queue))) 
        extract ! msg
      }
      case None => Logger.error("Extraction msg for file " + msg + " not sent. No channel available to rabbitmq broker.")
    }
//     Akka.system.registerOnTermination(() => {
//       Akka.system.stop(extract)
//       extract ! PoisonPill
//     })
  }
  
  
  
  object Sender {
 
    def startSending = {
      
     // setup listening
     val callback1 = (x: String) => Logger.info("QUEUE 1: Recieved on queue callback 1: " + x);
//     setupListener(connection.createChannel(),queue, callback1);
 
     // create an actor that starts listening on the specified queue and passes the
     // received message to the provided callback
     val callback2 = (x: String) => Logger.info("QUEUE 2: Recieved on queue callback 2: " + x);
 
     // setup the listener that sends to a specific queue using the SendingActor
//     setupListener(connection.createChannel(),queue, callback2);
      
     // setup sending
//     val sendingChannel = connection.createChannel()
//     sendingChannel.queueDeclare(queue, false, false, false, null)
// 
//     Akka.system.scheduler.schedule(2.seconds, 1 seconds, 
//       Akka.system.actorOf(Props(new SendingActor(channel = sendingChannel, 
//       queue = queue))), "Testing message");
     
//     Akka.system.registerOnTermination(() => Akka.system.shutdown)
    }
    
    private def setupListener(receivingChannel: Channel, queue: String, f: (String) => Any) {
      val listener = Akka.system.actorOf(Props(new ListeningActor(receivingChannel, queue, f)))
      val scheduled = Akka.system.scheduler.scheduleOnce(2 seconds, listener, "")

      Akka.system.registerOnTermination(() => {
        scheduled.cancel
        listener ! PoisonPill
        Akka.system.stop(listener)
      })
    }
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
 
class ListeningActor(channel: Channel, queue: String, f: (String) => Any) extends Actor {
 
  // called on the initial run
  def receive = {
    case _ => startReceving
  }
 
  def startReceving = {
 
    val consumer = new QueueingConsumer(channel);
    channel.basicConsume(queue, true, consumer);
 
    while (true) {
      // wait for the message
      val delivery = consumer.nextDelivery();
      val msg = new String(delivery.getBody());
 
      // send the message to the provided callback function
      // and execute this in a subactor
      context.actorOf(Props(new Actor {
        def receive = {
          case some: String => f(some);
        }
      })) ! msg
    }
  }
}
 