package api

import models.{UUID, Collection}
import play.api.libs.json.JsValue
import play.api.libs.json.Json.toJson
import javax.inject.{ Singleton, Inject }
import services.DatasetService
import services.CollectionService
import scala.util.{Try, Success, Failure}

/**
 * Manipulate collections.
 * 
 * @author Constantinos Sophocleous
 */
@Singleton
class Collections @Inject() (datasets: DatasetService, collections: CollectionService) extends ApiController {

  def attachDataset(collectionId: UUID, datasetId: UUID) = SecuredAction(parse.anyContent,
                    authorization=WithPermission(Permission.CreateCollections)) { request =>

    collections.addDataset(collectionId, datasetId) match {
      case Success(_) => Ok(toJson(Map("status" -> "success")))
      case Failure(t) => InternalServerError
    }
  }
  
  def removeDataset(collectionId: UUID, datasetId: UUID, ignoreNotFound: String) = SecuredAction(parse.anyContent,
                    authorization=WithPermission(Permission.CreateCollections)) { request =>

    collections.removeDataset(collectionId, datasetId, Try(ignoreNotFound.toBoolean).getOrElse(true)) match {
      case Success(_) => Ok(toJson(Map("status" -> "success")))
      case Failure(t) => InternalServerError
    }
  }
  
  def removeCollection(collectionId: UUID) = SecuredAction(parse.anyContent,
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