package controllers

import play.api.mvc._
import services.ElasticsearchPlugin
import services.VersusPlugin
import play.Logger
import scala.collection.JavaConversions.mapAsScalaMap
import services.Services
import sys.process._
import java.net.URL
import java.io.File
import scala.io.Source
import java.io._
import play.api.libs.ws.WS

import models.SectionDAO
import org.bson.types.ObjectId
import models.PreviewDAO
import models.MultimediaFeatures
import models.MultimediaFeaturesDAO
import com.mongodb.casbah.commons.MongoDBObject
import edu.illinois.ncsa.isda.lsva.ImageMeasures
import edu.illinois.ncsa.isda.lsva.ImageDescriptors.FeatureType
import scala.collection.mutable.LinkedList
import scala.collection.mutable.HashMap
import scala.collection.mutable.ListBuffer
import util.DistancePriorityQueue
import util.DistancePriorityQueue
import org.elasticsearch.action.search.SearchRequest
import util.SearchResult
import util.DistancePriorityQueue
import play.api.libs.ws.WS
import scala.concurrent.Future
import play.api.libs.concurrent.Execution.Implicits._
import java.io.FileInputStream
import play.api.libs.json.JsValue
import play.api.libs.json.Json
import scala.collection.mutable.ArrayBuffer
import models.Result
import models.SimilarityResult
import play.api.libs.json.Reads
import play.api.libs.json.JsArray
import models.TempFileDAO
import com.mongodb.DBCollection
import play.api.Play.current
import play.api.libs.concurrent._

import scala.concurrent.{ future, blocking, Future, Await }
import scala.concurrent.ExecutionContext.Implicits.global

import scala.collection.mutable.ArrayBuffer

/**
 * Text search.
 *
 * @author Luigi Marini
 */
object Search extends Controller {

  /**
   * Search results.
   */
  def search(query: String) = Action {
    Logger.debug("Searching for: " + query)
    import play.api.Play.current
    val result = current.plugin[ElasticsearchPlugin].map { _.search("files", query) }
    result match {
      case Some(searchResult) => {
        for (hit <- searchResult.hits().hits()) {
          Logger.debug("Search result: " + hit.getExplanation())
          Logger.info("Fields: ")
          for ((key, value) <- mapAsScalaMap(hit.getFields())) {
            Logger.info(value.getName + " = " + value.getValue())
          }
          Services.files.getFile(hit.getId())
          val files = result.get.hits().hits().map(hit => Services.files.getFile(hit.getId()).get)
          Ok(views.html.searchResults(query, files))
        }
      }
      case None => {
    	  Ok(views.html.searchResults(query, Array.empty))
      }
    }
    Ok(views.html.searchResults(query, Array.empty))
  }

  def multimediasearch() = Action {
    Logger.debug("Starting multimedia search interface")
    Ok(views.html.multimediasearch())
    //Ok("Sucessful")
  }

  /**
   * Search MultimediaFeatures.
   */
  def searchMultimediaIndex(section_id: String) = Action {
    Logger.debug("Searching multimedia index")
    // TODO handle multiple previews found
    val preview = PreviewDAO.findBySectionId(new ObjectId(section_id))(0)
    MultimediaFeaturesDAO.findOne(MongoDBObject("section_id" -> new ObjectId(section_id))) match {
      case Some(feature) => {
        // setup priority queues
        val queues = new HashMap[String, DistancePriorityQueue]
        val representations = feature.features.map { f =>
          queues(f.representation) = new DistancePriorityQueue(20)
        }
        // push into priority queues
        feature.features.map { f =>
          Logger.debug("Computing " + f.representation)
          MultimediaFeaturesDAO.find(MongoDBObject()).toList.map { mf =>
            Logger.trace("Found multimedia features " + mf.id + " for section " + section_id)
            mf.features.find(_.representation == f.representation) match {
              case Some(fd) => {
                val distance = ImageMeasures.getDistance(FeatureType.valueOf(fd.representation), f.descriptor.toArray, fd.descriptor.toArray)
                if (!distance.isNaN()) {
                  Logger.trace(f.representation + "/" + fd.representation + " Distance between " + feature.section_id + " and " + mf.id + " is " + distance)
                  queues.get(f.representation).map { q =>
                    val popped = q.insertWithOverflow(SearchResult(mf.section_id.get.toString, distance, None))
                    if (popped != null) Logger.trace("Popped distance off the queue " + popped)
                  }
                } else {
                  Logger.debug("Distance NaN " + f.descriptor + " and " + fd.descriptor + " is " + distance)
                }
              }
              case None =>
                Logger.error("Matching descriptor not found")
            }
          }
        }
        //        // grab first 10 items from priority queues
        //        val items = new HashMap[String, ListBuffer[SearchResult]]
        //        for(i <- 1 to 10) {
        //          Logger.debug("test")
        //          queues.foreach { case (k,v) =>
        //            
        //            if (items.contains(k)) {
        //              if (v.size > 0) items(k) += v.pop
        //            } else {
        //              items += k -> (new ListBuffer() += v.pop)
        //            }: Unit // TODO fixed in scala 2.10 https://issues.scala-lang.org/browse/SI-4938
        //          }
        //           Logger.debug("test")
        //        }

        //        val queuesWithPreviews = queues map {case (key, queue) =>
        //          val newQueue = new DistancePriorityQueue(queue.size())
        //          while (queue.size > 0) {
        //            val element = queue.pop()
        //            val previews = PreviewDAO.findBySectionId(new ObjectId(element.id))
        //            if (previews.size == 1) {
        //              newQueue.add(SearchResult(element.id, element.distance, Some(previews(0).id.toString)))
        //            } else {
        //              Logger.error("Found more/less than one preview " + preview)
        //            }
        //          }
        //        }

        val items = new HashMap[String, ListBuffer[SearchResult]]
        queues map {
          case (key, queue) =>
            val list = new ListBuffer[SearchResult]
            while (queue.size > 0) {
              val element = queue.pop()
              val previews = PreviewDAO.findBySectionId(new ObjectId(element.section_id))
              if (previews.size == 1) {
                Logger.trace("Appended search result " + key + " " + element.section_id + " " + element.distance + " " + previews(0).id.toString)
                list.prepend(SearchResult(element.section_id, element.distance, Some(previews(0).id.toString)))
              } else {
                Logger.error("Found more/less than one preview " + preview)
              }
            }
            items += key -> list
        }

        Ok(views.html.searchMultimediaIndex(preview, items))
      }
      case None => InternalServerError("feature not found")
    }
  }

  def advanced() = Action {
    Logger.debug("Starting Advanced Search interface")
    Ok(views.html.advancedsearch())
  }

  def SearchByText(query: String) = Action {
    Logger.debug("Searching for" + query)

    //Ok(views.html.searchTextResults(query))
    Ok("")
  }

  //GET the query image from the URL and compare within the database and show the result
  def searchbyURL(query: String) = Action {
    Logger.debug("Searching for" + query)
    var slashindex = query.lastIndexOf('/')

    Async {
      current.plugin[VersusPlugin] match {

        case Some(plugin) => {
          plugin.queryURL(query).map { result =>
            val l = result.size
            Ok(views.html.searchTextResults(query.substring(slashindex + 1), l, result))

          }
        } // case some
        case None => {
          Future(Ok("No Versus Service"))
        }
      } //match
    } //Async
  }

  /* Find Similar files*/
  def findSimilar(id: String) = Action {
    Async {
      current.plugin[VersusPlugin] match {

        case Some(plugin) => {
          plugin.query(id).map { result =>
            Services.queries.getFile(id) match {
              case Some(file) => {
                Logger.debug("file id=" + file.id.toString())
                val l = result.size
                Ok(views.html.searchImgResults(file, id, l, result))
              }
              case None => {
                Ok(id + " not found")
              }
            }
          }

        } // case some
        case None => {
          Future(Ok("No Versus Service"))
        }
      } //match
    } //Async
  }

  def Filterby(id: String) = TODO

  def uploadquery() = Action(parse.multipartFormData) { request =>
    request.body.file("picture").map { picture =>
      import java.io.File
      val filename = picture.filename
      val contentType = picture.contentType
      picture.ref.moveTo(new File("/tmp/picture"))
      Ok("File uploaded")
    }.getOrElse {
      Redirect(routes.Application.index).flashing(
        "error" -> "Missing file")
    }
  }
  /*Action(parse.multipartFormData) { request =>
  request.body.file("picture").map { picture =>
    import java.io.File
    val filename = picture.filename 
    val contentType = picture.contentType
    picture.ref.moveTo(new File("/tmp/picture"))
    Ok("File uploaded")
  }.getOrElse {
    Redirect(routes.Application.index).flashing(
      "error" -> "Missing file"
    )
  }
}*/
}