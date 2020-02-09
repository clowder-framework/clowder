package services

import java.util.Date

import api.Permission.Permission
import models.{File, _}
import play.api.Logger
import play.api.Play._
import play.api.libs.json.{JsObject, Json}

import scala.concurrent.duration._
import scala.collection.mutable.{Map => MemMap}
import play.api.libs.concurrent.Execution.Implicits._

import scala.collection.mutable.ListBuffer

/**
 * Queue service that keeps queues in transient memory.
 *
 */
class MemoryQueueService extends QueueService {
  val defaultQueueName: String = "default_queue"
  var queues: MemMap[String, ListBuffer[QueuedAction]] = MemMap[String, ListBuffer[QueuedAction]]()

  // return object description of Queue Service status
  def status(queueName: String): JsObject = {
    queues.get(queueName) match {
      case Some(q) => Json.obj("enabled" -> true, "queued" -> q.length)
      case None => Json.obj("enabled" -> true, "queued" -> 0)
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
    queues.get(queueName) match {
      case Some(q) => queues(queueName) = (q += queueAction)
      case None => queues(queueName) = ListBuffer(queueAction)
    }
    true
  }

  // get next entry from queue
  def getNextQueuedAction(queueName: String): Option[QueuedAction] = {
    queues.get(queueName) match {
      case Some(q) => q.headOption
      case None => None
    }
  }

  // remove entry from queue
  def removeQueuedAction(action: QueuedAction, queueName: String) = {
    queues.get(queueName) match {
      case Some(q) => queues(queueName) = (q -= action)
      case None => Logger.debug(s"No queue found with name $queueName to remove from")
    }
  }
}


