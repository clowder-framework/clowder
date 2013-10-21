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
import scala.collection.mutable.ListBuffer
import models.File
import models.Tag
import models.FileDAO
import models.Extraction
import services.ElasticsearchPlugin
import controllers.Previewers
import models.File
import play.api.libs.json.JsObject
import play.api.libs.json.JsString
import play.api.Routes
import controllers.SecuredController
import models.Collection
import org.bson.types.ObjectId
import securesocial.views.html.notAuthorized
import play.api.Play.current

import services.Services
import scala.util.parsing.json.JSONArray
/**
 * Dataset API.
 *
 * @author Luigi Marini
 *
 */
object ActivityFound extends Exception { }

@Api(value = "/datasets", listingPath = "/api-docs.{format}/datasets", description = "Maniputate datasets")
object Datasets extends ApiController {

  /**
   * List all datasets.
   */
  def list = SecuredAction(parse.anyContent, authorization=WithPermission(Permission.ListDatasets)) { request =>    
      val list = for (dataset <- Services.datasets.listDatasets()) yield jsonDataset(dataset)
      Ok(toJson(list))
  }
  
    /**
   * List all datasets outside a collection.
   */
  def listOutsideCollection(collectionId: String) = SecuredAction(parse.anyContent, authorization=WithPermission(Permission.ListDatasets)) { request =>
      Collection.findOneById(new ObjectId(collectionId)) match{
        case Some(collection) => {
          val list = for (dataset <- Services.datasets.listDatasetsChronoReverse; if(!isInCollection(dataset,collection))) yield jsonDataset(dataset)
          Ok(toJson(list))
        }
        case None =>{
          val list = for (dataset <- Services.datasets.listDatasetsChronoReverse) yield jsonDataset(dataset)
          Ok(toJson(list))
        } 
      }
  }
  
  def isInCollection(dataset: Dataset, collection: Collection): Boolean = {
    for(collDataset <- collection.datasets){
      if(collDataset.id == dataset.id)
        return true
    }
    return false
  }
  
  /**
   * Create new dataset
   */
    def createDataset() = SecuredAction(authorization=WithPermission(Permission.CreateDatasets)) { request =>
      Logger.debug("Creating new dataset")
      (request.body \ "name").asOpt[String].map { name =>
      	  (request.body \ "description").asOpt[String].map { description =>
      	    (request.body \ "file_id").asOpt[String].map { file_id =>
      	      FileDAO.get(file_id) match {
      	        case Some(file) =>
      	           val d = Dataset(name=name,description=description, created=new Date(), files=List(file), author=request.user.get)
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
  def addMetadata(id: String) = SecuredAction(authorization=WithPermission(Permission.AddDatasetsMetadata)) { request =>
      Logger.debug("Adding metadata to dataset " + id)
      Dataset.addMetadata(id, Json.stringify(request.body))
      Ok(toJson(Map("status" -> "success")))
  }

  def addUserMetadata(id: String) = SecuredAction(authorization=WithPermission(Permission.AddDatasetsMetadata)) { request =>
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

  def datasetFilesList(id: String) = SecuredAction(parse.anyContent, authorization=WithPermission(Permission.ShowDataset)) { request =>
      Services.datasets.get(id) match {
        case Some(dataset) => {
          val list = for (f <- dataset.files) yield jsonFile(f)
          Ok(toJson(list))
        }
        case None => { Logger.error("Error getting dataset" + id); InternalServerError }
      }
    }

  def jsonFile(file: File): JsValue = {
    toJson(Map("id" -> file.id.toString, "filename" -> file.filename, "contentType" -> file.contentType, "date-created" -> file.uploadDate.toString(), "size" -> file.length.toString))
  }

  def index(id: String) {
    Services.datasets.get(id) match {
      case Some(dataset) => {
        var tagListBuffer = new ListBuffer[String]()
        
        for (tag <- dataset.tags){
          tagListBuffer += tag.name
        }          
        
        val tagsJson = new JSONArray(tagListBuffer.toList)

        Logger.debug("tagStr=" + tagsJson);

        val comments = for(comment <- Comment.findCommentsByDatasetId(id,false)) yield {
          comment.text
        }
        val commentJson = new JSONArray(comments)

        Logger.debug("commentStr=" + commentJson.toString())

        current.plugin[ElasticsearchPlugin].foreach {
          _.index("data", "dataset", id,
            List(("name", dataset.name), ("description", dataset.description), ("tag", tagsJson.toString), ("comments", commentJson.toString)))
        }
      }
      case None => Logger.error("Dataset not found: " + id)
    }
  }

  def tag(id: String) = SecuredAction(parse.json, authorization = WithPermission(Permission.CreateTags)) { implicit request =>
    Logger.debug("Tagging " + request.body)
    
    val userObj = request.user;
    val tagId = new ObjectId
    
    request.body.\("tag").asOpt[String].map { tag =>
      Logger.debug("Tagging " + id + " with " + tag)
      val tagObj = Tag(id = tagId, name = tag, userId = userObj.get.identityId.toString, created = new Date)
      Dataset.tag(id, tagObj)
      index(id)
    }
    Ok(toJson(tagId.toString()))
  }
    
  def removeTag(id: String) = SecuredAction(parse.json,authorization=WithPermission(Permission.DeleteTags)) {implicit request =>
    Logger.debug("Removing tag " + request.body)
    
    request.body.\("tagId").asOpt[String].map { tagId =>
		  Logger.debug("Removing " + tagId + " from "+ id)
		  Dataset.removeTag(id, tagId)
		}
      Ok(toJson(""))    
  }

  def comment(id: String) = SecuredAction(authorization=WithPermission(Permission.CreateComments)) { implicit request =>
    request.user match {
      case Some(identity) => {
	    request.body.\("text").asOpt[String] match {
	      case Some(text) => {
	        val comment = new Comment(identity, text, dataset_id=Some(id))
	        Comment.save(comment)
	        index(id)
	        Ok(comment.id.toString())
	      }
	      case None => {
	        Logger.error("no text specified.")
	        BadRequest
	      }
	    }
      }
      case None => BadRequest
    }
  }

  /**
   * List datasets satisfying a user metadata search tree.
   */
  def searchDatasetsUserMetadata = SecuredAction(authorization=WithPermission(Permission.SearchDatasets)) { request =>
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
  
  /**
   * Return whether a dataset is currently being processed.
   */
  def isBeingProcessed(id: String) = SecuredAction(parse.anyContent, authorization=WithPermission(Permission.ShowDataset)) { request =>
  	Services.datasets.get(id)  match {
  	  case Some(dataset) => {
  	    val files = dataset.files map { f =>
          FileDAO.get(f.id.toString).get
        }
  	    
  	    var isActivity = "false"
        try{
        	for(f <- files){
        		Extraction.findMostRecentByFileId(f.id) match{
        		case Some(mostRecent) => {
        			mostRecent.status match{
        			case "DONE." => 
        			case _ => { 
        				isActivity = "true"
        				throw ActivityFound
        			  }  
        			}
        		}
        		case None =>       
        		}
        	}
        }catch{
          case ActivityFound =>
        }
        
        Ok(toJson(Map("isBeingProcessed"->isActivity))) 
  	  }
  	  case None => {Logger.error("Error getting dataset" + id); InternalServerError}
  	}  	
  }
  
  
  
  def jsonPreviewsFiles(filesList: List[(models.File, Array[(java.lang.String, String, String, String, java.lang.String, String, Long)])]): JsValue = {
    val list = for (filePrevs <- filesList) yield jsonPreviews(filePrevs._1, filePrevs._2)
    toJson(list)
  }  
  def jsonPreviews(prvFile: models.File, prvs: Array[(java.lang.String, String, String, String, java.lang.String, String, Long)]): JsValue = {
    val list = for (prv <- prvs) yield jsonPreview(prv._1, prv._2, prv._3, prv._4, prv._5, prv._6, prv._7)
    val listJson = toJson(list.toList)
    toJson(Map[String, JsValue]("file_id" -> JsString(prvFile.id.toString), "previews" -> listJson))
  }
  def jsonPreview(pvId: java.lang.String, pId: String, pPath: String, pMain: String, pvRoute: java.lang.String, pvContentType: String, pvLength: Long): JsValue = {
    if(pId.equals("X3d"))
    	toJson(Map("pv_id" -> pvId, "p_id" -> pId, "p_path" -> controllers.routes.Assets.at(pPath).toString, "p_main" -> pMain, "pv_route" -> pvRoute, "pv_contenttype" -> pvContentType, "pv_length" -> pvLength.toString,
    			"pv_annotationsEditPath" -> api.routes.Previews.editAnnotation(pvId).toString, "pv_annotationsListPath" -> api.routes.Previews.listAnnotations(pvId).toString, "pv_annotationsAttachPath" -> api.routes.Previews.attachAnnotation(pvId).toString)) 
    else    
    	toJson(Map("pv_id" -> pvId, "p_id" -> pId, "p_path" -> controllers.routes.Assets.at(pPath).toString , "p_main" -> pMain, "pv_route" -> pvRoute, "pv_contenttype" -> pvContentType, "pv_length" -> pvLength.toString))  
  }  
  def getPreviews(id: String) = SecuredAction(parse.anyContent, authorization=WithPermission(Permission.ShowDataset)) { request =>
    Services.datasets.get(id)  match {
      case Some(dataset) => {
        val files = dataset.files map { f =>
          FileDAO.get(f.id.toString).get
        }
        
        val datasetWithFiles = dataset.copy(files = files)
        val previewers = Previewers.findPreviewers
        val previewslist = for(f <- datasetWithFiles.files) yield {
          val pvf = for(p <- previewers ; pv <- f.previews; if (p.contentType.contains(pv.contentType))) yield { 
            (pv.id.toString, p.id, p.path, p.main, api.routes.Previews.download(pv.id.toString).toString, pv.contentType, pv.length)
          }        
          if (pvf.length > 0) {
            (f -> pvf)
          } else {
  	        val ff = for(p <- previewers ; if (p.contentType.contains(f.contentType))) yield {
  	          (f.id.toString, p.id, p.path, p.main, controllers.routes.Files.file(f.id.toString) + "/blob", f.contentType, f.length)
  	        }
  	        (f -> ff)
          }
        }
        Ok(jsonPreviewsFiles(previewslist.asInstanceOf[List[(models.File, Array[(java.lang.String, String, String, String, java.lang.String, String, Long)])]])) 
      }
      case None => {Logger.error("Error getting dataset" + id); InternalServerError}
    }
  }
  
  
  
}
