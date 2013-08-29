package api

import controllers.SecuredController
import play.api.mvc.Controller
import controllers.Permission
import models.Collection
import play.api.Logger
import services.Services
import org.bson.types.ObjectId
import play.api.libs.json.JsValue
import play.api.libs.json.Json
import play.api.libs.json.Json.toJson

object Collections extends Controller with SecuredController with ApiController {

  def attachDataset(collectionId: String, datasetId: String) = SecuredAction(parse.anyContent, allowKey=true, authorization=WithPermission(Permission.CreateCollections)) { request =>
    Collection.findOneById(new ObjectId(collectionId)) match{
      case Some(collection) => {
        Services.datasets.get(datasetId) match {
          case Some(dataset) => {
            
            // add dataset to collection
            val cl = collection.copy(datasets = collection.datasets ++ List(dataset))
            // TODO create a service instead of calling salat directly
            Collection.save(cl)
            Logger.info("Adding dataset to collection completed")
            Ok(toJson(Map("status" -> "success")))
          }
          case None => {
        	  Logger.error("Error getting dataset" + datasetId); InternalServerError
          }
        }  
      }
      case None => {
        Logger.error("Error getting collection" + collectionId); InternalServerError
      }      
    }    
  }
}