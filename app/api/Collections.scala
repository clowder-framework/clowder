package api

import controllers.SecuredController
import play.api.mvc.Controller
import models.Collection
import play.api.Logger
import services.Services
import org.bson.types.ObjectId
import play.api.libs.json.JsValue
import play.api.libs.json.Json
import play.api.libs.json.Json.toJson
import models.Dataset
import com.mongodb.casbah.commons.MongoDBObject

object Collections extends ApiController {
  
  
  def attachDataset(collectionId: String, datasetId: String) = SecuredAction(parse.anyContent, authorization=WithPermission(Permission.CreateCollections)) { request =>
    Collection.findOneById(new ObjectId(collectionId)) match{
      case Some(collection) => {
        Services.datasets.get(datasetId) match {
          case Some(dataset) => {
            if(!isInCollection(dataset,collection)){
	            // add dataset to collection  
	            // TODO create a service instead of calling salat directly
	            Collection.addDataset(collection.id.toString,dataset)
	            
	            //add collection to dataset
	            Dataset.addCollection(dataset.id.toString, collection.id.toString)
	            
	            Logger.info("Adding dataset to collection completed")
            }
            else{
              Logger.info("Dataset was already in collection.")
            }
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
  
  def removeDataset(collectionId: String, datasetId: String, ignoreNotFound: String) = SecuredAction(parse.anyContent, authorization=WithPermission(Permission.CreateCollections)) { request =>
    Collection.findOneById(new ObjectId(collectionId)) match{
      case Some(collection) => {
        Services.datasets.get(datasetId) match {
          case Some(dataset) => {
            if(isInCollection(dataset,collection)){
	            // remove dataset from collection  
	            // TODO create a service instead of calling salat directly
	            Collection.removeDataset(collection.id.toString, dataset)
	            
	            //remove collection from dataset
	            Dataset.removeCollection(dataset.id.toString, collection.id.toString)
	            
	            Logger.info("Removing dataset from collection completed")
            }
            else{
              Logger.info("Dataset was already out of the collection.")
            }
            Ok(toJson(Map("status" -> "success")))
          }
          case None => {
        	  Ok(toJson(Map("status" -> "success")))
          }
        }
      }
      case None => {
        ignoreNotFound match{
          case "True" =>
            Ok(toJson(Map("status" -> "success")))
          case "False" =>
        	Logger.error("Error getting collection" + collectionId); InternalServerError
        }
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
  
  def removeCollection(collectionId: String) = SecuredAction(parse.anyContent, authorization=WithPermission(Permission.DeleteCollections)) { request =>
    Collection.findOneById(new ObjectId(collectionId)) match{
      case Some(collection) => {       
        for(dataset <- collection.datasets){
          //remove collection from dataset
          Dataset.removeCollection(dataset.id.toString, collection.id.toString)
        }       
        Collection.remove(MongoDBObject("_id" -> collection.id))
        Ok(toJson(Map("status" -> "success")))
      }
      case None => {
        Ok(toJson(Map("status" -> "success")))
      }       
    }    
  }
  
  def listCollections() = SecuredAction(parse.anyContent, authorization=WithPermission(Permission.ListCollections)) { request =>
    val list = for (collection <- Services.collections.listCollections()) yield jsonCollection(collection)
      Ok(toJson(list))    
  }
  
  def jsonCollection(collection: Collection): JsValue = {
    toJson(Map("id" -> collection.id.toString, "name" -> collection.name, "description" -> collection.description, "created" -> collection.created.toString))
  }

  
}