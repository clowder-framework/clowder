package services.mongodb

import com.mongodb.casbah.Imports._
import models._
import org.bson.types.ObjectId
import play.api.libs.json.{JsObject, Json}
import salat.dao.SalatDAO
import services.mongodb.MongoContext.context
import services.{DI, QueueService}


/**
 *  Queue service that keeps queues in persistent Mongo collection.
 *
 */
class MongoDBQueueService extends QueueService {
  val defaultQueueName: String = "default_queue"

  val mongos: MongoStartup = DI.injector.getInstance(classOf[MongoStartup])

  // return object description of Queue Service status
  def status(queueName: String): JsObject = {
    val dao = new SalatDAO[QueuedAction, ObjectId](collection = mongos.collection(s"queue.${queueName}")) {}
    Json.obj("enabled" -> true, "queued" -> dao.count())
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
    val dao = new SalatDAO[QueuedAction, ObjectId](collection = mongos.collection(s"queue.${queueName}")) {}
    dao.insert(queueAction) match {
      case Some(id) => true
      case None => false
    }
  }

  // get next entry from queue
  def getNextQueuedAction(queueName: String): Option[QueuedAction] = {
    val dao = new SalatDAO[QueuedAction, ObjectId](collection = mongos.collection(s"queue.${queueName}")) {}
    dao.findOne(new MongoDBObject)

  }

  // remove entry from queue
  def removeQueuedAction(action: QueuedAction, queueName: String) = {
    val dao = new SalatDAO[QueuedAction, ObjectId](collection = mongos.collection(s"queue.${queueName}")) {}
    dao.remove(action)
  }
}


