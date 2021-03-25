package services

import javax.inject.Inject

import models._
import play.api.Logger
import play.api.Play._
import play.api.libs.json.Json._
import play.api.libs.json.{Json, JsValue, Writes}
import services._

/**
 * Elasticsearch queue service to allow code that updates ES indexes to proceed asynchronously.
 *
 */
class ElasticsearchQueue @Inject() (
  files: FileService,
  datasets: DatasetService,
  collections: CollectionService,
  sections: SectionService) extends MongoDBQueueService {

  override val consumer = "elasticsearch"

  // check whether necessary conditions are met (e.g. the plugin is enabled)
  override def enabled(): Boolean = {
    return current.plugin[ElasticsearchPlugin].exists(es => es.isEnabled())
  }

  // process the next entry in the queue
  def handler(action: QueuedAction) = {
    val recursive = action.elastic_parameters.fold(false)(_.recursive)
    val idx: Option[String] = action.elastic_parameters.fold[Option[String]](None)(_.index)

    action.target match {
      case Some(targ) => {
        action.action match {
          case "index_file" => {
            val target = files.get(targ.id) match {
              case Some(f) => current.plugin[ElasticsearchPlugin].foreach(p => p.index(f, idx))
              case None => throw new NullPointerException(s"File ${targ.id.stringify} no longer found for indexing")
            }
          }
          case "index_dataset" => {
            val target = datasets.get(targ.id) match {
              case Some(ds) => current.plugin[ElasticsearchPlugin].foreach(p => p.index(ds, recursive, idx))
              case None => throw new NullPointerException(s"Dataset ${targ.id.stringify} no longer found for indexing")
            }
          }
          case "index_collection" => {
            val target = collections.get(targ.id) match {
              case Some(c) => current.plugin[ElasticsearchPlugin].foreach(p => p.index(c, recursive, idx))
              case None => throw new NullPointerException(s"Collection ${targ.id.stringify} no longer found for indexing")
            }
          }
          case "index_all" => _indexAll()
          case "delete_index" => _deleteIndex()
          case "index_swap" => _swapIndex()
          case _ => throw new IllegalArgumentException(s"Unrecognized action: ${action.action}")
        }
      }
      case None => {
        action.action match {
          case "index_file" => throw new IllegalArgumentException(s"No target specified for action ${action.action}")
          case "index_dataset" => throw new IllegalArgumentException(s"No target specified for action ${action.action}")
          case "index_collection" => throw new IllegalArgumentException(s"No target specified for action ${action.action}")
          case "index_all" => _indexAll()
          case "delete_index" => _deleteIndex()
          case "index_swap" => _swapIndex()
          case _ => throw new IllegalArgumentException(s"Unrecognized action: ${action.action}")
        }
      }
    }
  }

  def _indexAll() = {
    val swap = false

    // Add all individual entries to the queue and delete this action
    current.plugin[ElasticsearchPlugin].foreach(p => {
      if (swap) {
        val idx = p.nameOfIndex + "_reindex_temp_swap"
        Logger.debug("Reindexing database into temporary reindex file: "+idx)
        p.createIndex(idx)

        // queue everything for each resource type
        collections.indexAll(Some(idx))
        datasets.indexAll(Some(idx))
        files.indexAll(Some(idx))

        // queue action to swap index once we're done reindexing
        p.queue.queue("index_swap")
      } else {
        // TODO: This does not delete the index first! It will need to do so in some cases!
        p.createIndex()
        collections.indexAll()
        datasets.indexAll()
        files.indexAll()
      }
    })
  }

  def _deleteIndex() = {
    current.plugin[ElasticsearchPlugin].foreach(p => {
      p.deleteAll()
    })
  }

  // Replace the main index with the newly reindexed temp file
  def _swapIndex() = {
    Logger.debug("Swapping temporary reindex for main index")
    current.plugin[ElasticsearchPlugin].foreach(p => {
      p.swapIndex(p.nameOfIndex + "_reindex_temp_swap")
    })
  }
}
