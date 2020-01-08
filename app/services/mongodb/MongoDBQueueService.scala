package services.mongodb

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
import com.novus.salat.dao.{ModelCompanion, SalatDAO}
import org.bson.types.ObjectId
import scala.concurrent.duration._
import play.api.libs.concurrent.Execution.Implicits._

import services.QueueService


/**
 *  Queue service that keeps queues in persistent Mongo collection.
 *
 */
class MongoDBQueueService extends QueueService {
  val defaultQueueName: String = "default_queue"

  // return object description of Queue Service status
  def status(queueName: String): JsObject = {
    current.plugin[MongoSalatPlugin] match {
      case None => throw new RuntimeException("No MongoSalatPlugin");
      case Some(x) => {
        val dao = new SalatDAO[QueuedAction, ObjectId](collection = x.collection(s"queue.${queueName}")) {}
        Json.obj("enabled" -> true, "queued" -> dao.count())
      }
    }
  }

  // add action to the queue/
  def queue(action: String, queueName: String): Boolean =
    _queue(new QueuedAction(action=action), queueName)

  // add action to the queue with handler parameters
  def queue(action: String, parameters: ElasticsearchParameters, queueName: String): Boolean =
    _queue(new QueuedAction(action=action, elastic_parameters=Some(parameters)), queueName)

  // add action to the queue with target resource
  def queue(action: String, target: ResourceRef, queueName: String): Boolean =
    _queue(new QueuedAction(action=action, target=Some(target)), queueName)

  // add action to the queue with target resource and handler parameters
  def queue(action: String, target: ResourceRef, parameters: ElasticsearchParameters, queueName: String): Boolean =
    _queue(new QueuedAction(action=action, target=Some(target), elastic_parameters=Some(parameters)), queueName)

  private def _queue(queueAction: QueuedAction, queueName: String): Boolean = {
    current.plugin[MongoSalatPlugin] match {
      case None => throw new RuntimeException("No MongoSalatPlugin");
      case Some(x) => {
        val dao = new SalatDAO[QueuedAction, ObjectId](collection = x.collection(s"queue.${queueName}")) {}
        dao.insert(queueAction) match {
          case Some(id) => true
          case None => false
        }
      }
    }
  }

  // get next entry from queue
  def getNextQueuedAction(queueName: String): Option[QueuedAction] = {
    current.plugin[MongoSalatPlugin] match {
      case None => throw new RuntimeException("No MongoSalatPlugin");
      case Some(x) => {
        val dao = new SalatDAO[QueuedAction, ObjectId](collection = x.collection(s"queue.${queueName}")) {}
        dao.findOne(new MongoDBObject)
      }
    }
  }

  // remove entry from queue
  def removeQueuedAction(action: QueuedAction, queueName: String) = {
    current.plugin[MongoSalatPlugin] match {
      case None => throw new RuntimeException("No MongoSalatPlugin");
      case Some(x) => {
        val dao = new SalatDAO[QueuedAction, ObjectId](collection = x.collection(s"queue.${queueName}")) {}
        dao.remove(action)
      }
    }
  }
}


