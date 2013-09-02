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
import models.Dataset

object Collections extends Controller with SecuredController with ApiController {

  def attachDataset(collectionId: String, datasetId: String) = SecuredAction(parse.anyContent, allowKey=true, authorization=WithPermission(Permission.CreateCollections)) { request =>
    Collection.findOneById(new ObjectId(collectionId)) match{
      case Some(collection) => {
        Services.datasets.get(datasetId) match {
          case Some(dataset) => {
            if(!isInCollection(dataset,collection)){
	            // add dataset to collection  
	            val cl = collection.copy(datasets = collection.datasets ++ List(dataset))
	            // TODO create a service instead of calling salat directly
	            Collection.save(cl)
	            
	            //add collection to dataset
	            val ds = dataset.copy(collections = dataset.collections ++ List(collection.id.toString()))
	            Dataset.save(ds)
	            
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
  
  def removeDataset(collectionId: String, datasetId: String, ignoreNotFound: String) = SecuredAction(parse.anyContent, allowKey=true, authorization=WithPermission(Permission.CreateCollections)) { request =>
    Collection.findOneById(new ObjectId(collectionId)) match{
      case Some(collection) => {
        Services.datasets.get(datasetId) match {
          case Some(dataset) => {
            if(isInCollection(dataset,collection)){
	            // remove dataset from collection  
	            val cl = collection.copy(datasets = removeItemDataset(collection.datasets,dataset))
	            // TODO create a service instead of calling salat directly
	            Collection.save(cl)
	            
	            //remove collection from dataset
	            val ds = dataset.copy(collections = removeItemCollection(dataset.collections,collection))
	            Dataset.save(ds)
	            
	            Logger.info("Removing dataset from collection completed")
            }
            else{
              Logger.info("Dataset was already out of the collection.")
            }
            Ok(toJson(Map("status" -> "success")))
          }
          case None => {
        	  Logger.error("Error getting dataset" + datasetId); InternalServerError
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
  
  def removeItemDataset(datasets: List[Dataset], dataset: Dataset): List[Dataset] = {
    val newList = for(dst <- datasets; if(!dst.id.toString().equals(dataset.id.toString()))) yield {
      dst
    }    
    return newList
  }
  def removeItemCollection(collections: List[String], collection: Collection): List[String] = {
    val newList = for(cl <- collections; if(!cl.equals(collection.id.toString()))) yield {
      cl
    }    
    return newList
  }
  
  
  def isInCollection(dataset: Dataset, collection: Collection): Boolean = {
    for(collDataset <- collection.datasets){
      if(collDataset.id == dataset.id)
        return true
    }
    return false
  }
  
  def removeCollection(collectionId: String) = SecuredAction(parse.anyContent, allowKey=true, authorization=WithPermission(Permission.DeleteCollections)) { request =>
    Collection.findOneById(new ObjectId(collectionId)) match{
      case Some(collection) => {       
        for(dataset <- collection.datasets){
          //remove collection from dataset
	      val ds = dataset.copy(collections = removeItemCollection(dataset.collections,collection))
	      Dataset.save(ds)
        }       
        Collection.remove(collection)
        Ok(toJson(Map("status" -> "success")))
      }
      case None => {
        Ok(toJson(Map("status" -> "success")))
      }       
    }    
  }

  
}