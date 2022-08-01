package controllers

import api.Permission._
import models._
import org.apache.commons.lang.StringEscapeUtils._
import util.{ Formatters, RequiredFieldsConfig, SortingUtils, SearchUtils }
import java.text.SimpleDateFormat
import java.util.Date
import javax.inject.{ Inject, Singleton }
import api.Permission
import play.api.Logger
import play.api.Play.current
import play.api.libs.json.JsValue
import play.api.libs.json.Json.toJson
import services.{ CollectionService, DatasetService, _ }
import scala.collection.immutable.List
import scala.collection.mutable.ListBuffer
import services._
import org.apache.commons.lang.StringEscapeUtils
import play.api.i18n.Messages

@Singleton
class Collections @Inject() (datasets: DatasetService, collections: CollectionService, previewsService: PreviewService,
                            spaceService: SpaceService, users: UserService, events: EventService,
                            appConfig: AppConfigurationService, selections: SelectionService, sinkService: EventSinkService) extends SecuredController {

  /**
   * String name of the Space such as 'Project space' etc. parsed from conf/messages
   */
  val spaceTitle: String = Messages("space.title")

  def newCollection(space: Option[String]) = PermissionAction(Permission.CreateCollection) { implicit request =>
    implicit val user = request.user
    val spacesList = user.get.spaceandrole.map(_.spaceId).flatMap(spaceService.get(_))
    val permittedSpaces = Permission.checkPermissions(Permission.AddResourceToSpace, spacesList.map(s => ResourceRef(ResourceRef.space, s.id))).approved
    var decodedSpaceList = new ListBuffer[models.ProjectSpace]()
    permittedSpaces.map(ps => spacesList.filter(_.id == ps.id).foreach(sp =>
      decodedSpaceList += Utils.decodeSpaceElements(sp)
    ))

    space match {
      case Some(spaceId) => {
        spaceService.get(UUID(spaceId)) match {
          case Some(s) => Ok(views.html.newCollection(null, decodedSpaceList.toList, RequiredFieldsConfig.isNameRequired, RequiredFieldsConfig.isDescriptionRequired, Some(spaceId), Some(s.name)))
          case None => Ok(views.html.newCollection(null, decodedSpaceList.toList, RequiredFieldsConfig.isNameRequired, RequiredFieldsConfig.isDescriptionRequired, None, None))
        }
      }
      case None => Ok(views.html.newCollection(null, decodedSpaceList.toList, RequiredFieldsConfig.isNameRequired, RequiredFieldsConfig.isDescriptionRequired, None, None))
    }

  }

  def newCollectionWithParent(parentCollectionId: UUID) = PermissionAction(Permission.AddResourceToCollection, Some(ResourceRef(ResourceRef.collection, parentCollectionId))) { implicit request =>
    implicit val user = request.user
    collections.get(parentCollectionId) match {
      case Some(parentCollection) => {

        Ok(views.html.newCollectionWithParent(null, RequiredFieldsConfig.isNameRequired, RequiredFieldsConfig.isDescriptionRequired, None, Some(parentCollectionId.toString()), Some(parentCollection.name)))
      }
      case None => Ok(toJson("newCollectionWithParent, no collection matches parentCollectionId"))
    }

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
  def decodeCollectionElements(collection: Collection): Collection = {
    val decodedCollection = collection.copy(name = StringEscapeUtils.unescapeHtml(collection.name),
      description = StringEscapeUtils.unescapeHtml(collection.description))

    decodedCollection
  }

  def followingCollections(index: Int, limit: Int, mode: String) = PrivateServerAction { implicit request =>
    implicit val user = request.user
    user match {
      case Some(clowderUser) => {

        val title: Option[String] = Some(Messages("following.title", Messages("collections.title")))
        val collectionList = new ListBuffer[Collection]()
        val collectionIds = clowderUser.followedEntities.filter(_.objectType == "collection")
        val collectionIdsToUse = collectionIds.slice(index * limit, (index + 1) * limit)
        val prev = index - 1
        val next = if (collectionIds.length > (index + 1) * limit) {
          index + 1
        } else {
          -1
        }
        collections.get(collectionIdsToUse.map(_.id)).found.foreach(collectionList += _)

        val collectionsWithThumbnails = collectionList.map {c =>
          if (c.thumbnail_id.isDefined) {
            c
          } else {
            val collectionThumbnail = datasets.listCollection(c.id.stringify).find(_.thumbnail_id.isDefined).flatMap(_.thumbnail_id)
            c.copy(thumbnail_id = collectionThumbnail)
          }
        }

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
        Ok(views.html.users.followingCollections(decodedCollections.toList, prev, next, limit, viewMode, None, title, None))
      }
      case None => InternalServerError("No user defined")
    }

  }

  def sortedListInSpace(space: String, offset: Integer, limit: Integer, showPublic: Boolean) = UserAction(needActive = false) { implicit request =>
    {
      implicit val user = request.user
      val sortOrder: String =
        request.cookies.get("sort-order") match {
          case Some(cookie) => cookie.value
          case None => "dateN" //a default
        }
      val datasetSpace = spaceService.get(UUID(space))
    val spaceName = datasetSpace match {
      case Some(s) => Some(s.name)
      case None => None
    }
      val mode = ""
      val spaceResource: Option[ProjectSpace] = spaceService.get(UUID(space))

      if (!spaceResource.isDefined) {
        Logger.error(s"space with id $space doesn't exist.")
        BadRequest(views.html.notFound("Space " + space + " not found."))
      } else {
        var title: Option[String] = Some(Messages("resource.in.title", Messages("collections.title"), spaceTitle, routes.Spaces.getSpace(spaceResource.get.id), spaceResource.get.name))

        if (!Permission.checkPermission(Permission.ViewSpace, ResourceRef(ResourceRef.space, UUID(space)))) {
          BadRequest(views.html.notAuthorized("You are not authorized to access the " + spaceTitle + ".", spaceResource.get.name, "space"))
        } else {


        val cList = collections.listSpaceAccess(0, space, Set[Permission](Permission.ViewCollection), user, false, showPublic)


          val len = cList.length

          val collectionList = SortingUtils.sortCollections(cList, sortOrder).drop(offset).take(limit)

          val collectionsWithThumbnails = collectionList.map { c =>
            {
              if (c.thumbnail_id.isDefined) {
                c
              } else {
                val collectionThumbnail = datasets.listCollection(c.id.stringify).find(_.thumbnail_id.isDefined).flatMap(_.thumbnail_id)
                c.copy(thumbnail_id = collectionThumbnail)
              }
            }
          }

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
          val prev: String = if (offset != 0) {
            offset.toString()
          } else {
            ""
          }
          val next: String = if (len > (offset + limit)) {
            (offset + limit).toString()
          } else {
            ""
          }
          val date = ""
          Ok(views.html.collectionList(decodedCollections.toList, prev, next, limit, viewMode, Some(space), spaceName, title, None, None, "a", date))
        }
      }
    }
  }

  /**
   * List collections.
   */
  def list(when: String, date: String, limit: Int, space: Option[String], mode: String, owner: Option[String], showPublic: Boolean, showOnlyShared : Boolean, showTrash : Boolean) = UserAction(needActive=false) { implicit request =>
    implicit val user = request.user
    val nextPage = (when == "a")
    val person = owner.flatMap(o => users.get(UUID(o)))
    val ownerName = person match {
      case Some(p) => Some(p.fullName)
      case None => None
    }
    val collectionSpace = space.flatMap(o => spaceService.get(UUID(o)))
    val spaceName = collectionSpace match {
      case Some(s) => Some(s.name)
      case None => None
    }
    var title: Option[String] = Some(play.api.i18n.Messages("list.title", play.api.i18n.Messages("collections.title")))

    val collectionList = person match {
      case Some(p) => {
        space match {
          case Some(s) if (collectionSpace.isDefined) => {
            title = Some(Messages("owner.in.resource.title", p.fullName, Messages("collections.title"), spaceTitle, routes.Spaces.getSpace(collectionSpace.get.id), collectionSpace.get.name))
          }
          case _ => {
            if (showTrash){
              title = Some(Messages("owner.title", p.fullName, Messages("collections.trashtitle")))
            } else {
              title = Some(Messages("owner.title", p.fullName, Messages("collections.title")))
            }
          }
        }
        if (date != "") {
          if (showTrash){
            collections.listUserTrash(date, nextPage, limit, request.user, request.user.fold(false)(_.superAdminMode), p)
          } else {
            collections.listUser(date, nextPage, limit, request.user, request.user.fold(false)(_.superAdminMode), p)
          }
        } else {
          if (showTrash){
            collections.listUserTrash(limit, request.user, request.user.fold(false)(_.superAdminMode), p)
          } else {
            collections.listUser(limit, request.user, request.user.fold(false)(_.superAdminMode), p)
          }
        }
      }
      case None => {
        space match {
          case Some(s)  if(collectionSpace.isDefined) => {
            title = Some(Messages("resource.in.title", Messages("collections.title"), spaceTitle, routes.Spaces.getSpace(collectionSpace.get.id), collectionSpace.get.name))
            if (date != "") {
              collections.listSpace(date, nextPage, limit, s)
            } else {
              collections.listSpace(limit, s)
            }
          }
          case _ => {
            if (date != "") {
              collections.listAccess(date, nextPage, limit, Set[Permission](Permission.ViewCollection), request.user, request.user.fold(false)(_.superAdminMode), showPublic, showOnlyShared)
            } else {
              collections.listAccess(limit, Set[Permission](Permission.ViewCollection), request.user, request.user.fold(false)(_.superAdminMode), showPublic, showOnlyShared)
            }

          }
        }
      }
    }

    // check to see if there is a prev page
    val prev = if (collectionList.nonEmpty && date != "") {
      val first = Formatters.iso8601(collectionList.head.created)
      val c = person match {
        case Some(p) =>{
          if (showTrash){
            collections.listUserTrash(first, nextPage=false, 1, request.user, request.user.fold(false)(_.superAdminMode), p)
          } else {
            collections.listUser(first, nextPage=false, 1, request.user, request.user.fold(false)(_.superAdminMode), p)
          }
        }
        case None => {
          space match {
            case Some(s) => collections.listSpace(first, nextPage = false, 1, s)
            case None => collections.listAccess(first, nextPage = false, 1, Set[Permission](Permission.ViewCollection), request.user, request.user.fold(false)(_.superAdminMode), showPublic, showOnlyShared)
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
        case Some(p) => {
          if (showTrash){
            collections.listUserTrash(last, nextPage=true, 1, request.user, request.user.fold(false)(_.superAdminMode), p)
          } else {
            collections.listUser(last, nextPage=true, 1, request.user, request.user.fold(false)(_.superAdminMode), p)
          }
        }
        case None => {
          space match {
            case Some(s) => collections.listSpace(last, nextPage = true, 1, s)
            case None => collections.listAccess(last, nextPage = true, 1, Set[Permission](Permission.ViewCollection), request.user, request.user.fold(false)(_.superAdminMode), showPublic, showOnlyShared)
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

    val collectionsWithThumbnails = collectionList.map {c =>
      if (c.thumbnail_id.isDefined) {
        c
      } else {
        val collectionThumbnail = datasets.listCollection(c.id.stringify).find(_.thumbnail_id.isDefined).flatMap(_.thumbnail_id)
        c.copy(thumbnail_id = collectionThumbnail)
      }
    }

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
    if(!showPublic) {
      title = Some(Messages("you.title", Messages("collections.title")))
    }
    //Pass the viewMode into the view
    space match {
        //space id is not correct
      case Some(s) if collectionSpace.isEmpty =>{
        NotFound(views.html.notFound(Messages("space.title")  + " not found."))
      }
        // view the list of collection in a space that you should not access
      case Some(s) if !Permission.checkPermission(Permission.ViewSpace, ResourceRef(ResourceRef.space, UUID(s))) => {
        BadRequest(views.html.notAuthorized("You are not authorized to access the " + spaceTitle+ ".", s, "space"))
      }
      case _ =>  Ok(views.html.collectionList(decodedCollections.toList, prev, next, limit, viewMode, space, spaceName, title, owner, ownerName, when, date, showTrash))
    }
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
      val colName = request.body.asFormUrlEncoded.getOrElse("name", null)
      val colDesc = request.body.asFormUrlEncoded.getOrElse("description", null)
      val colSpace = request.body.asFormUrlEncoded.getOrElse("space", List.empty)
      val colParentColId = request.body.asFormUrlEncoded.getOrElse("parentcolid",null)

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
            BadRequest(views.html.newCollection("Name, Description, or " + spaceTitle + " was missing during collection creation.", decodedSpaceList.toList, RequiredFieldsConfig.isNameRequired, RequiredFieldsConfig.isDescriptionRequired, None, None))
          }

          var parentCollectionIds = List.empty[String]
          try {
            parentCollectionIds = colParentColId(0).split(",").toList
          }  catch {
            case e : Exception => Logger.debug("error cannot split ")
          }

          var collection : Collection = null
          if (colSpace.isEmpty || colSpace(0) == "default" || colSpace(0) == "") {
              collection = Collection(name = colName(0), description = colDesc(0), datasetCount = 0, created = new Date, author = identity, root_spaces = List.empty, stats = new Statistics())
          }
          else {
            val stringSpaces = colSpace(0).split(",").toList
            val colSpaces: List[UUID] = stringSpaces.map(aSpace => if(aSpace != "") UUID(aSpace) else None).filter(_ != None).asInstanceOf[List[UUID]]
            var root_spaces = List.empty[UUID]
            if(parentCollectionIds.length == 0) {
              root_spaces = colSpaces
            }
            collection = Collection(name = colName(0), description = colDesc(0), datasetCount = 0, created = new Date, author = identity, spaces = colSpaces, root_spaces = root_spaces, stats = new Statistics())
          }

          Logger.debug("Saving collection " + collection.name)
          collections.insert(collection)
          appConfig.incrementCount('collections, 1)
          collection.spaces.map{
            sp => spaceService.get(sp) match {
              case Some(s) => {
                spaces.addCollection(collection.id, s.id, user)
                collections.addToRootSpaces(collection.id, s.id)
                events.addSourceEvent(request.user, collection.id, collection.name, s.id, s.name, EventType.ADD_COLLECTION_SPACE.toString)
              }
              case None => Logger.error(s"space with id $sp on collection $collection.id doesn't exist.")
            }
          }

          //index collection
            current.plugin[ElasticsearchPlugin].foreach{
              _.index(SearchUtils.getElasticsearchObject(collection))
            }

          //Add to Events Table
          val option_user = users.findByIdentity(identity)
          events.addObjectEvent(option_user, collection.id, collection.name, EventType.CREATE_COLLECTION.toString)

          // redirect to collection page
          current.plugin[AdminsNotifierPlugin].foreach{_.sendAdminsNotification(Utils.baseUrl(request), "Collection","added",collection.id.toString,collection.name)}
          if (colParentColId != null && colParentColId.size>0) {
            try {
              collections.get(UUID(colParentColId(0))) match {
                case Some(parentCollection) => {
                  collections.addSubCollection(UUID(colParentColId(0)), collection.id, user)
                  Redirect(routes.Collections.collection(UUID(colParentColId(0))))
                }
                case None => {
                  Logger.error("Unable to add collection to parent ")
                  BadRequest(views.html.notFound("Parent collection does not exist."))
                }
              }

            } catch {
              case e : Exception => {
                InternalServerError("error with parent collection id " + colParentColId)
              }
            }
          } else {
            Redirect(routes.Collections.collection(collection.id))
          }
	      }
	      case None => Redirect(routes.Collections.list()).flashing("error" -> "You are not authorized to create new collections.")
      }
  }

  /**
   * Collection.
   */
  def collection(id: UUID, limit: Int) = PermissionAction(Permission.ViewCollection, Some(ResourceRef(ResourceRef.collection, id))) {
    implicit request =>
      Logger.debug(s"Showing collection $id")
      implicit val user = request.user

      val sortOrder: String =
        request.cookies.get("sort-order") match {
          case Some(cookie) => cookie.value
          case None => "dateN" //a default
        }

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
          val datasetsInside = if(play.Play.application().configuration().getBoolean("sortInMemory")) {
            SortingUtils.sortDatasets(datasets.listCollection(id.stringify, user), sortOrder)
          } else {
            datasets.listCollection(id.stringify, user)
          }
          val decodedDatasetsInside = datasetsInside.slice(0, limit).map(Utils.decodeDatasetElements(_))

          val child_collections = if(play.Play.application().configuration().getBoolean("sortInMemory")) {
            SortingUtils.sortCollections(collections.get(dCollection.child_collection_ids).found, sortOrder).slice(0, limit)
          } else {
            collections.get(dCollection.child_collection_ids.slice(0, limit)).found
          }
          val decodedChildCollections = child_collections.map(Utils.decodeCollectionElements(_))
          val decodedParentCollections = collections.get(dCollection.parent_collection_ids).found.map(Utils.decodeCollectionElements(_))

          val collectionSpaces = spaceService.get(collection.spaces).found
          var collectionSpaces_canRemove : Map[ProjectSpace,Boolean] = Map.empty
          collectionSpaces.foreach(s => {
            collectionSpaces_canRemove += (Utils.decodeSpaceElements(s) -> removeFromSpaceAllowed(dCollection.id, s.id))
          })

          val prevd = -1
          val nextd = if(datasetsInside.length > limit) 1 else -1
          val prevcc = -1
          val nextcc = if(dCollection.child_collection_ids.length > limit) 1 else -1

          val decodedSpaces_canRemove = collectionSpaces.map(collectionSpace => {
            (Utils.decodeSpaceElements(collectionSpace) -> removeFromSpaceAllowed(dCollection.id,collectionSpace.id))
          })

          var canAddToParent = Permission.checkOwner(user, ResourceRef(ResourceRef.collection, collection.id))
          if(!canAddToParent) {
            val parent_space_refs = collection.spaces.map(ResourceRef(ResourceRef.space, _))
            canAddToParent = !Permission.checkPermissions(Permission.AddResourceToCollection, parent_space_refs).approved.isEmpty
          }
          val userSelections = user match {
            case Some(u) => selections.get(u.email.get).map(_.id.toString)
            case None => List.empty
          }

          // Increment view count for collection
          val (view_count, view_date) = collections.incrementViews(id, user)
          sinkService.logCollectionViewEvent(collection, user)
          Ok(views.html.collectionofdatasets(decodedDatasetsInside.toList, decodedChildCollections.toList,
            Some(decodedParentCollections.toList),dCollection, filteredPreviewers, Some(collectionSpaces_canRemove),
            prevd,nextd, prevcc, nextcc, limit, canAddToParent, userSelections, view_count, view_date))

        }
        case None => {
          Logger.error("Error getting collection " + id)
          BadRequest(views.html.notFound("Collection does not exist."))
        }
      }
  }

  def getUpdatedDatasets(id: UUID, index: Int, limit: Int) = PermissionAction(Permission.ViewCollection, Some(ResourceRef(ResourceRef.collection, id))) { implicit request =>
      implicit val user = request.user
    collections.get(id) match {
      case Some(collection) => {

        val datasetsInside = datasets.listCollection(id.stringify, user)
        val datasetIdsToUse = datasetsInside.slice(index*limit, (index+1)*limit)
        val decodedDatasetsInside = ListBuffer.empty[models.Dataset]
        for (aDataset <- datasetIdsToUse) {
          val dDataset = Utils.decodeDatasetElements(aDataset)
          decodedDatasetsInside += dDataset
        }

        val prev = index-1
        val next = if(datasetsInside.length > (index+1) * limit) {
          index + 1
        } else {
          -1
        }
        user match {
          case Some(u) => {
            val selectIds = selections.get(u.email.get).map(_.id.toString)
            Ok(views.html.collections.datasetsInCollection(decodedDatasetsInside.toList, id, prev, next, selectIds))
          }
          case None => Ok(views.html.collections.datasetsInCollection(decodedDatasetsInside.toList, id, prev, next, List.empty))
        }


      }
      case None => Logger.error("Error getting "+ Messages("collection.title") + " " + id); BadRequest(Messages("collection.title") + " not found")
    }
  }

  def getUpdatedChildCollections(id: UUID, index: Int, limit: Int) = PermissionAction(Permission.ViewCollection, Some(ResourceRef(ResourceRef.collection, id))) { implicit request =>
    implicit val user = request.user
    collections.get(id) match {
      case Some(collection) => {
        val dCollection = Utils.decodeCollectionElements(collection)
        val child_ids = dCollection.child_collection_ids.slice(index*limit, (index+1)*limit)
        val decodedChildCollections = collections.get(child_ids).found.map(Utils.decodeCollectionElements(_))

        val prev = index-1
        val next = if (dCollection.child_collection_ids.length > (index+1)*limit)
          index+1
        else
          -1

        Ok(views.html.collections.childCollections(decodedChildCollections.toList, collection, prev, next))
      }
      case None => Logger.error("Error getting" + Messages("collection.title")  + " " + id); BadRequest(Messages("collection.title") + " not found")
    }
  }

  /**
   * Show all users with access to a collection (identified by its id)
   */
  def users(id: UUID) = PermissionAction(Permission.ViewCollection, Some(ResourceRef(ResourceRef.collection, id))) { implicit request =>
    implicit val user = request.user

    collections.get(id) match {
      case Some(collection) => {
        var userList: List[User] = List.empty
        var userListSpaceRoleTupleMap = Map[UUID, List[Tuple2[String,String]]]() // Map( User-id -> List((Space-name,Role-name)) )

        // Setup userList, add all users of all spaces associated with the collection
        collection.spaces.foreach { spaceId =>
          spaceService.get(spaceId) match {
            case Some(spc) => userList = spaceService.getUsersInSpace(spaceId, None) ::: userList
            case None => Redirect (routes.Collections.collection(id)).flashing ("error" -> s"Error: No $spaceTitle found for collection $id.");
          }
        }
        userList = userList.distinct.sortBy(_.fullName.toLowerCase)

        // Setup userListSpaceRoleTupleMap
        userList.foreach( usr => userListSpaceRoleTupleMap = userListSpaceRoleTupleMap + (usr.id -> List()) ) // initialize, based upon userList's values
        collection.spaces.foreach { spaceId =>
          spaceService.get(spaceId) match {
            case Some(spc) => {
              val usersInCurrSpace: List[User] = spaceService.getUsersInSpace(spaceId, None)
              if (usersInCurrSpace.nonEmpty) {

                usersInCurrSpace.foreach { usr =>
                  spaceService.getRoleForUserInSpace(spaceId, usr.id) match {
                    case Some(role) => userListSpaceRoleTupleMap += ( usr.id -> ((spc.name,role.name) :: userListSpaceRoleTupleMap(usr.id)) )
                    case None => Redirect(routes.Collections.collection(id)).flashing("error" -> s"Error: Role not found for collection $id user $usr.")
                  }
                }

              }
            }
            case None => Redirect (routes.Collections.collection(id)).flashing ("error" -> s"Error: No $spaceTitle found for collection $id.");
          }
        }
        // Clean-up, and sort space-names per user
        userListSpaceRoleTupleMap = userListSpaceRoleTupleMap filter (_._2.nonEmpty) // remove empty-list Values from Map (and corresponding Key)
        for(k <- userListSpaceRoleTupleMap.keys) userListSpaceRoleTupleMap += ( k -> userListSpaceRoleTupleMap(k).distinct.sortBy(_._1.toLowerCase) )

        if(userList.nonEmpty) {
          Ok(views.html.collections.users(collection, userListSpaceRoleTupleMap, userList))
        } else Redirect(routes.Collections.collection(id)).flashing("error" -> s"Error: No users found for collection $id.")
      }
      case None => Redirect(routes.Collections.collection(id)).flashing("error" -> s"Error: Collection $id not found.")
    }

  }

  def previews(collection_id: UUID) = PermissionAction(Permission.EditCollection, Some(ResourceRef(ResourceRef.collection, collection_id))) { implicit request =>
      collections.get(collection_id) match {
        case Some(collection) => {
          val previewsByCol = previewsService.findByCollectionId(collection_id)
          Ok(views.html.collectionPreviews(collection_id.toString, previewsByCol, Previewers.findCollectionPreviewers))
        }
        case None => {
          Logger.error("Error getting " + Messages("collection.title")  + " " + collection_id);
          BadRequest(Messages("collection.title") + " not found")
        }
      }
  }

  def listChildCollections(parentCollectionId : String ,when: String, date: String, limit: Int, space: Option[String], mode: String, owner: Option[String]) = PrivateServerAction { implicit request =>
    implicit val user = request.user

    val nextPage = (when == "a")
    val person = owner.flatMap(o => users.get(UUID(o)))
    val ownerName = person match {
      case Some(p) => Some(p.fullName)
      case None => None
    }
    val collectionSpace = space.flatMap(o => spaceService.get(UUID(o)))
    val spaceName = collectionSpace match {
      case Some(s) => Some(s.name)
      case None => None
    }

    val parentCollection = collections.get(UUID(parentCollectionId))
    var title: Option[String] = Some(Messages("collections.title"))

    val collectionList = person match {
      case Some(p) => {
        parentCollection match {
          case Some(parent) => {
            title = Some(Messages("owner.in.resource.title", p.fullName, Messages("collections.title"),  Messages("collection.parent.title"), routes.Collections.collection(parent.id), parent.name))
          }
          case None => {
            title = Some(Messages("owner.title", p.fullName, Messages("collections.title")))
          }
        }
        if (date != "")
          (collections.listUser(date, nextPage, limit, request.user, request.user.fold(false)(_.superAdminMode), p)).filter(_.parent_collection_ids.contains(parentCollectionId) == true)
        else
          (collections.listUser(limit, request.user, request.user.fold(false)(_.superAdminMode), p)).filter(_.parent_collection_ids.contains(parentCollectionId) == true)
      }
      case None => {
        space match {
          case Some(s) => {
            title = Some(Messages("resource.in.title", Messages("collections.title"), Messages("collection.parent.title"), routes.Collections.collection(parentCollection.get.id), parentCollection.get.name))
            if (date != "")
              (collections.listSpace(date, nextPage, limit, s)).filter(_.parent_collection_ids.contains(parentCollectionId) == true)
            else
              (collections.listSpace(limit, s)).filter(_.parent_collection_ids.contains(parentCollectionId) == true)
          }
          case None => {
            if (date != "")
              (collections.listAccess(date, nextPage, limit, Set[Permission](Permission.ViewCollection), request.user, request.user.fold(false)(_.superAdminMode), true, false)).filter(_.parent_collection_ids.contains(parentCollectionId) == true)
            else
              (collections.listAccess(limit, Set[Permission](Permission.ViewCollection), request.user, request.user.fold(false)(_.superAdminMode), true, false)).filter(_.parent_collection_ids.contains(parentCollectionId) == true)
          }
        }
      }
    }

    //collectionList = collectionList.filter((c : Collection) => c.parent_collection_ids.contains(parentCollectionId) == true)

    // check to see if there is a prev page
    val prev = if (collectionList.nonEmpty && date != "") {
      val first = Formatters.iso8601(collectionList.head.created)
      val c = person match {
        case Some(p) => (collections.listUser(first, nextPage=false, 1, request.user, request.user.fold(false)(_.superAdminMode), p)).filter(_.parent_collection_ids.contains(parentCollectionId) == true)
        case None => {
          space match {
            case Some(s) => (collections.listSpace(first, nextPage = false, 1, s)).filter(_.parent_collection_ids.contains(parentCollectionId) == true)
            case None => (collections.listAccess(first, nextPage = false, 1, Set[Permission](Permission.ViewCollection), request.user, request.user.fold(false)(_.superAdminMode), true, false)).filter(_.parent_collection_ids.contains(parentCollectionId) == true)
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
        case Some(p) => (collections.listUser(last, nextPage=true, 1, request.user, request.user.fold(false)(_.superAdminMode), p)).filter(_.parent_collection_ids.contains(parentCollectionId) == true)
        case None => {
          space match {
            case Some(s) => (collections.listSpace(last, nextPage = true, 1, s)).filter((c : Collection) => c.parent_collection_ids.contains(parentCollectionId) == true)
            case None => (collections.listAccess(last, nextPage = true, 1, Set[Permission](Permission.ViewCollection), request.user, request.user.fold(false)(_.superAdminMode), true,false)).filter(_.parent_collection_ids.contains(parentCollectionId) == true)
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

    val collectionsWithThumbnails = collectionList.map {c =>
      if (c.thumbnail_id.isDefined) {
        c
      } else {
        val collectionThumbnail = datasets.listCollection(c.id.stringify).find(_.thumbnail_id.isDefined).flatMap(_.thumbnail_id)
        c.copy(thumbnail_id = collectionThumbnail)
      }
    }

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
    Ok(views.html.collectionList(decodedCollections.toList, prev, next, limit, viewMode, space, spaceName, title, owner, ownerName, when, date))
  }

  private def removeFromSpaceAllowed(collectionId : UUID, spaceId : UUID) : Boolean = {
    return !(collections.hasParentInSpace(collectionId, spaceId))
  }

}

