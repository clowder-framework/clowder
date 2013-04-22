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

object Datasets extends Controller {
  
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