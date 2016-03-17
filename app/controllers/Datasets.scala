package controllers

import java.io.FileInputStream
import java.text.SimpleDateFormat
import java.util.Date
import javax.inject.Inject
import api.Permission
import api.Permission.Permission
import fileutils.FilesUtils
import models._
import play.api.Logger
import play.api.Play.current
import play.api.libs.json.Json._
import services._
import util.{FileUtils, Formatters, RequiredFieldsConfig}
import scala.collection.immutable._
import scala.collection.mutable.ListBuffer

/**
 * A dataset is a collection of files and streams.
 */
class Datasets @Inject()(
  datasets: DatasetService,
  files: FileService,
  collections: CollectionService,
  comments: CommentService,
  sections: SectionService,
  extractions: ExtractionService,
  dtsrequests:ExtractionRequestsService,
  sparql: RdfSPARQLService,
  users: UserService,
  previewService: PreviewService,
  spaceService: SpaceService,
  curationService: CurationService,
  relations: RelationService,
  folders: FolderService,
  metadata: MetadataService,
  events: EventService) extends SecuredController {

  object ActivityFound extends Exception {}

  /**
   * Display the page that allows users to create new datasets
   */
  def newDataset(space: Option[String], collection: Option[String]) = PermissionAction(Permission.CreateDataset) { implicit request =>
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
    val spaceId = space match {
      case Some(s) => {
        spaceService.get(UUID(s)) match {
          case Some(space) =>  Some(space.id.toString)
          case None => None
        }
      }
      case None => None
    }

    val collectionSelected = collection match {
      case Some(c) => {
        collections.get(UUID(c)) match {
          case Some(collection) =>  Some(collection)
          case None => None
        }
      }
      case None => None
    }

    Ok(views.html.datasets.create(decodedSpaceList.toList, RequiredFieldsConfig.isNameRequired,
      RequiredFieldsConfig.isDescriptionRequired, spaceId, collectionSelected))

  }

  def createStep2(id: UUID) = PermissionAction(Permission.CreateDataset, Some(ResourceRef(ResourceRef.dataset, id))) { implicit request =>
    implicit val user = request.user
    datasets.get(id) match {
      case Some(dataset) => {
        Ok(views.html.datasets.createStep2(dataset))
      }
      case None => {
        InternalServerError(s"Dataset $id not found")
      }
    }
  }

  def addFiles(id: UUID) = PermissionAction(Permission.EditDataset, Some(ResourceRef(ResourceRef.dataset, id))) { implicit request =>
    implicit val user = request.user
    datasets.get(id) match {
      case Some(dataset) => {
        Ok(views.html.datasets.addFiles(dataset, None))
      }
      case None => {
        InternalServerError(s"Dataset $id not found")
      }
    }
  }

  def followingDatasets(index: Int, limit: Int, mode: String) = PrivateServerAction {implicit request =>
    implicit val user = request.user
    user match {
      case Some(clowderUser)  => {
        val title: Option[String] = Some("Following Datasets")
        var datasetList =  new ListBuffer[Dataset]()
        val datasetIds = clowderUser.followedEntities.filter(_.objectType == "dataset")
        val datasetIdsToUse = datasetIds.slice(index*limit, (index+1)*limit)
        val prev = index-1
        val next = if(datasetIds.length > (index+1) * limit) {
          index + 1
        } else {
          -1
        }

        for (tidObject <- datasetIdsToUse) {
            val followedDataset = datasets.get(tidObject.id)
            followedDataset match {
              case Some(fdset) => {
                datasetList += fdset
              }
              case None =>
            }
        }

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
          if (mode == null || mode == "") {
            request.cookies.get("view-mode") match {
              case Some(cookie) => Some(cookie.value)
              case None => None //If there is no cookie, and a mode was not passed in, the view will choose its default
            }
          } else {
            Some(mode)
          }

        //Pass the viewMode into the view
        Ok(views.html.users.followingDatasets(decodedDatasetList.toList, commentMap, prev, next, limit, viewMode, None, title, None))
      }
      case None => InternalServerError("No User found")
    }
  }

  /**
   * List datasets.
   */
  def list(when: String, date: String, limit: Int, space: Option[String], mode: String, owner: Option[String]) = PrivateServerAction { implicit request =>
    implicit val user = request.user

    val nextPage = (when == "a")
    val person = owner.flatMap(o => users.get(UUID(o)))
    val datasetSpace = space.flatMap(o => spaceService.get(UUID(o)))
    var title: Option[String] = Some("Datasets")

    val datasetList = person match {
      case Some(p) => {
        space match {
          case Some(s) => {
            title = Some(person.get.fullName + "'s Datasets in Space <a href=" + routes.Spaces.getSpace(datasetSpace.get.id) + ">" + datasetSpace.get.name + "</a>")
          }
          case None => {
            title = Some(person.get.fullName + "'s Datasets")
          }
        }
        if (date != "") {
          datasets.listUser(date, nextPage, limit, request.user, request.superAdmin, p)
        } else {
          datasets.listUser(limit, request.user, request.superAdmin, p)
        }
      }
      case None => {
        space match {
          case Some(s) => {
            title = Some("Datasets in Space <a href=" + routes.Spaces.getSpace(datasetSpace.get.id) + ">" + datasetSpace.get.name + "</a>")
            if (date != "") {
              datasets.listSpace(date, nextPage, limit, s)
            } else {
              datasets.listSpace(limit, s)
            }
          }
          case None => {
            if (date != "") {
              datasets.listAccess(date, nextPage, limit, Set[Permission](Permission.ViewDataset), request.user, request.superAdmin)
            } else {
              datasets.listAccess(limit, Set[Permission](Permission.ViewDataset), request.user, request.superAdmin)
            }

          }
        }
      }
    }

    // check to see if there is a prev page
    val prev = if (datasetList.nonEmpty && date != "") {
      val first = Formatters.iso8601(datasetList.head.created)
      val ds = person match {
        case Some(p) => datasets.listUser(first, nextPage=false, 1, request.user, request.superAdmin, p)
        case None => {
          space match {
            case Some(s) => datasets.listSpace(first, nextPage = false, 1, s)
            case None => datasets.listAccess(first, nextPage = false, 1, Set[Permission](Permission.ViewDataset), request.user, request.superAdmin)
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
        case Some(p) => datasets.listUser(last, nextPage=true, 1, request.user, request.superAdmin, p)
        case None => {
          space match {
            case Some(s) => datasets.listSpace(last, nextPage=true, 1, s)
            case None => datasets.listAccess(last, nextPage=true, 1, Set[Permission](Permission.ViewDataset), request.user, request.superAdmin)
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
      if (mode == null || mode == "") {
        request.cookies.get("view-mode") match {
          case Some(cookie) => Some(cookie.value)
          case None => None //If there is no cookie, and a mode was not passed in, the view will choose its default
        }
      } else {
        Some(mode)
      }

    //Pass the viewMode into the view
    Ok(views.html.datasetList(decodedDatasetList.toList, commentMap, prev, next, limit, viewMode, space, title, owner, when, date))
  }

  def addViewer(id: UUID, user: Option[User]) = {
      user match{
            case Some(viewer) => {
              implicit val email = viewer.email
              email match {
                case Some(addr) => {
                  implicit val modeluser = users.findByEmail(addr.toString())
                  modeluser match {
                    case Some(muser) => {
                       muser.viewed match {
                        case Some(viewList) =>{
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
   * Dataset.
   */
  def dataset(id: UUID, currentSpace: Option[String], limit: Int) = PermissionAction(Permission.ViewDataset, Some(ResourceRef(ResourceRef.dataset, id))) { implicit request =>

      implicit val user = request.user
      Previewers.findPreviewers.foreach(p => Logger.debug("Previewer found " + p.id))
      datasets.get(id) match {
        case Some(dataset) => {

          // get files info sorted by date
          val filesInDataset = dataset.files.flatMap(f => files.get(f) match {
            case Some(file) => Some(file)
            case None => Logger.debug(s"Unable to find file $f"); None
          }).asInstanceOf[List[File]].sortBy(_.uploadDate)

          var datasetWithFiles = dataset.copy(files = filesInDataset.map(_.id))
          datasetWithFiles = Utils.decodeDatasetElements(datasetWithFiles)

          val filteredPreviewers = Previewers.findDatasetPreviewers

          val m = metadata.getMetadataByAttachTo(ResourceRef(ResourceRef.dataset, dataset.id))

          val collectionsInside = collections.listInsideDataset(id, request.user, request.superAdmin).sortBy(_.name)
          var decodedCollectionsInside = new ListBuffer[models.Collection]()
          var filesTags = TreeSet.empty[String]

          for (aCollection <- collectionsInside) {
              val dCollection = Utils.decodeCollectionElements(aCollection)
              decodedCollectionsInside += dCollection
          }

          var commentsByDataset = comments.findCommentsByDatasetId(id)
          filesInDataset.map {
              file =>

              commentsByDataset ++= comments.findCommentsByFileId(file.id)
              sections.findByFileId(UUID(file.id.toString)).map { section =>
                commentsByDataset ++= comments.findCommentsBySectionId(section.id)
              }
          }
          commentsByDataset = commentsByDataset.sortBy(_.posted)

          //Decode the comments so that their free text will display correctly in the view
          var decodedCommentsByDataset = ListBuffer.empty[Comment]
          for (aComment <- commentsByDataset) {
            val dComment = Utils.decodeCommentElements(aComment)
            decodedCommentsByDataset += dComment
          }

          val isRDFExportEnabled = current.plugin[RDFExportService].isDefined


          filesInDataset.map
          {
            file =>
              file.tags.map {
                tag => filesTags += tag.name
              }
          }

          // associated sensors
          val sensors: List[(String, String, String)]= current.plugin[PostgresPlugin] match {
            case Some(db) => {
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
            case None => List.empty[(String, String, String)]
          }

          var datasetSpaces: List[ProjectSpace]= List.empty[ProjectSpace]
          dataset.spaces.map{
                  sp => spaceService.get(sp) match {
                    case Some(s) => {
                      datasetSpaces = s :: datasetSpaces
                    }
                    case None => Logger.error(s"space with id $sp on dataset $id doesn't exist.")
                  }
          }
          val decodedSpaces: List[ProjectSpace] = datasetSpaces.map{aSpace => Utils.decodeSpaceElements(aSpace)}

          val fileList : List[File]= dataset.files.reverse.map(f => files.get(f)).flatten

          //dataset is in at least one space with editstagingarea permission, or if the user is the owner of dataset.
          val stagingarea = datasetSpaces filter (space => Permission.checkPermission(Permission.EditStagingArea, ResourceRef(ResourceRef.space, space.id)))
          val toPublish = ! stagingarea.isEmpty

          val curObjectsPublished: List[CurationObject] = curationService.getCurationObjectByDatasetId(dataset.id).filter(_.status == 'Published)
          val curObjectsPermission: List[CurationObject] = curationService.getCurationObjectByDatasetId(dataset.id).filter(curation => Permission.checkPermission(Permission.EditStagingArea, ResourceRef(ResourceRef.curationObject, curation.id)))
          val curPubObjects: List[CurationObject] = curObjectsPublished ::: curObjectsPermission

          Ok(views.html.dataset(datasetWithFiles, commentsByDataset, filteredPreviewers.toList, m,
            decodedCollectionsInside.toList, isRDFExportEnabled, sensors, Some(decodedSpaces), fileList,
            filesTags, toPublish, curPubObjects, currentSpace, limit))
        }
        case None => {
          Logger.error("Error getting dataset" + id)
          InternalServerError
        }
    }
  }

  def getUpdatedFilesAndFolders(datasetId: UUID, limit: Int, pageIndex: Int)  = PermissionAction(Permission.ViewDataset, Some(ResourceRef(ResourceRef.dataset, datasetId))) (parse.json) { implicit request =>
    implicit val user = request.user
    val filepageUpdate = if (pageIndex < 0) 0 else pageIndex
    datasets.get(datasetId) match {
      case Some(dataset) => {
        val folderId = (request.body \ "folderId").asOpt[String]
        folderId match {
          case Some(fId) => {
            folders.get(UUID(fId)) match {
              case Some(folder) => {

                val foldersList = folder.folders.reverse.slice(limit * filepageUpdate, limit * (filepageUpdate+1)).map(f => folders.get(f)).flatten
                val limitFileList : List[File]= folder.files.reverse.slice(limit * filepageUpdate - folder.folders.length, limit * (filepageUpdate+1) - folder.folders.length).map(f => files.get(f)).flatten
                var folderHierarchy = new ListBuffer[Folder]()
                folderHierarchy += folder
                var f1: Folder = folder
                while(f1.parentType == "folder") {
                  folders.get(f1.parentId) match {
                    case Some(fparent) => {
                      folderHierarchy += fparent
                      f1 = fparent
                    }
                    case None =>
                  }
                }
                val fileComments = limitFileList.map{file =>
                  var allComments = comments.findCommentsByFileId(file.id)
                  sections.findByFileId(file.id).map { section =>
                    allComments ++= comments.findCommentsBySectionId(section.id)
                  }
                  file.id -> allComments.size
                }.toMap
                val next = folder.files.length + folder.folders.length > limit * (filepageUpdate+1)

                Ok(views.html.datasets.filesAndFolders(dataset, Some(folder.id.stringify), foldersList, folderHierarchy.reverse.toList, pageIndex, next, limitFileList.toList, fileComments)(request.user))

              }
              case None => InternalServerError(s"No folder with id $fId found")
            }
          }
          case None => {

            val foldersList = dataset.folders.reverse.slice(limit * filepageUpdate, limit * (filepageUpdate+1)).map(f => folders.get(f)).flatten

            val limitFileList : List[File]= dataset.files.reverse.slice(limit * filepageUpdate - dataset.folders.length, limit * (filepageUpdate+1) - dataset.folders.length).map(f => files.get(f)).flatten

            val fileComments = limitFileList.map{file =>
              var allComments = comments.findCommentsByFileId(file.id)
              sections.findByFileId(file.id).map { section =>
                allComments ++= comments.findCommentsBySectionId(section.id)
              }
              file.id -> allComments.size
            }.toMap

            val folderHierarchy = new ListBuffer[Folder]()
            val next = dataset.files.length + dataset.folders.length > limit * (filepageUpdate+1)
            Ok(views.html.datasets.filesAndFolders(dataset, None, foldersList, folderHierarchy.reverse.toList, pageIndex, next, limitFileList.toList, fileComments)(request.user))
          }
        }
      }
      case None => InternalServerError(s"Dataset with id $datasetId not Found")
    }
  }


  /**
   * Dataset by section.
   */
  def datasetBySection(section_id: UUID) = PermissionAction(Permission.ViewDataset, Some(ResourceRef(ResourceRef.section, section_id))) { implicit request =>
      sections.get(section_id) match {
        case Some(section) => {
          datasets.findOneByFileId(section.file_id) match {
            case Some(dataset) => Redirect(routes.Datasets.dataset(dataset.id))
            case None => InternalServerError("Dataset not found")
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

    val folder = folderId.flatMap(id => folders.get(UUID(id)))
    val retMap = request.body.asFormUrlEncoded.get("datasetid").flatMap(_.headOption) match {
      case Some(ds) => {
        datasets.get(UUID(ds)) match {
          case Some(dataset) => {
            val uploadedFiles = FileUtils.uploadFilesMultipart(request, showPreviews = "DatasetLevel", dataset = Some(dataset), folder = folder)
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
                    "name" -> toJson("Dataset ID Invalid."),
                    "size" -> toJson(0),
                    "error" -> toJson(s"Dataset with the specified ID=${ds} was not found. Please try again.")
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
                "name" -> toJson("Missing dataset ID."),
                "size" -> toJson(0),
                "error" -> toJson("No datasetid found. Please try again.")
              )
            )
          )
        )
      }
    }
    Ok(toJson(retMap))
  }

  def users(id: UUID) = PermissionAction(Permission.ViewDataset) { implicit request =>
    implicit val user = request.user

    datasets.get(id) match {
      case Some(dataset) => {
        var userList: List[User] = List.empty
        var userListSpaceRoleTupleMap = Map[UUID, List[Tuple2[String,String]]]() // Map( User-id -> List((Space-name,Role-name)) )

        // Setup userList, add all users of all spaces associated with the dataset
        dataset.spaces.foreach { spaceId =>
          spaceService.get(spaceId) match {
            case Some(spc) => userList = spaceService.getUsersInSpace(spaceId) ::: userList
            case None => Redirect(routes.Datasets.dataset(id)).flashing("error" -> s"Error: No spaces found for dataset $id.")
          }
        }
        userList = userList.distinct.sortBy(_.fullName.toLowerCase)

        // Setup userListSpaceRoleTupleMap
        userList.foreach( usr => userListSpaceRoleTupleMap = userListSpaceRoleTupleMap + (usr.id -> List()) ) // initialize, based upon userList's values
        dataset.spaces.foreach { spaceId =>
          spaceService.get(spaceId) match {
            case Some(spc) => {
              val usersInCurrSpace: List[User] = spaceService.getUsersInSpace(spaceId)
              if (usersInCurrSpace.nonEmpty) {

                usersInCurrSpace.foreach { usr =>
                  spaceService.getRoleForUserInSpace(spaceId, usr.id) match {
                    case Some(role) => userListSpaceRoleTupleMap += ( usr.id -> ((spc.name,role.name) :: userListSpaceRoleTupleMap(usr.id)) )
                    case None => Redirect(routes.Datasets.dataset(id)).flashing("error" -> s"Error: Role not found for dataset $id user $usr.")
                  }
                }

              }
            }
            case None => Redirect (routes.Datasets.dataset(id)).flashing ("error" -> s"Error: No spaces found for dataset $id.");
          }
        }
        // Clean-up, and sort space-names per user
        userListSpaceRoleTupleMap = userListSpaceRoleTupleMap filter (_._2.nonEmpty) // remove empty-list Values from Map (and corresponding Key)
        for(k <- userListSpaceRoleTupleMap.keys) userListSpaceRoleTupleMap += ( k -> userListSpaceRoleTupleMap(k).distinct.sortBy(_._1.toLowerCase) )

        if(userList.nonEmpty) {
          val currUserIsAuthor = user.get.identityId.userId.equals(dataset.author.identityId.userId)
          Ok(views.html.datasets.users(dataset, userListSpaceRoleTupleMap, currUserIsAuthor, userList))
        }
        else Redirect(routes.Datasets.dataset(id)).flashing("error" -> s"Error: No users found for dataset $id.")
      }
      case None => Redirect(routes.Datasets.dataset(id)).flashing("error" -> s"Error: Dataset $id not found.")
    }

  }

  def metadataSearch() = PermissionAction(Permission.ViewDataset) { implicit request =>
      implicit val user = request.user
      Ok(views.html.metadataSearch())
  }

  def generalMetadataSearch() = PermissionAction(Permission.ViewDataset) { implicit request =>
      implicit val user = request.user
      Ok(views.html.generalMetadataSearch())
  }
}