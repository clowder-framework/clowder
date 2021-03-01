package controllers

import javax.inject.Inject
import api.Permission
import api.Permission.Permission
import models._
import play.api.Logger
import play.api.Play.current
import play.api.libs.json.Json._
import services._
import util.{FileUtils, Formatters, RequiredFieldsConfig, SortingUtils }
import scala.collection.immutable._
import scala.collection.mutable.ListBuffer
import play.api.i18n.Messages

/**
 * A dataset is a collection of files and streams.
 */
class Datasets @Inject() (
    datasets: DatasetService,
    files: FileService,
    collections: CollectionService,
    comments: CommentService,
    sections: SectionService,
    extractions: ExtractionService,
    dtsrequests: ExtractionRequestsService,
    sparql: RdfSPARQLService,
    users: UserService,
    previewService: PreviewService,
    spaceService: SpaceService,
    curationService: CurationService,
    relations: RelationService,
    folders: FolderService,
    metadata: MetadataService,
    events: EventService,
    selections: SelectionService,
    sinkService: EventSinkService) extends SecuredController {

  object ActivityFound extends Exception {}

  /**
   * String name of the Space such as 'Project space' etc., from conf/messages
   */
  val spaceTitle: String = Messages("space.title")

  /**
   * Display the page that allows users to create new datasets
   */
  def newDataset(space: Option[String], collection: Option[String]) = PermissionAction(Permission.CreateDataset) { implicit request =>
    implicit val user = request.user
    val spacesList = user.get.spaceandrole.map(_.spaceId).flatMap(spaceService.get(_))
    val perms = Permission.checkPermissions(Permission.AddResourceToSpace, spacesList.map(s => ResourceRef(ResourceRef.space, s.id))).approved
    val decodedSpaceList = spacesList.filter(sp => perms.map(_.id).exists(_ == sp.id)).map(Utils.decodeSpaceElements(_))

    var hasVerifiedSpace = false
    val (spaceId, spaceName) = space match {
      case Some(s) => {
        spaceService.get(UUID(s)) match {
          case Some(space) => {
            hasVerifiedSpace = !space.isTrial
            (Some(space.id.toString), Some(space.name))
          }
          case None => (None, None)
        }
      }
      case None => (None, None)
    }

    var collectionSpaces: ListBuffer[String] = ListBuffer.empty[String]

    val collectionSelected = collection match {
      case Some(c) => {
        collections.get(UUID(c)) match {
          case Some(collection) => {
            //if the spaces of the collection are not automatically added to the dataset spaces
            //they will be preselected in the view, but the user can choose
            //not to share the dataset with those spaces
            if (play.Play.application().configuration().getBoolean("addDatasetToCollectionSpace")) {
              for (collection_space <- collection.spaces) {
                spaceService.get(collection_space) match {
                  case Some(col_space) => {
                    collectionSpaces += col_space.id.stringify
                  }
                  case None => Logger.error("No space found for id " + collection_space)
                }
              }
            }
            Some(collection)
          }
          case None => None
        }
      }
      case None => None
    }
    val showAccess = play.Play.application().configuration().getBoolean("enablePublic") &&
      (!play.Play.application().configuration().getBoolean("verifySpaces") || hasVerifiedSpace)

    Ok(views.html.datasets.create(decodedSpaceList.toList, RequiredFieldsConfig.isNameRequired,
      RequiredFieldsConfig.isDescriptionRequired, spaceId, spaceName, collectionSelected, collectionSpaces.toList, showAccess))

  }

  def createStep2(id: UUID) = PermissionAction(Permission.CreateDataset, Some(ResourceRef(ResourceRef.dataset, id))) { implicit request =>
    implicit val user = request.user
    datasets.get(id) match {
      case Some(dataset) => {
        var datasetSpaces: List[ProjectSpace]= List.empty[ProjectSpace]

        dataset.spaces.map(sp =>
          spaceService.get(sp) match {
          case Some(s) => datasetSpaces =  s :: datasetSpaces
          case None =>
        })
        Ok(views.html.datasets.createStep2(dataset, datasetSpaces))
      }
      case None => {
        InternalServerError(s"$Messages('dataset.title') $id not found")
      }
    }
  }

  def addFiles(id: UUID) = PermissionAction(Permission.AddResourceToDataset, Some(ResourceRef(ResourceRef.dataset, id))) { implicit request =>
    implicit val user = request.user
    datasets.get(id) match {
      case Some(dataset) => {
            var datasetSpaces: List[ProjectSpace]= List.empty[ProjectSpace]

            dataset.spaces.map(sp =>
              spaceService.get(sp) match {
                case Some(s) => datasetSpaces =  s :: datasetSpaces
                case None =>
              })
            Ok(views.html.datasets.addFiles(dataset, None, datasetSpaces, List.empty))
      }
      case None => {
        InternalServerError(s"$Messages('dataset.title')  $id not found")
      }
    }
  }

  def followingDatasets(index: Int, limit: Int, mode: String) = PrivateServerAction { implicit request =>
    implicit val user = request.user
    user match {
      case Some(clowderUser) => {
        val title: Option[String] = Some(Messages("following.title", Messages("datasets.title")))
        var datasetList = new ListBuffer[Dataset]()
        val datasetIds = clowderUser.followedEntities.filter(_.objectType == "dataset")
        val datasetIdsToUse = datasetIds.slice(index * limit, (index + 1) * limit)
        val prev = index - 1
        val next = if (datasetIds.length > (index + 1) * limit) {
          index + 1
        } else {
          -1
        }

        datasets.get(datasetIdsToUse.map(_.id)).found.foreach(followedDataset => datasetList += followedDataset)

        //Modifications to decode HTML entities that were stored in an encoded fashion as part
        //of the datasets names or descriptions
        val decodedDatasetList = ListBuffer.empty[models.Dataset]
        for (aDataset <- datasetList) {
          decodedDatasetList += Utils.decodeDatasetElements(aDataset)
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

        Logger.debug("User selections" + user)
        val userSelections: List[String] =
          if(user.isDefined) selections.get(user.get.identityId.userId).map(_.id.stringify)
          else List.empty[String]
        Logger.debug("User selection " + userSelections)

        //Pass the viewMode into the view
        Ok(views.html.users.followingDatasets(decodedDatasetList.toList, prev, next, limit, viewMode, None, title, None, userSelections))
      }
      case None => InternalServerError("No User found")
    }
  }

  /**
   * List datasets.
   */
  def list(when: String, date: String, limit: Int, space: Option[String], status: Option[String], mode: String, owner: Option[String], showPublic: Boolean, showOnlyShared : Boolean, showTrash : Boolean) = UserAction(needActive=false) { implicit request =>
    implicit val user = request.user

    val nextPage = (when == "a")
    val person = owner.flatMap(o => users.get(UUID(o)))
    val ownerName = person match {
      case Some(p) => Some(p.fullName)
      case None => None
    }
    val datasetSpace = space.flatMap(o => spaceService.get(UUID(o)))
    val spaceName = datasetSpace match {
      case Some(s) => Some(s.name)
      case None => None
    }
    var title: Option[String] = Some(Messages("list.title", Messages("datasets.title")))

    Logger.debug("User selections" + user)
    val userSelections: List[String] =
      if(user.isDefined) selections.get(user.get.identityId.userId).map(_.id.stringify)
      else List.empty[String]
    Logger.debug("User selection " + userSelections)

    val datasetList = person match {
      case Some(p) => {
        space match {
          case Some(s) if datasetSpace.isDefined=> {
            title = Some(Messages("owner.in.resource.title", p.fullName, Messages("datasets.title"), spaceTitle, routes.Spaces.getSpace(datasetSpace.get.id), datasetSpace.get.name))
          }
          case _ => {
            if (showTrash) {
              title = Some(Messages("owner.title", p.fullName, play.api.i18n.Messages("datasets.trashtitle")))
            } else {
              title = Some(Messages("owner.title", p.fullName, play.api.i18n.Messages("datasets.title")))
            }          }
        }
        if (date != "") {
          if (showTrash){
            datasets.listUserTrash(date, nextPage, limit, request.user, request.user.fold(false)(_.superAdminMode), p)
          } else {
            datasets.listUser(date, nextPage, limit, request.user, request.user.fold(false)(_.superAdminMode), p)
          }
        } else {
          if (showTrash){
            datasets.listUserTrash(limit, request.user, request.user.fold(false)(_.superAdminMode), p)
          } else {
            datasets.listUser(limit, request.user, request.user.fold(false)(_.superAdminMode), p)
          }
        }
      }
      case None => {
        space match {
          case Some(s) if datasetSpace.isDefined => {
            title = Some(Messages("resource.in.title", Messages("datasets.title"), spaceTitle, routes.Spaces.getSpace(datasetSpace.get.id), datasetSpace.get.name))
            if (date != "") {
              status match {
                case Some(st) => datasets.listSpaceStatus(date, nextPage, limit, s, st, user)
                case None => datasets.listSpace(date, nextPage, limit, s, user)
              }
            } else {
              status match {
                case Some(st) => datasets.listSpaceStatus(limit, s, st, user)
                case None => datasets.listSpace(limit, s, user)
              }
            }
          }
          case _ => {
            if (date != "") {
              datasets.listAccess(date, nextPage, limit, Set[Permission](Permission.ViewDataset), request.user, request.user.fold(false)(_.superAdminMode), showPublic, showOnlyShared)
            } else {
              datasets.listAccess(limit, Set[Permission](Permission.ViewDataset), request.user, request.user.fold(false)(_.superAdminMode), showPublic, showOnlyShared)
            }

          }
        }
      }
    }

    // check to see if there is a prev page
    val prev = if (datasetList.nonEmpty && date != "") {
      val first = Formatters.iso8601(datasetList.head.created)
      val ds = person match {
        case Some(p) => {
          if (showTrash){
            datasets.listUserTrash(first, nextPage=false, 1, request.user, request.user.fold(false)(_.superAdminMode), p)
          } else {
            datasets.listUser(first, nextPage=false, 1, request.user, request.user.fold(false)(_.superAdminMode), p)
          }
        }
        case None => {
          space match {
            case Some(s) => {
              status match {
                case Some(st) => datasets.listSpaceStatus(first, nextPage=false, 1, s, st, user)
                case None => datasets.listSpace(first, nextPage=false, 1, s, user)
              }
            }
            case None => datasets.listAccess(first, nextPage = false, 1, Set[Permission](Permission.ViewDataset), request.user, request.user.fold(false)(_.superAdminMode), showPublic, showOnlyShared)
          }
        }
      }
      if (ds.nonEmpty && ds.head.id != datasetList.head.id) {
        first
      } else {
        ""
      }
    } else {
      ""
    }

    // check to see if there is a next page
    val next = if (datasetList.nonEmpty) {
      val last = Formatters.iso8601(datasetList.last.created)
      val ds = person match {
        case Some(p) => {
          if (showTrash){
            datasets.listUserTrash(last, nextPage=true, 1, request.user, request.user.fold(false)(_.superAdminMode), p)
          } else {
            datasets.listUser(last, nextPage=true, 1, request.user, request.user.fold(false)(_.superAdminMode), p)
          }
        }
        case None => {
          space match {
            case Some(s) => {
                status match {
                  case Some(st) => datasets.listSpaceStatus(last, nextPage=true, 1, s, st, user)
                  case None => datasets.listSpace(last, nextPage=true, 1, s, user)
                }
              }
            case None => datasets.listAccess(last, nextPage=true, 1, Set[Permission](Permission.ViewDataset), request.user, request.user.fold(false)(_.superAdminMode), showPublic, showOnlyShared)
          }
        }
      }
      if (ds.nonEmpty && ds.head.id != datasetList.last.id) {
        last
      } else {
        ""
      }
    } else {
      ""
    }

    //Modifications to decode HTML entities that were stored in an encoded fashion as part
    //of the datasets names or descriptions
    val decodedDatasetList = ListBuffer.empty[models.Dataset]
    for (aDataset <- datasetList) {
      decodedDatasetList += Utils.decodeDatasetElements(aDataset)
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
    if (!showPublic) {
      title = Some(Messages("you.title", Messages("datasets.title")))
    }
    //Pass the viewMode into the view
    space match {
      case Some(s) if datasetSpace.isEmpty => {
        NotFound(views.html.notFound(spaceTitle + " not found."))
      }
      case Some(s) if !Permission.checkPermission(Permission.ViewSpace, ResourceRef(ResourceRef.space, UUID(s))) => {
        BadRequest(views.html.notAuthorized("You are not authorized to access the " + spaceTitle + ".", s, "space"))
      }
      case _ =>
        Ok(views.html.datasetList(decodedDatasetList.toList, prev, next, limit, viewMode, space, spaceName,
        status, title, owner, ownerName, when, date, userSelections, showTrash))
    }
  }

  def addViewer(id: UUID, user: Option[User]) = {
    user match {
      case Some(viewer) => {
        implicit val email = viewer.email
        email match {
          case Some(addr) => {
            implicit val modeluser = users.findByEmail(addr.toString())
            modeluser match {
              case Some(muser) => {
                muser.viewed match {
                  case Some(viewList) => {
                    users.addUserDatasetView(addr, id)
                  }
                  case None => {
                    val newList: List[UUID] = List(id)
                    users.createNewListInUser(addr, "viewed", newList)
                  }
                }
              }
              case None => {
                Ok("NOT WORKS")
              }
            }
          }
        }
      }

    }
  }

  /**
   * Sorted List of datasets within a space
   * Since this only works within a space right now, it just checks to see if the user has permission to view the space 
   * (which takes into account the public settings) and, if so, calls the method to list all datasets in the space, regardless 
   * of status/public view flags, etc. To generalize for sorting of other lists, the permission checks will need to be in 
   * the dataset query (as in the list method).
   */
  def sortedListInSpace(space: String, offset: Int, size: Int, showPublic: Boolean) = UserAction(needActive = false) { implicit request =>
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

    var title: Option[String] = Some(Messages("resource.in.title", Messages("datasets.title"), spaceTitle, routes.Spaces.getSpace(datasetSpace.get.id), datasetSpace.get.name))

    if (!datasetSpace.isDefined) {
      Logger.error(s"space with id $space doesn't exist.")
      BadRequest(views.html.notFound("Space " + space + " not found."))
    } else {
      if (!Permission.checkPermission(Permission.ViewSpace, ResourceRef(ResourceRef.space, UUID(space)))) {
        BadRequest(views.html.notAuthorized("You are not authorized to access the " + spaceTitle + ".", datasetSpace.get.name, "space"))
      } else {
        val dList = datasets.listSpaceAccess(0, Set[Permission](Permission.ViewDataset), space, user, false, showPublic);
        val len = dList.length
        Logger.debug("User selections" + user)
        val userSelections: List[String] =
          if(user.isDefined) selections.get(user.get.identityId.userId).map(_.id.stringify)
          else List.empty[String]
        Logger.debug("User selection " + userSelections)
        val datasetList = SortingUtils.sortDatasets(dList, sortOrder).drop(offset).take(size)
        val commentMap = datasetList.map { dataset =>
          var allComments = comments.findCommentsByDatasetId(dataset.id)
          dataset.files.map { file =>
            allComments ++= comments.findCommentsByFileId(file)
            sections.findByFileId(file).map { section =>
              allComments ++= comments.findCommentsBySectionId(section.id)
            }
          }
          dataset.id -> allComments.size
        }.toMap

        //Modifications to decode HTML entities that were stored in an encoded fashion as part
        //of the datasets names or descriptions
        val decodedDatasetList = ListBuffer.empty[models.Dataset]
        for (aDataset <- datasetList) {
          decodedDatasetList += Utils.decodeDatasetElements(aDataset)
        }

        //Code to read the cookie data. On default calls, without a specific value for the mode, the cookie value is used.
        //Note that this cookie will, in the long run, pertain to all the major high-level views that have the similar
        //modal behavior for viewing data. Currently the options are tile and list views. MMF - 12/14
        val viewMode: Option[String] =
          request.cookies.get("view-mode") match {
            case Some(cookie) => Some(cookie.value)
            case None => None //If there is no cookie, and a mode was not passed in, the view will choose its default
          }

        val prev: String = if (offset != 0) {
          offset.toString()
        } else {
          ""
        }

        val next: String = if (len > (offset + size)) {
          (offset + size).toString()
        } else {
          ""
        }

        val date = ""

        Ok(views.html.datasetList(decodedDatasetList.toList, prev, next, size, viewMode, Some(space), spaceName, None, title, None, None, "a", date, userSelections))
      }
    }
  }

  /**
   * Dataset.
   */
  def dataset(id: UUID, currentSpace: Option[String], limit: Int, filter: Option[String]) = PermissionAction(Permission.ViewDataset, Some(ResourceRef(ResourceRef.dataset, id))) { implicit request =>
    implicit val user = request.user
    Previewers.findDatasetPreviewers().foreach(p => Logger.debug("Previewer found " + p.id))
    datasets.get(id) match {
      case Some(dataset) => {
        // previewers
        val filteredPreviewers = Previewers.findDatasetPreviewers

        // metadata
        val m = metadata.getMetadataByAttachTo(ResourceRef(ResourceRef.dataset, dataset.id))

        // collections
        val collectionsInside = collections.listInsideDataset(id, request.user, request.user.fold(false)(_.superAdminMode)).sortBy(_.name)
        var decodedCollectionsInside = new ListBuffer[models.Collection]()
        for (aCollection <- collectionsInside) {
          val dCollection = Utils.decodeCollectionElements(aCollection)
          decodedCollectionsInside += dCollection
        }

        // comments
        var commentsByDataset = comments.findCommentsByDatasetId(id).sortBy(_.posted)
        // decode the comments so that their free text will display correctly in the view
        var decodedCommentsByDataset = ListBuffer.empty[Comment]
        for (aComment <- commentsByDataset) {
          val dComment = Utils.decodeCommentElements(aComment)
          decodedCommentsByDataset += dComment
        }

        // sensors
        val sensors: List[(String, String, String)] = current.plugin[PostgresPlugin] match {
          case Some(db) if db.isEnabled => {
            // findRelationships will return a "Relation" model with all information about the relationship
            val relationships = relations.findRelationships(id.stringify, ResourceType.dataset, ResourceType.sensor)

            // we want to get the name of the sensor and its location on Geodashboard
            // the "target.id" in a relationship is the Sensor's ID from the geostreaming API (like 117)
            // we will lookup the name and url using the sensor ID, then return each sensor in a list of tuples:
            // [(relationship_ID, sensor_name, geodashboard_url), ...]
            relationships.map { r =>
              val nameToURLTuple = db.getDashboardSensorURLs(List(r.target.id)).head
              (r.id.stringify, nameToURLTuple._1, nameToURLTuple._2)
            }
          }
          case _ => List.empty[(String, String, String)]
        }

        // spaces
        var datasetSpaces: List[ProjectSpace] = List.empty[ProjectSpace]
        var decodedSpaces_canRemove: Map[ProjectSpace, Boolean] = Map.empty
        var isInPublicSpace = false
        dataset.spaces.map {
            sp => spaceService.get(sp) match {
              case Some(s) => {
                decodedSpaces_canRemove += (Utils.decodeSpaceElements(s) -> true)
                datasetSpaces = s :: datasetSpaces
                if (s.isPublic) {
                  isInPublicSpace = true
                }
              }
              case None => Logger.error(s"space with id $sp on $Messages('dataset.title') $id doesn't exist.")
            }
        }
        // dataset is in at least one space with editstagingarea permission, or if the user is the owner of dataset
        val perms = Permission.checkPermissions(Permission.EditStagingArea, datasetSpaces.map(ds => ResourceRef(ResourceRef.space, ds.id))).approved
        val stagingarea = datasetSpaces.filter(sp => perms.map(_.id).exists(_ == sp.id))
        val toPublish = !stagingarea.isEmpty
        val curObjs = curationService.getCurationObjectByDatasetId(dataset.id)
        val curObjectsPublished = curObjs.filter(_.status == 'Published)
        val coperms = Permission.checkPermissions(Permission.EditStagingArea, curObjs.map(co => ResourceRef(ResourceRef.curationObject, co.id))).approved
        val curObjectsPermission = curObjs.filter(co => coperms.map(_.id).exists(_ == co.id))
        val curPubObjects: List[CurationObject] = curObjectsPublished ::: curObjectsPermission

        // download button
        var showDownload: Boolean = dataset.files.length > 0
        if (!showDownload) {
          val foldersList = folders.findByParentDatasetId(dataset.id)
          foldersList.map { folder =>
            if (folder.files.length > 0) { showDownload = true }
          }
        }

        // access control based on config flags `verifySpaces`, `enablePublic`
        var showAccess = false
        if (play.Play.application().configuration().getBoolean("verifySpaces")) {
          showAccess = !dataset.isTRIAL
        } else {
          showAccess = play.Play.application().configuration().getBoolean("enablePublic")
        }
        val access = if (showAccess) {
          if (dataset.isDefault && isInPublicSpace) {
            "Public (" + spaceTitle + " Default)"
          } else if (dataset.isDefault && !isInPublicSpace) {
            "Private (" + spaceTitle + " Default)"
          } else {
            dataset.status(0).toUpper + dataset.status.substring(1).toLowerCase()
          }
        } else {
          ""
        }
        val accessOptions = new ListBuffer[String]()
        if (isInPublicSpace) {
          accessOptions.append(spaceTitle + " Default (Public)")
        } else {
          accessOptions.append(spaceTitle + " Default (Private)")
        }
        accessOptions.append(DatasetStatus.PRIVATE.toString.substring(0, 1).toUpperCase() +
          DatasetStatus.PRIVATE.toString.substring(1).toLowerCase())
        accessOptions.append(DatasetStatus.PUBLIC.toString.substring(0, 1).toUpperCase() +
          DatasetStatus.PUBLIC.toString.substring(1).toLowerCase())
        val accessData = new models.DatasetAccess(showAccess, access, accessOptions.toList)

        // add to collection permissions
        var canAddDatasetToCollection = Permission.checkOwner(user, ResourceRef(ResourceRef.dataset, dataset.id))
        if (!canAddDatasetToCollection) {
          val parent_space_refs = datasetSpaces.map(space => ResourceRef(ResourceRef.space, space.id))
          canAddDatasetToCollection = !Permission.checkPermissions(Permission.AddResourceToCollection, parent_space_refs).approved.isEmpty
        }

        // staging area
        val stagingAreaDefined = play.api.Play.current.plugin[services.StagingAreaPlugin].isDefined

        // extraction logs
        val extractionsByDataset = extractions.findById(new ResourceRef('dataset, id))
        val extractionGroups = extractions.groupByType(extractionsByDataset)

        // increment view count for dataset
        val view_data = datasets.incrementViews(id, user)

        // related datasets
        val relatedThings = relations.findRelationships(dataset.id.stringify, ResourceType.dataset, ResourceType.dataset)
        val relatedDatasets = for(r <- relatedThings) yield NodeDataset(datasets.get(UUID(r.target.id)).get, r.rdfType)

        sinkService.logDatasetViewEvent(dataset, user)
        // view_data is passed as tuple in dataset case only, because template is at limit of 22 parameters
        Ok(views.html.dataset(dataset, commentsByDataset, filteredPreviewers.toList, m,
          decodedCollectionsInside.toList, sensors, Some(decodedSpaces_canRemove), toPublish, curPubObjects,
          currentSpace, limit, showDownload, accessData, canAddDatasetToCollection,
          stagingAreaDefined, view_data, extractionGroups, relatedDatasets, filter))
      }
      case None => {
        Logger.error("Error getting dataset" + id)
        BadRequest(views.html.notFound(Messages("dataset.title") + " does not exist."))
      }
    }
  }

  def getUpdatedFilesAndFolders(datasetId: UUID, limit: Int, pageIndex: Int, space: Option[String], filter: Option[String]) = PermissionAction(Permission.ViewDataset, Some(ResourceRef(ResourceRef.dataset, datasetId)))(parse.json) { implicit request =>
    implicit val user = request.user
    val filepageUpdate = if (pageIndex < 0) 0 else pageIndex
    val sortOrder: String =
      request.cookies.get("sort-order") match {
        case Some(cookie) => cookie.value
        case None => "dateN" //If there is no cookie, and an order was not passed in, the view will choose its default
      }

    datasets.get(datasetId) match {
      case Some(dataset) => {
        val folderHierarchy = new ListBuffer[Folder]()

        val folderId = (request.body \ "folderId").asOpt[String]
        val (childFolders: List[UUID], childFiles: List[UUID]) =
          folderId match {
            case Some(fId) => folders.get(UUID(fId)) match {
              case Some(folder) => {
                folderHierarchy += folder
                var f1 = folder
                while (f1.parentType == "folder") {
                  folders.get(f1.parentId) match {
                    case Some(fparent) => {
                      folderHierarchy += fparent
                      f1 = fparent
                    }
                    case None => Logger.error("Parent folder " + f1.parentId.toString + " not found.")
                  }
                }
                (folder.folders, folder.files)
              }
              case None => Logger.error("Folder " + fId + " not found.")
            }
            case None => (dataset.folders, dataset.files)
          }

        val filteredFiles = filter match {
          case Some(filt) => files.get(childFiles).found.filter(f => f.filename.toLowerCase.contains(filt.toLowerCase))
          case None => files.get(childFiles).found
        }

        val filteredFolders = filter match {
          case Some(filt) => folders.get(childFolders).found.filter(f => f.name.toLowerCase.contains(filt.toLowerCase))
          case None => folders.get(childFolders).found
        }

        val (foldersList: List[Folder], limitFileList: List[File]) =
          if (play.Play.application().configuration().getBoolean("sortInMemory")) {
            (SortingUtils.sortFolders(filteredFolders, sortOrder).slice(limit * filepageUpdate, limit * (filepageUpdate + 1)),
              SortingUtils.sortFiles(filteredFiles, sortOrder).slice(limit * filepageUpdate - filteredFolders.length, limit * (filepageUpdate + 1) - filteredFolders.length))
          } else {
            (folders.get(childFolders.reverse.slice(limit * filepageUpdate, limit * (filepageUpdate + 1))).found,
              files.get(childFiles.reverse.slice(limit * filepageUpdate - childFolders.length, limit * (filepageUpdate + 1) - childFolders.length)).found)
          }

        // Get comment counts per file
        val fileComments = limitFileList.map { file =>
          var allComments = comments.findCommentsByFileId(file.id)
          sections.findByFileId(file.id).map { section =>
            allComments ++= comments.findCommentsBySectionId(section.id)
          }
          file.id -> allComments.size
        }.toMap

        // Pagination
        val next = childFiles.length + childFolders.length > limit * (filepageUpdate + 1)

        Ok(views.html.datasets.filesAndFolders(dataset, folderId, foldersList, folderHierarchy.reverse.toList, pageIndex, next, limitFileList.toList, fileComments, space, filter)(request.user))
      }
      case None => InternalServerError(s"Dataset with id $datasetId not Found")
    }
  }

  /**
   * Dataset by section.
   */
  def datasetBySection(section_id: UUID) = PermissionAction(Permission.ViewSection, Some(ResourceRef(ResourceRef.section, section_id))) { implicit request =>
    sections.get(section_id) match {
      case Some(section) => {
        datasets.findOneByFileId(section.file_id) match {
          case Some(dataset) => Redirect(routes.Datasets.dataset(dataset.id))
          case None => InternalServerError(Messages("dataset.title") + " not found")
        }
      }
      case None => InternalServerError("Section not found")
    }
  }

  /**
   * Controller flow that handles the new multi file uploader workflow for creating a new dataset. Requires name, description,
   * and id for the dataset. The interface should validate to ensure that these are present before reaching this point, but
   * the checks are made here as well.
   *
   */
  def submit(folderId: Option[String]) = PermissionAction(Permission.CreateDataset)(parse.multipartFormData) { implicit request =>
    implicit val user = request.user

    Logger.debug("------- in Datasets.submit ---------")

    val folder = folderId.flatMap(id => folders.get(UUID(id)))
    val retMap = request.body.asFormUrlEncoded.get("datasetid").flatMap(_.headOption) match {
      case Some(ds) => {
        datasets.get(UUID(ds)) match {
          case Some(dataset) => {
            val uploadedFiles = FileUtils.uploadFilesMultipart(request, showPreviews = "DatasetLevel",
              dataset = Some(dataset), folder = folder, apiKey=request.apiKey)
            Map("files" -> uploadedFiles.map(f => toJson(Map(
              "name" -> toJson(f.filename),
              "size" -> toJson(f.length),
              "url" -> toJson(routes.Files.file(f.id).absoluteURL(Utils.https(request))),
              "deleteUrl" -> toJson(api.routes.Files.removeFile(f.id).absoluteURL(Utils.https(request))),
              "deleteType" -> toJson("POST")
            ))))
          }
          case None => {
            Map("files" ->
              Seq(
                toJson(
                  Map(
                    "name" -> toJson(Messages("dataset.title") + " ID Invalid."),
                    "size" -> toJson(0),
                    "error" -> toJson(s"${Messages("dataset.title")} with the specified ID=${ds} was not found. Please try again.")
                  )
                )
              )
            )
          }
        }
      }
      case None => {
        Map("files" ->
          Seq(
            toJson(
              Map(
                "name" -> toJson("Missing " + Messages("dataset.title") + "  ID."),
                "size" -> toJson(0),
                "error" -> toJson("No "+ Messages("dataset.title")+"id found. Please try again.")
              )
            )
          )
        )
      }
    }
    Ok(toJson(retMap))
  }

  def users(id: UUID) = PermissionAction(Permission.ViewDataset, Some(ResourceRef(ResourceRef.dataset, id))) { implicit request =>
    implicit val user = request.user

    datasets.get(id) match {
      case Some(dataset) => {
        var userList: List[User] = List.empty
        var userListSpaceRoleTupleMap = Map[UUID, List[Tuple2[String, String]]]() // Map( User-id -> List((Space-name,Role-name)) )

        // Setup userList, add all users of all spaces associated with the dataset
        dataset.spaces.foreach { spaceId =>
          spaceService.get(spaceId) match {
            case Some(spc) => userList = spaceService.getUsersInSpace(spaceId, None) ::: userList
            case None => Redirect(routes.Datasets.dataset(id)).flashing("error" -> s"Error: No $spaceTitle found for $Messages('dataset.title') $id.")
          }
        }
        userList = userList.distinct.sortBy(_.fullName.toLowerCase)

        // Setup userListSpaceRoleTupleMap
        userList.foreach(usr => userListSpaceRoleTupleMap = userListSpaceRoleTupleMap + (usr.id -> List())) // initialize, based upon userList's values
        dataset.spaces.foreach { spaceId =>
          spaceService.get(spaceId) match {
            case Some(spc) => {
              val usersInCurrSpace: List[User] = spaceService.getUsersInSpace(spaceId, None)
              if (usersInCurrSpace.nonEmpty) {

                usersInCurrSpace.foreach { usr =>
                  spaceService.getRoleForUserInSpace(spaceId, usr.id) match {
                    case Some(role) => userListSpaceRoleTupleMap += (usr.id -> ((spc.name, role.name) :: userListSpaceRoleTupleMap(usr.id)))
                    case None => Redirect(routes.Datasets.dataset(id)).flashing("error" -> s"Error: Role not found for $Messages('dataset.title') $id user $usr.")
                  }
                }

              }
            }
            case None => Redirect(routes.Datasets.dataset(id)).flashing("error" -> s"Error: No $spaceTitle found for $Messages('dataset.title') $id.");
          }
        }
        // Clean-up, and sort space-names per user
        userListSpaceRoleTupleMap = userListSpaceRoleTupleMap filter (_._2.nonEmpty) // remove empty-list Values from Map (and corresponding Key)
        for (k <- userListSpaceRoleTupleMap.keys) userListSpaceRoleTupleMap += (k -> userListSpaceRoleTupleMap(k).distinct.sortBy(_._1.toLowerCase))

        if (userList.nonEmpty) {
          Ok(views.html.datasets.users(dataset, userListSpaceRoleTupleMap, userList))
      }
        else Redirect(routes.Datasets.dataset(id)).flashing("error" -> s"Error: No users found for $Messages('dataset.title') $id.")
      }
      case None => Redirect(routes.Datasets.dataset(id)).flashing("error" -> s"Error: $Messages('dataset.title') $id not found.")
    }

  }

  def metadataSearch() = PermissionAction(Permission.ViewMetadata) { implicit request =>
    implicit val user = request.user
    Ok(views.html.metadataSearch())
  }

  def generalMetadataSearch() = PermissionAction(Permission.ViewMetadata) { implicit request =>
    implicit val user = request.user
    Ok(views.html.generalMetadataSearch())
  }
}