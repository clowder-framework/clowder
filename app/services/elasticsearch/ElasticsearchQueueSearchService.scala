package services.elasticsearch

import play.libs.Akka
import play.api.{Application, Logger, Plugin}
import play.api.libs.json._
import play.api.libs.concurrent.Execution.Implicits._
import akka.actor.Cancellable
import scala.concurrent.duration._
import javax.inject.{Inject, Singleton}
import java.util.Date


import models.{ResourceRef, QueuedAction}
import services.{CollectionService, CommentService, DatasetService, QueueService, FileService, FolderService,
  MetadataService}


/**
 * Elasticsearch service that uses a queue to process indexing asynchronously.
 *
 */
@Singleton
class ElasticsearchQueueSearchService @Inject() (comments: CommentService,
                                                 files: FileService,
                                                 folders: FolderService,
                                                 datasets: DatasetService,
                                                 collections: CollectionService,
                                                 metadatas: MetadataService,
                                                 queue: QueueService) extends ElasticsearchSearchService(comments, files, folders, datasets, collections, metadatas) {
  var queueTimer: Cancellable = null
  val queueName = "elasticsearch"

  // Start listening for messages from the queue
  listen()

  // start pool to being processing queue actions
  def listen() = {
    if (queueTimer == null) {
      // TODO: Need to make these in a separate pool
      queueTimer = Akka.system().scheduler.schedule(0 seconds, 5 millis) {
        queue.getNextQueuedAction(queueName) match {
          case Some(qa) => handleQueuedAction(qa)
          case None => {}
        }
      }
    }
  }

  // wrapper for processing next action in queue
  def handleQueuedAction(action: QueuedAction) = {
    try {
      handler(action)
      queue.removeQueuedAction(action, queueName)
    }
    catch {
      case except: Throwable => {
        Logger.error(s"Error handling ${action.action}: ${except}")
        queue.removeQueuedAction(action, queueName)
      }
    }
  }

  // process the next entry in the queue
  def handler(action: QueuedAction) = {
    val recursive = action.elastic_parameters.fold(false)(_.recursive)

    action.target match {
      case Some(targ) => {
        action.action match {
          case "index_file" => {
            val target = files.get(targ.id) match {
              case Some(f) => index(f)
              case None => throw new NullPointerException(s"File ${targ.id.stringify} no longer found for indexing")
            }
          }
          case "index_dataset" => {
            val target = datasets.get(targ.id) match {
              case Some(ds) => index(ds, recursive)
              case None => throw new NullPointerException(s"Dataset ${targ.id.stringify} no longer found for indexing")
            }
          }
          case "index_collection" => {
            val target = collections.get(targ.id) match {
              case Some(c) => index(c, recursive)
              case None => throw new NullPointerException(s"Collection ${targ.id.stringify} no longer found for indexing")
            }
          }
          case "index_all" => indexAll()
          case _ => throw new IllegalArgumentException(s"Unrecognized action: ${action.action}")
        }
      }
      case None => {
        action.action match {
          case "index_file" => throw new IllegalArgumentException(s"No target specified for action ${action.action}")
          case "index_dataset" => throw new IllegalArgumentException(s"No target specified for action ${action.action}")
          case "index_collection" => throw new IllegalArgumentException(s"No target specified for action ${action.action}")
          case "index_all" => indexAll()
          case _ => throw new IllegalArgumentException(s"Unrecognized action: ${action.action}")
        }
      }
    }
  }

  override def getInformation(): JsObject = {
    Json.obj("server" -> serverAddress,
      "clustername" -> nameOfCluster,
      "queue" -> queue.status(queueName),
      "status" -> "connected")
  }

  /**
   * Reindex using a resource reference and route to correct handler
   */
  override def index(resource: ResourceRef, recursive: Boolean = true) = {
    resource.resourceType match {
      case 'file => {
        files.get(resource.id) match {
          case Some(_) => queue.queue("index_file", resource, queueName)
          case None => Logger.error(s"File ID not found: ${resource.id.stringify}")
        }
      }
      case 'dataset => {
        datasets.get(resource.id) match {
          case Some(_) => queue.queue("index_dataset", resource, queueName)
          case None => Logger.error(s"Dataset ID not found: ${resource.id.stringify}")
        }
      }
      case 'collection => {
        collections.get(resource.id) match {
          case Some(_) => queue.queue("index_collection", resource, queueName)
          case None => Logger.error(s"Collection ID not found: ${resource.id.stringify}")
        }
      }
    }
  }

  override def indexAll(): String = {
    // Add all individual entries to the queue and delete this action
    // delete & recreate index
    deleteAll()
    createIndex()
    // queue everything for each resource type
    collections.indexAll()
    datasets.indexAll()
    files.indexAll()
    "Reindexing successfully queued."
  }
}
