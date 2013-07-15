/**
 *
 */
package api

import java.util.Date
import com.wordnik.swagger.annotations.Api
import com.wordnik.swagger.annotations.ApiOperation
import models.Comment
import models.Dataset
import models.File
import play.api.Logger
import play.api.libs.json.JsValue
import play.api.libs.json.Json
import play.api.libs.json.Json.toJson
import play.api.mvc.Action
import play.api.mvc.Controller
import services.Services
import jsonutils.JsonUtil
import scala.collection.JavaConversions._
import models.File
import models.FileDAO
import services.ElasticsearchPlugin

/**
 * Dataset API.
 *
 * @author Luigi Marini
 *
 */
@Api(value = "/datasets", listingPath = "/api-docs.{format}/datasets", description = "Maniputate datasets")
object Datasets extends Controller with ApiController {

  /**
   * List all datasets.
   */
  def list = Authenticated {
    Action {
      val list = for (dataset <- Services.datasets.listDatasets()) yield jsonDataset(dataset)
      Ok(toJson(list))
    }
  }
  
  /**
   * Create new dataset
   */
    def createDataset() = Action(parse.json) { request =>
      Logger.debug("Creating new dataset")
      (request.body \ "name").asOpt[String].map { name =>
      	  (request.body \ "description").asOpt[String].map { description =>
      	    (request.body \ "file_id").asOpt[String].map { file_id =>
      	      FileDAO.get(file_id) match {
      	        case Some(file) =>
      	           val d = Dataset(name=name,description=description, created=new Date(), files=List(file))
		      	   Dataset.insert(d) match {
		      	     case Some(id) => {
		      	       import play.api.Play.current
		      	        current.plugin[ElasticsearchPlugin].foreach{_.index("data", "dataset", id.toString, 
		      	        			List(("name",d.name), ("description", d.description)))}
		      	       Ok(toJson(Map("id" -> id.toString)))
		      	     }
		      	     case None => Ok(toJson(Map("status" -> "error")))
		      	   }
      	        case None => BadRequest(toJson("Bad file_id = " + file_id))
      	      }
      	   }.getOrElse {
      		BadRequest(toJson("Missing parameter [file_id]"))
      	  }
      	  }.getOrElse {
      		BadRequest(toJson("Missing parameter [description]"))
      	  }
      }.getOrElse {
        BadRequest(toJson("Missing parameter [name]"))
      }

    }

  def jsonDataset(dataset: Dataset): JsValue = {
    toJson(Map("id" -> dataset.id.toString, "datasetname" -> dataset.name, "description" -> dataset.description, "created" -> dataset.created.toString))
  }

  @ApiOperation(value = "Add metadata to dataset", notes = "Returns success of failure", responseClass = "None", httpMethod = "POST")
  def addMetadata(id: String) = Authenticated {
    Logger.debug("Adding metadata to dataset " + id)
    Action(parse.json) { request =>
      Logger.debug("Adding metadata to dataset " + id)
      Dataset.addMetadata(id, Json.stringify(request.body))
      Ok(toJson(Map("status" -> "success")))
    }
  }

  def addUserMetadata(id: String) = Action(parse.json) { request =>
      Logger.debug("Adding user metadata to dataset " + id)
      Dataset.addUserMetadata(id, Json.stringify(request.body))
      Ok(toJson(Map("status" -> "success")))
    }

  def datasetFilesGetIdByDatasetAndFilename(datasetId: String, filename: String): Option[String] = {
      Services.datasets.get(datasetId) match {
        case Some(dataset) => {
          //        val files = dataset.files map { f =>
          //          FileDAO.get(f.id.toString).get
          //        }		  
          for (file <- dataset.files) {
            if (file.filename.equals(filename)) {
              return Some(file.id.toString)
            }
          }
          Logger.error("File does not exist in dataset" + datasetId); return None
        }
        case None => { Logger.error("Error getting dataset" + datasetId); return None }
      }
    }

  def datasetFilesList(id: String) = Authenticated {
    Action {
      Services.datasets.get(id) match {
        case Some(dataset) => {
          //        val files = dataset.files map { f =>
          //          FileDAO.get(f.id.toString).get
          //        }
          val list = for (f <- dataset.files) yield jsonFile(f)
          Ok(toJson(list))
        }
        case None => { Logger.error("Error getting dataset" + id); InternalServerError }
      }
    }
  }

  def jsonFile(file: File): JsValue = {
    toJson(Map("id" -> file.id.toString, "filename" -> file.filename, "contentType" -> file.contentType, "date-created" -> file.uploadDate.toString(), "size" -> file.length.toString))
  }

  def tag(id: String) = SecuredAction(parse.json, allowKey = false) { implicit request =>
    request.body.\("tag").asOpt[String] match {
      case Some(tag) => {
        Dataset.tag(id, tag)
        Ok
      }
      case None => {
        Logger.error("no tag specified.")
        BadRequest
      }
    }
  }

  def comment(id: String) = SecuredAction(parse.json, allowKey = false) { implicit request =>
    request.body.\("comment").asOpt[String] match {
      case Some(comment) => {
        Dataset.comment(id, new Comment(request.user.email.get, new Date(), comment))
        Ok
      }
      case None => {
        Logger.error("no tag specified.")
        BadRequest
      }
    }
  }

  /**
   * List datasets satisfying a user metadata search tree.
   */
  def searchDatasetsUserMetadata =
    Action(parse.json) { request =>
      Logger.debug("Searching datasets' user metadata for search tree.")
      var searchTree = JsonUtil.parseJSON(Json.stringify(request.body)).asInstanceOf[java.util.LinkedHashMap[String, Any]]
      var datasetsSatisfying = List[Dataset]()
      for (dataset <- Services.datasets.listDatasetsChronoReverse) {
        if (Dataset.searchUserMetadata(dataset.id.toString(), searchTree)) {
          datasetsSatisfying = dataset :: datasetsSatisfying
        }
      }
      datasetsSatisfying = datasetsSatisfying.reverse

      Logger.debug("Search completed. Returning datasets list.")

      val list = for (dataset <- datasetsSatisfying) yield jsonDataset(dataset)
      Logger.debug("thelist: " + toJson(list))
      Ok(toJson(list))
    }
}
