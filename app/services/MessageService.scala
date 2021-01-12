package services

import java.io.IOException

import com.ning.http.client.Realm.AuthScheme
import com.rabbitmq.client.AMQP.BasicProperties
import com.rabbitmq.client.{Channel, QueueingConsumer}
import models._
import play.api.Logger
import play.api.http.MimeTypes
import play.api.libs.json.{JsValue, Json}
import play.api.libs.ws.{Response, WS}

import scala.concurrent.Future

/**
 * Send/get messages from a message bus
 */

trait MessageService {

  /** Open connection to broker. **/
  def connect(): Boolean

  /** Close connection to broker. **/
  def close()

  /** Submit a message to broker. */
  def submit(exchange: String, routing_key: String, message: JsValue)

  def getRestEndPoint(path: String): Future[Response]

  /**
   * Get the exchange list for a given host
   */
  def getExchanges : Future[Response]

  /**
   * get list of queues attached to an exchange
   */
  def getQueuesNamesForAnExchange(exchange: String): Future[Response]

  /**
   * Get the binding lists (lists of routing keys) from the rabbitmq broker
   */
  def getBindings: Future[Response]

  /**
   * Get Channel list from rabbitmq broker
   */
  def getChannelsList: Future[Response]

  /**
   * Get queue details for a given queue
   */
  def getQueueDetails(qname: String): Future[Response]

  /**
   * Get queue bindings for a given host and queue from rabbitmq broker
   */
  def getQueueBindings(qname: String): Future[Response]

  /**
   * Get Channel information from rabbitmq broker for given channel id 'cid'
   */
  def getChannelInfo(cid: String): Future[Response]

  def cancelPendingSubmission(id: UUID, queueName: String, msg_id: UUID)

  /**
   * a helper function to get user email address from user's request api key.
   * @param requestAPIKey user request apikey
   * @return a list of email address
   */
  def getEmailNotificationEmailList(requestAPIKey: Option[String]): List[String]

  /**
   * loop through the queue and dispatch the message via the routing key.
   *
   * @param cancellationQueueConsumer  the queue consumer to download the requests from the cancellation downloaded queue
   * @param channel                    the channel connecting to the rabbitmq
   * @param cancellationSearchTimeout  the timeout of downloading the requests from the rabbitmq
   */
  def resubmitPendingRequests(cancellationQueueConsumer: QueueingConsumer, channel: Channel, cancellationSearchTimeout: Long)

}