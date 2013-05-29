/**
 *
 */
package api

import play.api.mvc.Controller
import play.api.mvc.Action
import models.Dataset
import models.File
import services.Services
import play.api.libs.json.JsValue
import play.api.Logger
import models.FileDAO
import play.api.libs.json.Json._
import play.api.libs.json.Json
import com.wordnik.swagger.annotations.Api
import com.wordnik.swagger.annotations.ApiOperation

/**
 * Dataset API.
 * 
 * @author Luigi Marini
 *
 */
@Api(value = "/datasets", listingPath = "/api-docs.{format}/datasets", description = "Maniputate datasets")
object Datasets extends Controller {
  
  /**
   * List all files.
   */
  def list = Authenticated {
    Action {
      val list = for (dataset <- Services.datasets.listDatasets()) yield jsonDataset(dataset)
      Ok(toJson(list))
    }
  }  
  
  def jsonDataset(dataset: Dataset): JsValue = {
    toJson(Map("id"->dataset.id.toString, "datasetname"->dataset.name, "description"->dataset.description,"created"->dataset.created.toString ))
  }
  
  @ApiOperation(value = "Add metadata to dataset", notes = "Returns success of failure", responseClass = "None", httpMethod = "POST")
  def addMetadata(id: String) = Authenticated {
	  Logger.debug("Adding metadata to dataset " + id)
	    Action(parse.json) { request =>
	      	  Logger.debug("Adding metadata to dataset " + id)
	          Dataset.addMetadata(id, Json.stringify(request.body))
	          Ok(toJson(Map("status"->"success")))
	    }
  }

  def addUserMetadata(id: String) =  
	    Action(parse.json) { request => 
	      	  Logger.debug("Adding user metadata to dataset " + id)
	          Dataset.addUserMetadata(id, Json.stringify(request.body))
	          Ok(toJson(Map("status"->"success")))
	    } 
  
	
  def datasetFilesGetIdByDatasetAndFilename(datasetId: String, filename: String): Option[String] = 
		{
			Services.datasets.get(datasetId)  match {
			case Some(dataset) => {
				//        val files = dataset.files map { f =>
				//          FileDAO.get(f.id.toString).get
				//        }		  
			  for(file <- dataset.files){
			    if(file.filename.equals(filename)){
			      return Some(file.id.toString)
			    }
			  }			  
			  Logger.error("File does not exist in dataset" + datasetId); return None			  
			}
			case None => {Logger.error("Error getting dataset" + datasetId); return None}
			}
		}

  
	def datasetFilesList(id: String) = Authenticated {
		Action {
			Services.datasets.get(id)  match {
			case Some(dataset) => {
				//        val files = dataset.files map { f =>
				//          FileDAO.get(f.id.toString).get
				//        }
				val list = for (f <- dataset.files) yield jsonFile(f)
				Ok(toJson(list))       
			}
			case None => {Logger.error("Error getting dataset" + id); InternalServerError}
			}
		}
	}
  
    def jsonFile(file: File): JsValue = {
    toJson(Map("id"->file.id.toString, "filename"->file.filename, "contentType"->file.contentType))
  }
}
