package api

import play.api.Logger
import play.api.Play.current
import models.{UUID, Collection}
import services._
import play.api.libs.json.{JsObject, JsValue}
import play.api.libs.json.Json.toJson
import javax.inject.{ Singleton, Inject }
import scala.util.{Try, Success, Failure}
import com.wordnik.swagger.annotations.Api
import com.wordnik.swagger.annotations.ApiOperation
import java.util.Date
import controllers.Utils

/**
 * Manipulate collections.
 * 
 * @author Constantinos Sophocleous
 */
@Api(value = "/collections", listingPath = "/api-docs.json/collections", description = "Collections are groupings of datasets")
@Singleton
class Collections @Inject() (datasets: DatasetService, collections: CollectionService, previews: PreviewService, userService: UserService) extends ApiController {

    
  @ApiOperation(value = "Create a collection",
      notes = "",
      responseClass = "None", httpMethod = "POST")
  def createCollection() = SecuredAction(authorization=WithPermission(Permission.CreateCollections)) {
    request =>
      Logger.debug("Creating new collection")
      (request.body \ "name").asOpt[String].map {
        name =>
          (request.body \ "description").asOpt[String].map {
            description =>
              val c = Collection(name = name, description = description, created = new Date())
              collections.insert(c) match {
                case Some(id) => {
                 Ok(toJson(Map("id" -> id)))
                }
                case None => Ok(toJson(Map("status" -> "error")))
              }
          }.getOrElse(BadRequest(toJson("Missing parameter [description]")))
      }.getOrElse(BadRequest(toJson("Missing parameter [name]")))
  }

  @ApiOperation(value = "Add dataset to collection",
      notes = "",
      responseClass = "None", httpMethod = "POST")
  def attachDataset(collectionId: UUID, datasetId: UUID) = SecuredAction(parse.anyContent,
                    authorization=WithPermission(Permission.CreateCollections), resourceId = Some(collectionId)) { request =>

    collections.addDataset(collectionId, datasetId) match {
      case Success(_) => Ok(toJson(Map("status" -> "success")))
      case Failure(t) => InternalServerError
    }
  }

  /**
   * Reindex the given collection, if recursive is set to true it will
   * also reindex all datasets and files.
   */
  @ApiOperation(value = "Reindex a collection",
    notes = "Reindex the existing collection, if recursive is set to true it will also reindex all datasets and files.",
    httpMethod = "GET")
  def reindex(id: UUID, recursive: Boolean) =
    SecuredAction(parse.anyContent, authorization = WithPermission(Permission.CreateCollections)) {
    request =>
      collections.get(id) match {
        case Some(coll) => {
          current.plugin[ElasticsearchPlugin].foreach {
            _.index(coll, recursive)
          }
          Ok(toJson(Map("status" -> "success")))
        }
        case None => {
          Logger.error("Error getting collection" + id)
          BadRequest(toJson(s"The given collection id $id is not a valid ObjectId."))
        }
      }
  }

  @ApiOperation(value = "Remove dataset from collection",
      notes = "",
      responseClass = "None", httpMethod = "POST")
  def removeDataset(collectionId: UUID, datasetId: UUID, ignoreNotFound: String) = SecuredAction(parse.anyContent,
                    authorization=WithPermission(Permission.CreateCollections), resourceId = Some(collectionId)) { request =>

    collections.removeDataset(collectionId, datasetId, Try(ignoreNotFound.toBoolean).getOrElse(true)) match {
      case Success(_) => Ok(toJson(Map("status" -> "success")))
      case Failure(t) => InternalServerError
    }
  }
  
  @ApiOperation(value = "Remove collection",
      notes = "Does not delete the individual datasets in the collection.",
      responseClass = "None", httpMethod = "POST")
  def removeCollection(collectionId: UUID) = SecuredAction(parse.anyContent,
    authorization=WithPermission(Permission.DeleteCollections), resourceId = Some(collectionId)) { request =>
    collections.get(collectionId) match {
      case Some(collection) => {
        collections.delete(collectionId)
        current.plugin[AdminsNotifierPlugin].foreach {
          _.sendAdminsNotification(Utils.baseUrl(request),"Collection","removed",collection.id.stringify, collection.name)
        }
      }
    }
    //Success anyway, as if collection is not found it is most probably deleted already
    Ok(toJson(Map("status" -> "success")))
  }

  @ApiOperation(value = "List all collections",
      notes = "",
      responseClass = "None", httpMethod = "GET")
  def listCollections() = SecuredAction(parse.anyContent,
                                        authorization=WithPermission(Permission.ListCollections)) { request =>
    val list = for (collection <- collections.listCollections()) yield jsonCollection(collection)
    Ok(toJson(list))
  }

  @ApiOperation(value = "Get a specific collection",
    responseClass = "Collection", httpMethod = "GET")
  def getCollection(collectionId: UUID) = SecuredAction(parse.anyContent,
    authorization=WithPermission(Permission.ShowCollection)) { request =>
    collections.get(collectionId) match {
      case Some(x) => Ok(jsonCollection(x))
      case None => BadRequest(toJson("collection not found"))
    }
  }

  def jsonCollection(collection: Collection): JsValue = {
    toJson(Map("id" -> collection.id.toString, "name" -> collection.name, "description" -> collection.description,
               "created" -> collection.created.toString))
  }

  /**
   * Add preview to file.
   */
  @ApiOperation(value = "Attach existing preview to collection",
    notes = "",
    responseClass = "None", httpMethod = "POST")
  def attachPreview(collection_id: UUID, preview_id: UUID) = SecuredAction(authorization = WithPermission(Permission.EditCollection)) {
    request =>
      // Use the "extractor_id" field contained in the POST data.  Use "Other" if absent.
      val eid = (request.body \ "extractor_id").asOpt[String]
      val extractor_id = if (eid.isDefined) {
        eid
      } else {
        Logger.debug("api.Files.attachPreview(): No \"extractor_id\" specified in request, set it to None.  request.body: " + request.body.toString)
        Some("Other")
      }
      val preview_type = (request.body \ "preview_type").asOpt[String].getOrElse("")
      request.body match {
        case JsObject(fields) => {
          collections.get(collection_id) match {
            case Some(collection) => {
              previews.get(preview_id) match {
                case Some(preview) =>
                  // "extractor_id" is stored at the top level of "Preview".  Remove it from the "metadata" field to avoid dup.
                  // TODO replace null with None
                  previews.attachToCollection(preview_id, collection_id, preview_type, extractor_id, request.body)
                  Ok(toJson(Map("status" -> "success")))
                case None => BadRequest(toJson("Preview not found"))
              }
            }
            //If file to be previewed is not found, just delete the preview
            case None => {
              previews.get(preview_id) match {
                case Some(preview) =>
                  Logger.debug("Collection not found. Deleting previews.files " + preview_id)
                  previews.removePreview(preview)
                  BadRequest(toJson("Collection not found. Preview deleted."))
                case None => BadRequest(toJson("Preview not found"))
              }
            }
          }
        }
        case _ => Ok("received something else: " + request.body + '\n')
      }
  }

  @ApiOperation(value = "Follow collection.",
    notes = "Add user to collection followers and add collection to user followed collections.",
    responseClass = "None", httpMethod = "POST")
  def follow(id: UUID) = SecuredAction(parse.anyContent, authorization = WithPermission(Permission.LoggedIn)) {
    request =>
      val user = request.mediciUser

      user match {
        case Some(loggedInUser) => {
          collections.get(id) match {
            case Some(collection) => {
              collections.addFollower(id, loggedInUser.id)
              userService.followCollection(loggedInUser.id, id)
              Ok
            }
            case None => {
              NotFound
            }
          }
        }
        case None => {
          Unauthorized
        }
      }
  }

  @ApiOperation(value = "Unfollow collection.",
    notes = "Remove user from collection followers and remove collection from user followed collections.",
    responseClass = "None", httpMethod = "POST")
  def unfollow(id: UUID) = SecuredAction(parse.anyContent, authorization = WithPermission(Permission.LoggedIn)) {
    request =>
      val user = request.mediciUser

      user match {
        case Some(loggedInUser) => {
          collections.get(id) match {
            case Some(collection) => {
              collections.removeFollower(id, loggedInUser.id)
              userService.unfollowCollection(loggedInUser.id, id)
              Ok
            }
            case None => {
              NotFound
            }
          }
        }
        case None => {
          Unauthorized
        }
      }
  }

}

