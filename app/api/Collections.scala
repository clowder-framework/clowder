package api

import models.Collection
import play.api.libs.json.JsValue
import play.api.libs.json.Json.toJson
import javax.inject.{ Singleton, Inject }
import services.DatasetService
import services.CollectionService
import scala.util.{Success, Failure}

/**
 * Manipulate collections.
 * 
 * @author Constantinos Sophocleous
 */
@Singleton
class Collections @Inject() (datasets: DatasetService, collections: CollectionService) extends ApiController {

  def attachDataset(collectionId: String, datasetId: String) = SecuredAction(parse.anyContent,
                    authorization=WithPermission(Permission.CreateCollections)) { request =>

    collections.addDataset(collectionId: String, datasetId: String) match {
      case Success(_) => Ok(toJson(Map("status" -> "success")))
      case Failure(t) => InternalServerError
    }
  }
  
  def removeDataset(collectionId: String, datasetId: String, ignoreNotFound: String) = SecuredAction(parse.anyContent,
                    authorization=WithPermission(Permission.CreateCollections)) { request =>

    collections.removeDataset(collectionId, datasetId, ignoreNotFound) match {
      case Success(_) => Ok(toJson(Map("status" -> "success")))
      case Failure(t) => InternalServerError
    }
  }
  
  def removeCollection(collectionId: String) = SecuredAction(parse.anyContent,
                       authorization=WithPermission(Permission.DeleteCollections)) { request =>
    collections.delete(collectionId)
    Ok(toJson(Map("status" -> "success")))
  }

  def listCollections() = SecuredAction(parse.anyContent,
                                        authorization=WithPermission(Permission.ListCollections)) { request =>
    val list = for (collection <- collections.listCollections()) yield jsonCollection(collection)
    Ok(toJson(list))
  }
  
  def jsonCollection(collection: Collection): JsValue = {
    toJson(Map("id" -> collection.id.toString, "name" -> collection.name, "description" -> collection.description,
               "created" -> collection.created.toString))
  }
}