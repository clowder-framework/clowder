package controllers

import models._
import play.api.data.Form
import play.api.data.Forms._
import play.api.mvc.SimpleResult
import util.{Formatters, RequiredFieldsConfig}
import java.text.SimpleDateFormat
import java.util.Date
import javax.inject.{Inject, Singleton}

import api.{UserRequest, Permission}
import org.apache.commons.lang.StringEscapeUtils
import play.api.Logger
import play.api.Play.current
import play.api.libs.json.JsValue
import play.api.libs.json.Json.toJson
import services.{CollectionService, DatasetService, _}
import views.html.defaultpages.badRequest

import scala.collection.mutable.ListBuffer
import services._
import org.apache.commons.lang.StringEscapeUtils

@Singleton
class Collections @Inject()(datasets: DatasetService, collections: CollectionService, previewsService: PreviewService, 
                            spaceService: SpaceService, users: UserService, events: EventService) extends SecuredController {

  def newCollection() = PermissionAction(Permission.CreateCollection) { implicit request =>
    implicit val user = request.user
    val spacesList = user.get.spaceandrole.map(_.spaceId).flatMap(spaceService.get(_))
    var decodedSpaceList = new ListBuffer[models.ProjectSpace]()
    for (aSpace <- spacesList) {
      //For each space in the list, check if the user has permission to add something to it, if so
      //decode it and add it to the list to pass back to the view.
      if (Permission.checkPermission(Permission.AddResourceToSpace, ResourceRef(ResourceRef.space, aSpace.id))) {
        decodedSpaceList += Utils.decodeSpaceElements(aSpace)
      }
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
  def list(when: String, date: String, limit: Int, space: Option[String], mode: String, owner: Option[String]) = PrivateServerAction { implicit request =>
    implicit val user = request.user

    val nextPage = (when == "a")
    val person = owner.flatMap(o => users.get(UUID(o)))

    val collectionList = person match {
      case Some(p) => {
        if (date != "") {
          collections.listUser(date, nextPage, limit, request.user, request.superAdmin, p)
        } else {
          collections.listUser(limit, request.user, request.superAdmin, p)
        }
      }
      case None => {
        space match {
          case Some(s) => {
            if (date != "") {
              collections.listSpace(date, nextPage, limit, s)
            } else {
              collections.listSpace(limit, s)
            }
          }
          case None => {
            if (date != "") {
              collections.listAccess(date, nextPage, limit, request.user, request.superAdmin)
            } else {
              collections.listAccess(limit, request.user, request.superAdmin)
            }

          }
        }
      }
    }

    // check to see if there is a prev page
    val prev = if (collectionList.nonEmpty && date != "") {
      val first = Formatters.iso8601(collectionList.head.created)
      val c = person match {
        case Some(p) => collections.listUser(first, nextPage=false, 1, request.user, request.superAdmin, p)
        case None => {
          space match {
            case Some(s) => collections.listSpace(first, nextPage = false, 1, s)
            case None => collections.listAccess(first, nextPage = false, 1, request.user, request.superAdmin)
          }
        }
      }
      if (c.nonEmpty && c.head.id != collectionList.head.id) {
        first
      } else {
        ""
      }
    } else {
      ""
    }

    // check to see if there is a next page
    val next = if (collectionList.nonEmpty) {
      val last = Formatters.iso8601(collectionList.last.created)
      val ds = person match {
        case Some(p) => collections.listUser(last, nextPage=true, 1, request.user, request.superAdmin, p)
        case None => {
          space match {
            case Some(s) => collections.listSpace(last, nextPage = true, 1, s)
            case None => collections.listAccess(last, nextPage = true, 1, request.user, request.superAdmin)
          }
        }
      }
      if (ds.nonEmpty && ds.head.id != collectionList.last.id) {
        last
      } else {
        ""
      }
    } else {
      ""
    }


    var collectionsWithThumbnails = List.empty[models.Collection]
    for (collection <- collectionList) {
      val collectionThumbnail = collection.datasets.find(_.thumbnail_id.isDefined).flatMap(_.thumbnail_id)
      val collectionWithThumbnail = collection.copy(thumbnail_id = collectionThumbnail)
      collectionsWithThumbnails = collectionWithThumbnail +: collectionsWithThumbnails
    }
    collectionsWithThumbnails = collectionsWithThumbnails.reverse

    //Modifications to decode HTML entities that were stored in an encoded fashion as part
    //of the collection's names or descriptions
    val decodedCollections = ListBuffer.empty[models.Collection]
    for (aCollection <- collectionsWithThumbnails) {
      decodedCollections += Utils.decodeCollectionElements(aCollection)
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
      var colSpace = request.body.asFormUrlEncoded.getOrElse("space", List.empty)

      implicit val user = request.user
      user match {
        case Some(identity) => {
          if (colName == null || colDesc == null || colSpace == null) {
            val spacesList = spaceService.list()
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
            val stringSpaces = colSpace(0).split(",").toList
            val colSpaces: List[UUID] = stringSpaces.map(aSpace => if(aSpace != "") UUID(aSpace) else None).filter(_ != None).asInstanceOf[List[UUID]]
            collection = Collection(name = colName(0), description = colDesc(0), created = new Date, author = null, spaces = colSpaces)
          }

          Logger.debug("Saving collection " + collection.name)
          collections.insert(Collection(id = collection.id, name = collection.name, description = collection.description, created = collection.created, author = Some(identity), spaces = collection.spaces))

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

          var collectionSpaces: List[ProjectSpace] = List.empty[ProjectSpace]
          collection.spaces.map{
            sp=> spaceService.get(sp) match {
              case Some(s) => {
                collectionSpaces = s :: collectionSpaces
              }
              case None => Logger.error(s"space with id $sp on collection $id doesn't exist.")
            }
          }
          val decodedSpaces: List[ProjectSpace] = collectionSpaces.map{aSpace => Utils.decodeSpaceElements(aSpace)}

          var otherSpaces: List[ProjectSpace] = List.empty[ProjectSpace]
          user match {
            case Some (u) => {
              otherSpaces = user.get.spaceandrole.map(_.spaceId).flatMap(spaceService.get(_)).map(aSpace => if(!collectionSpaces.map(_.id).contains(aSpace.id)) aSpace else None).filter(_ != None).asInstanceOf[List[ProjectSpace]]

              Ok(views.html.collectionofdatasets(decodedDatasetsInside.toList, dCollection, filteredPreviewers.toList, Some(decodedSpaces), otherSpaces))
            }
            case None => {
              Redirect(routes.RedirectUtility.authenticationRequired())
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
        var userList: List[User] = List.empty
        var userListSpaceRoleTupleMap = Map[UUID, List[Tuple2[String,String]]]() // Map( User-id -> List((Space-name,Role-name)) )

        // Setup userList, add all users of all spaces associated with the collection
        collection.spaces.foreach { spaceId =>
          spaceService.get(spaceId) match {
            case Some(spc) => userList = spaceService.getUsersInSpace(spaceId) ::: userList
            case None => Redirect (routes.Collections.collection(id)).flashing ("error" -> s"Error: No spaces found for collection $id.");
          }
        }
        userList = userList.distinct.sortBy(_.fullName.toLowerCase)

        // Setup userListSpaceRoleTupleMap
        userList.foreach( usr => userListSpaceRoleTupleMap = userListSpaceRoleTupleMap + (usr.id -> List()) ) // initialize, based upon userList's values
        collection.spaces.foreach { spaceId =>
          spaceService.get(spaceId) match {
            case Some(spc) => {
              val usersInCurrSpace: List[User] = spaceService.getUsersInSpace(spaceId)
              if (usersInCurrSpace.nonEmpty) {

                usersInCurrSpace.foreach { usr =>
                  spaceService.getRoleForUserInSpace(spaceId, usr.id) match {
                    case Some(role) => userListSpaceRoleTupleMap += ( usr.id -> ((spc.name,role.name) :: userListSpaceRoleTupleMap(usr.id)) )
                    case None => Redirect(routes.Collections.collection(id)).flashing("error" -> s"Error: Role not found for collection $id user $usr.")
                  }
                }

              }
            }
            case None => Redirect (routes.Collections.collection(id)).flashing ("error" -> s"Error: No spaces found for collection $id.");
          }
        }
        // Clean-up, and sort space-names per user
        userListSpaceRoleTupleMap = userListSpaceRoleTupleMap filter (_._2.nonEmpty) // remove empty-list Values from Map (and corresponding Key)
        for(k <- userListSpaceRoleTupleMap.keys) userListSpaceRoleTupleMap += ( k -> userListSpaceRoleTupleMap(k).distinct.sortBy(_._1.toLowerCase) )

        if(userList.nonEmpty) {
          val currUserIsAuthor = user.get.identityId.userId.equals(collection.author.get.identityId.userId)
          Ok(views.html.collections.users(collection, userListSpaceRoleTupleMap, currUserIsAuthor, userList))
        }
        else Redirect(routes.Collections.collection(id)).flashing("error" -> s"Error: No users found for collection $id.")
      }
      case None => Redirect(routes.Collections.collection(id)).flashing("error" -> s"Error: Collection $id not found.")
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

