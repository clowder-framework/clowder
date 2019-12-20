package services

import java.util.Date

import akka.actor.Cancellable
import api.Permission.Permission
import com.mongodb.casbah.Imports._
import com.novus.salat.dao.{SalatDAO, ModelCompanion}
import models.{File, _}
import org.bson.types.ObjectId
import play.api.Logger
import play.api.Play._
import play.api.libs.json.{Json, JsObject}
import play.libs.Akka
import services.mongodb.MongoSalatPlugin
import services.mongodb.MongoContext.context
import scala.concurrent.duration._
import play.api.libs.concurrent.Execution.Implicits._

/**
 * Generic queue service.
 *
 */
trait QueueService {
  // return object description of Queue Service status
  def status(queueName: String): JsObject

  // add action to the queue
  def queue(action: String, queueName: String): Boolean

  // add action to the queue with handler parameters
  def queue(action: String, parameters: ElasticsearchParameters, queueName: String): Boolean

  // add action to the queue with target resource
  def queue(action: String, target: ResourceRef, queueName: String): Boolean

  // add action to the queue with target resource and handler parameters
  def queue(action: String, target: ResourceRef, parameters: ElasticsearchParameters, queueName: String): Boolean

  // get next entry from queue
  def getNextQueuedAction(queueName: String): Option[QueuedAction]

  // remove entry from queue
  def removeQueuedAction(action: QueuedAction, queueName: String)
}
