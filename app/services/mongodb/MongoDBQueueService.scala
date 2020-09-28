package services

import akka.actor.Cancellable
import com.mongodb.casbah.Imports._
import com.mongodb.MongoException
import com.novus.salat.dao.{ModelCompanion, SalatDAO}
import models.{ElasticsearchParameters, QueuedAction, ResourceRef}
import org.bson.types.ObjectId
import play.api.Logger
import play.api.Play._
import play.api.libs.json.{JsObject, Json}
import play.libs.Akka
import services.mongodb.MongoSalatPlugin
import services.mongodb.MongoContext.context

import scala.concurrent.duration._
import scala.concurrent.ExecutionContext

/**
 * Generic queue service.
 *
 */
trait MongoDBQueueService {
  val consumer: String
  val threads: Int = 1
  val batchSize: Int = 50
  val interval: FiniteDuration = 5 milliseconds
  var ec : ExecutionContext = null
  var disabledNotified: Boolean = false
  var queueFetchError: Boolean = false

  // check whether necessary conditions are met (e.g. the plugin is enabled)
  def enabled(): Boolean = {
    return false
  }

  def status(): JsObject = {
    if (enabled) {
      Json.obj("enabled" -> true, "queued" -> Queue.count())
    } else {
      Json.obj("enabled" -> false)
    }
  }

  // add action to the queue
  def queue(action: String): Boolean = _queue(new QueuedAction(action=action))

  // add action to the queue with handler parameters
  def queue(action: String, parameters: ElasticsearchParameters): Boolean = _queue(new QueuedAction(action=action, elastic_parameters=Some(parameters)))

  // add action to the queue with target resource
  def queue(action: String, target: ResourceRef): Boolean = _queue(new QueuedAction(action=action, target=Some(target)))

  // add action to the queue with target resource and handler parameters
  def queue(action: String, target: ResourceRef, parameters: ElasticsearchParameters): Boolean = _queue(new QueuedAction(action=action, target=Some(target), elastic_parameters=Some(parameters)))

  private def _queue(queueAction: QueuedAction): Boolean = {
    if (enabled) {
      Queue.insert(queueAction) match {
        case Some(id) => true
        case None => false
      }
    } else {
      if (!disabledNotified) {
        Logger.debug(s"Queuing for ${consumer} is disabled")
        disabledNotified = true
      }
      false
    }
  }

  // get next entries from queue
  def getBatchQueuedAction(): List[QueuedAction] = {
    try {
      val response = Queue.find(new MongoDBObject).limit(batchSize)
      if (queueFetchError) {
        Logger.info("MongoDB has successfully reconnected.")
        queueFetchError = false
      }
      response.toList
    } catch {
      case e: MongoException => {
        // Only log an error once on failed fetch, instead of repeating every 5ms
        if (!queueFetchError) {
          Logger.error("Problem connecting to MongoDB queue.")
          queueFetchError = true
        }
        List.empty[QueuedAction]
      }
      case _ => List.empty[QueuedAction]
    }
  }

  // start processing queue actions
  def listen() = {
    if (ec == null) {
      ec = ExecutionContext.fromExecutor(new java.util.concurrent.ForkJoinPool(threads))
      for (_ <- 1 to threads) {
        Akka.system().scheduler.schedule(0 seconds, interval) {
          getBatchQueuedAction().foreach(qa => handleQueuedAction(qa))
        }(ec)
      }
    }
  }

  // wrapper for processing next action in queue
  def handleQueuedAction(action: QueuedAction) = {
    try {
      handler(action)
      Queue.remove(action)
    }
    catch {
      case except: Throwable => {
        Logger.error(s"Error handling ${action.action}: ${except}")
        Queue.remove(action)
      }
    }
  }

  // process the next action in the queue
  def handler(action: QueuedAction)

  object Queue extends ModelCompanion[QueuedAction, ObjectId] {
    val dao = current.plugin[MongoSalatPlugin] match {
      case None => throw new RuntimeException("No MongoSalatPlugin");
      case Some(x) => new SalatDAO[QueuedAction, ObjectId](collection = x.collection(s"queue.${consumer}")) {}
    }
  }
}
