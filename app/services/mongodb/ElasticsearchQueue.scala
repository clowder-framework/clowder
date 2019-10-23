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

    action.target match {
      case Some(targ) => {
        action.action match {
          case "index_file" => {
            val target = files.get(targ.id) match {
              case Some(f) => current.plugin[ElasticsearchPlugin].foreach(p => p.index(f))
              case None => throw new NullPointerException(s"File ${targ.id.stringify} no longer found for indexing")
            }
          }
          case "index_dataset" => {
            val target = datasets.get(targ.id) match {
              case Some(ds) => current.plugin[ElasticsearchPlugin].foreach(p => p.index(ds, recursive))
              case None => throw new NullPointerException(s"Dataset ${targ.id.stringify} no longer found for indexing")
            }
          }
          case "index_collection" => {
            val target = collections.get(targ.id) match {
              case Some(c) => current.plugin[ElasticsearchPlugin].foreach(p => p.index(c, recursive))
              case None => throw new NullPointerException(s"Collection ${targ.id.stringify} no longer found for indexing")
            }
          }
          case "index_all" => _indexAll()
          case _ => throw new IllegalArgumentException(s"Unrecognized action: ${action.action}")
        }
      }
      case None => {
        action.action match {
          case "index_file" => throw new IllegalArgumentException(s"No target specified for action ${action.action}")
          case "index_dataset" => throw new IllegalArgumentException(s"No target specified for action ${action.action}")
          case "index_collection" => throw new IllegalArgumentException(s"No target specified for action ${action.action}")
          case "index_all" => _indexAll()
          case _ => throw new IllegalArgumentException(s"Unrecognized action: ${action.action}")
        }
      }
    }
  }

  def _indexAll() = {
    // Add all individual entries to the queue and delete this action
    current.plugin[ElasticsearchPlugin].foreach(p => {
      // delete & recreate index
      p.deleteAll
      p.createIndex()
      // queue everything for each resource type
      collections.indexAll()
      datasets.indexAll()
      files.indexAll()
    })
  }
}
