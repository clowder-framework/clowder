package controllers

import models._
import play.api.data.Form
import play.api.data.Forms._
import util.RequiredFieldsConfig
import java.text.SimpleDateFormat
import java.util.Date
import javax.inject.{Inject, Singleton}

import api.Permission
import org.apache.commons.lang.StringEscapeUtils
import play.api.Logger
import play.api.Play.current
import play.api.libs.json.JsValue
import play.api.libs.json.Json.toJson
import services.{CollectionService, DatasetService, _}
import util.RequiredFieldsConfig
import views.html.defaultpages.badRequest

import scala.collection.mutable.ListBuffer
import services._
import org.apache.commons.lang.StringEscapeUtils

object ThumbnailFound extends Exception {}

@Singleton
class Collections @Inject()(datasets: DatasetService, collections: CollectionService, previewsService: PreviewService, 
                            spaces: SpaceService, users: UserService, events: EventService) extends SecuredController {  

  def newCollection() = PermissionAction(Permission.CreateCollection) { implicit request =>
      implicit val user = request.user
      val spacesList = user.get.spaceandrole.map(_.spaceId).flatMap(spaces.get(_))
      var decodedSpaceList = new ListBuffer[models.ProjectSpace]()
      for (aSpace <- spacesList) {
          decodedSpaceList += Utils.decodeSpaceElements(aSpace)
      }
      Ok(views.html.newCollection(null, decodedSpaceList.toList, RequiredFieldsConfig.isNameRequired, RequiredFieldsConfig.isDescriptionRequired))
  }

  /**
   * Utility method to modify the elements in a collection that are encoded when submitted and stored. These elements
   * are decoded when a view requests the objects, so that they can be human readable.
   * 
   * Currently, the following collection elements are encoded:
   * 
   * name
   * description
   *  
   */
  def decodeCollectionElements(collection: Collection) : Collection = {      
      val decodedCollection = collection.copy(name = StringEscapeUtils.unescapeHtml(collection.name), 
              							  description = StringEscapeUtils.unescapeHtml(collection.description))
              							  
      decodedCollection
  }
  
  /**
   * List collections.
   */	
  def list(when: String, date: String, limit: Int, space: Option[String] = None, mode: String) =
    PrivateServerAction { implicit request =>
      implicit val user = request.user
      var direction = "b"
      if (when != "") direction = when
      val formatter = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS")
      var prev, next = ""
      var collectionList = List.empty[models.Collection]
      if (direction == "b") {
        collectionList = collections.listCollectionsBefore(date, limit, space)
      } else if (direction == "a") {
        collectionList = collections.listCollectionsAfter(date, limit, space)
      } else {
        badRequest
      }

      // latest object
      val latest = collections.latest(space)
      // first object
      val first = collections.first(space)
      var firstPage = false
      var lastPage = false
      if (latest.size == 1) {
        firstPage = collectionList.exists(_.id.equals(latest.get.id))
        lastPage = collectionList.exists(_.id.equals(first.get.id))
        Logger.debug("latest " + latest.get.id + " first page " + firstPage)
        Logger.debug("first " + first.get.id + " last page " + lastPage)
      }
      if (collectionList.size > 0) {
        if (date != "" && !firstPage) {
          // show prev button
          prev = formatter.format(collectionList.head.created)
        }
        if (!lastPage) {
          // show next button
          next = formatter.format(collectionList.last.created)
        }
      }

      var collectionsWithThumbnails = List.empty[models.Collection]
      for (collection <- collectionList) {
        var collectionThumbnail: Option[String] = None
        try {
          for (dataset <- collection.datasets) {
            if (!dataset.thumbnail_id.isEmpty) {
              collectionThumbnail = dataset.thumbnail_id
              throw ThumbnailFound
            }
          }
        } catch {
          case ThumbnailFound =>
        }
        val collectionWithThumbnail = collection.copy(thumbnail_id = collectionThumbnail)
        collectionsWithThumbnails = collectionWithThumbnail +: collectionsWithThumbnails
      }
      collectionsWithThumbnails = collectionsWithThumbnails.reverse

      //Modifications to decode HTML entities that were stored in an encoded fashion as part
      //of the collection's names or descriptions
      val decodedCollections = ListBuffer.empty[models.Collection]
      for (aCollection <- collectionsWithThumbnails) {
        val dCollection = Utils.decodeCollectionElements(aCollection)
        decodedCollections += dCollection
      }

      //Code to read the cookie data. On default calls, without a specific value for the mode, the cookie value is used.
      //Note that this cookie will, in the long run, pertain to all the major high-level views that have the similar
      //modal behavior for viewing data. Currently the options are tile and list views. MMF - 12/14
      val viewMode: Option[String] =
        if (mode == null || mode == "") {
          request.cookies.get("view-mode") match {
              case Some(cookie) => Some(cookie.value)
              case None => None //If there is no cookie, and a mode was not passed in, the view will choose its default
          }
        } else {
            Some(mode)
        }    
    
      //Pass the viewMode into the view
      Ok(views.html.collectionList(decodedCollections.toList, prev, next, limit, viewMode, space))
  }

  def jsonCollection(collection: Collection): JsValue = {
    toJson(Map("id" -> collection.id.toString, "name" -> collection.name, "description" -> collection.description, "created" -> collection.created.toString))
  }
  
  /**
   * Controller flow to create a new collection. Takes two parameters, name, a String, and description, a String. On success,
   * the browser is redirected to the new collection's page. On error, it is redirected back to the dataset creation
   * page with the appropriate error to be displayed.
   *  
   */
  def submit() = PermissionAction(Permission.CreateCollection)(parse.multipartFormData) { implicit request =>
      Logger.debug("------- in Collections.submit ---------")
      var colName = request.body.asFormUrlEncoded.getOrElse("name", null)
      var colDesc = request.body.asFormUrlEncoded.getOrElse("description", null)
      var colSpace = request.body.asFormUrlEncoded.getOrElse("space", null)
        
      implicit val user = request.user
      user match {
        case Some(identity) => {
          if (colName == null || colDesc == null || colSpace == null) {
            val spacesList = spaces.list()
            var decodedSpaceList = new ListBuffer[models.ProjectSpace]()
            for (aSpace <- spacesList) {
              decodedSpaceList += Utils.decodeSpaceElements(aSpace)
            }
            //This case shouldn't happen as it is validated on the client.
            BadRequest(views.html.newCollection("Name, Description, or Space was missing during collection creation.", decodedSpaceList.toList, RequiredFieldsConfig.isNameRequired, RequiredFieldsConfig.isDescriptionRequired))
          }

          var collection : Collection = null
          if (colSpace(0) == "default") {
              collection = Collection(name = colName(0), description = colDesc(0), created = new Date, author = null)
          }
          else {
              collection = Collection(name = colName(0), description = colDesc(0), created = new Date, author = null, space = Some(UUID(colSpace(0))))
          }

          Logger.debug("Saving collection " + collection.name)
          collections.insert(Collection(id = collection.id, name = collection.name, description = collection.description, created = collection.created, author = Some(identity), space = collection.space))

          //index collection
            val dateFormat = new SimpleDateFormat("dd/MM/yyyy")
            current.plugin[ElasticsearchPlugin].foreach{_.index("data", "collection", collection.id,
            List(("name",collection.name), ("description", collection.description), ("created",dateFormat.format(new Date()))))}

          //Add to Events Table
          var option_user = users.findByIdentity(identity)
          events.addObjectEvent(option_user, collection.id, collection.name, "create_collection")

          // redirect to collection page
          current.plugin[AdminsNotifierPlugin].foreach{_.sendAdminsNotification(Utils.baseUrl(request), "Collection","added",collection.id.toString,collection.name)}
          Redirect(routes.Collections.collection(collection.id))
	      }
	      case None => Redirect(routes.Collections.list()).flashing("error" -> "You are not authorized to create new collections.")
      }
  }

  /**
   * Collection.
   */
  def collection(id: UUID) = PermissionAction(Permission.ViewCollection, Some(ResourceRef(ResourceRef.collection, id))) {
    implicit request =>
      Logger.debug(s"Showing collection $id")
      implicit val user = request.user

      collections.get(id) match {
        case Some(collection) => { 
          Logger.debug(s"Found collection $id")
          // only show previewers that have a matching preview object associated with collection
          Logger.debug("Num previewers " + Previewers.findCollectionPreviewers.size)

          //Decode the encoded items
          val dCollection = Utils.decodeCollectionElements(collection)

          for (p <- Previewers.findCollectionPreviewers) Logger.debug("Previewer " + p)
          val filteredPreviewers = for (
            previewer <- Previewers.findCollectionPreviewers;
            preview <- previewsService.findByCollectionId(id);
            if (previewer.collection);
            if (previewer.supportedPreviews.contains(preview.preview_type.get))
          ) yield {
            previewer
          }
          Logger.debug("Num previewers " + filteredPreviewers.size)
          filteredPreviewers.map(p => Logger.debug(s"Filtered previewers for collection $id $p.id"))

          //Decode the datasets so that their free text will display correctly in the view
          val datasetsInside = datasets.listInsideCollection(id)
          val decodedDatasetsInside = ListBuffer.empty[models.Dataset]
          for (aDataset <- datasetsInside) {
            val dDataset = Utils.decodeDatasetElements(aDataset)
            decodedDatasetsInside += dDataset
          }

          val space = collection.space.flatMap(spaces.get(_))
          var decodedSpace: ProjectSpace = null;
          space match {
              case Some(s) => {
                  decodedSpace = Utils.decodeSpaceElements(s)
                  Ok(views.html.collectionofdatasets(decodedDatasetsInside.toList, dCollection, filteredPreviewers.toList, Some(decodedSpace)))
              }
              case None => {
                  Logger.error("Problem in decoding the space element for this dataset: " + dCollection.name)
                  Ok(views.html.collectionofdatasets(decodedDatasetsInside.toList, dCollection, filteredPreviewers.toList, space))
              }
          }

        }
        case None => {
          Logger.error("Error getting collection " + id); BadRequest("Collection not found")
        }
      }
  }

  /**
   * Show all users with access to a collection (identified by its id)
   */
  def users(id: UUID) = PermissionAction(Permission.ViewCollection) { implicit request =>
    implicit val user = request.user
    collections.get(id) match {
      case Some(collection) => {
        collection.space match {
          case Some(spaceId) => {
            spaces.get(spaceId) match {
              case Some(projectSpace) => {

                val userList: List[User] = spaces.getUsersInSpace(spaceId)
                if(!userList.isEmpty) {

                  var userRoleMap = scala.collection.mutable.Map[UUID, String]()
                  for(usr <- userList) {
                    spaces.getRoleForUserInSpace(spaceId, usr.id) match {
                      case Some(role) => userRoleMap += (usr.id -> role.name)
                      case None => Redirect(routes.Collections.collection(id)).flashing("error" -> "Error: Role not found for collection's user.")
                    }
                  }// for user ...
                  Ok(views.html.collections.users(collection, projectSpace.name, userRoleMap, userList.sortBy(_.fullName.toLowerCase)))

                } else { Redirect(routes.Collections.collection(id)).flashing("error" -> "Error: Collection's users not found.") }
              }
              case None => Redirect(routes.Collections.collection(id)).flashing("error" -> "Error: Collection's space not found.")
            }
          }
          case None => Redirect(routes.Collections.collection(id)).flashing("error" -> "Error: Collection's space not found.")
        }
      }
      case None => Redirect(routes.Collections.collection(id)).flashing("error" -> "Error: Collection not found.")
    }
  }

  def previews(collection_id: UUID) = PermissionAction(Permission.EditCollection) { implicit request =>
      collections.get(collection_id) match {
        case Some(collection) => {
          val previewsByCol = previewsService.findByCollectionId(collection_id)
          Ok(views.html.collectionPreviews(collection_id.toString, previewsByCol, Previewers.findCollectionPreviewers))
        }
        case None => {
          Logger.error("Error getting collection " + collection_id);
          BadRequest("Collection not found")
        }
      }
  }
}

