package controllers

import java.util.Date
import java.net.URLDecoder

import javax.inject.Inject
import api.Permission._
import api.{ UserRequest, Permission }
import com.fasterxml.jackson.annotation.JsonValue
import models._
import org.apache.commons.lang.StringEscapeUtils._
import play.api.Logger
import play.api.i18n.Messages
import play.api.libs.json._
import play.api.libs.json.Json._
import play.api.libs.json.JsArray
import services._
import _root_.util.{ Formatters, RequiredFieldsConfig, Publications }
import play.api.Play._
import scala.collection.mutable.ListBuffer
import scala.concurrent.{ Future, Await }
import play.api.mvc.{ MultipartFormData, Request, Action, Results }
import play.api.libs.ws._
import scala.concurrent.duration._
import play.api.libs.json.Reads._

/**
 * Methods for interacting with the Curation Objects (now referred to as Publication Requests) in the staging area.
 */
class CurationObjects @Inject() (
  curations: CurationService,
  datasets: DatasetService,
  collections: CollectionService,
  spaces: SpaceService,
  files: FileService,
  folders: FolderService,
  comments: CommentService,
  sections: SectionService,
  events: EventService,
  userService: UserService,
  metadatas: MetadataService,
  contextService: ContextLDService,
  routing: ExtractorRoutingService) extends SecuredController {

  /**
   * String name of the Space such as 'Project space' etc., parsed from conf/messages
   */
  val spaceTitle: String = Messages("space.title")

  def newCO(datasetId: UUID, spaceId: String) = PermissionAction(Permission.EditDataset, Some(ResourceRef(ResourceRef.dataset, datasetId))) { implicit request =>
    implicit val user = request.user
    val (name, desc, creators, spaceByDataset) = datasets.get(datasetId) match {
      case Some(dataset) => {
        val perms = Permission.checkPermissions(Permission.EditStagingArea, dataset.spaces.map(ResourceRef(ResourceRef.space, _)))
        (dataset.name, dataset.description, dataset.creators, spaces.get(perms.approved.map(_.id)).found)
      }
      case None => ("", "", List.empty, List.empty)
    }
    //default space is the space from which user access to the dataset
    val defaultspace = spaceId match {
      case "" => if (spaceByDataset.length == 1) spaceByDataset.lift(0) else None
      case _ => spaces.get(UUID(spaceId))
    }

    Ok(views.html.curations.newCuration(datasetId, name, desc, defaultspace, spaceByDataset, RequiredFieldsConfig.isNameRequired,
      true, true, creators))
  }

  /**
   * List Publication Requests.
   */
  def list(when: String, date: String, limit: Int, space: Option[String]) = UserAction(needActive = false) { implicit request =>
    implicit val user = request.user

    val nextPage = (when == "a")
    val curationObjectSpace = space.flatMap(o => spaces.get(UUID(o)))
    val title: Option[String] = Some(curationObjectSpace.get.name)

    val curationObjectList: List[CurationObject] = {
      if (date != "") {
        curations.listSpace(date, nextPage, Some(limit), space)
      } else {
        curations.listSpace(Some(limit), space)
      }
    }

    // check to see if there is a prev page
    val prev = if (curationObjectList.nonEmpty && date != "") {
      val first = Formatters.iso8601(curationObjectList.head.created)
      val ds = curations.listSpace(first, nextPage = false, Some(1), space)

      if (ds.nonEmpty && ds.head.id != curationObjectList.head.id) {
        first
      } else {
        ""
      }
    } else {
      ""
    }

    // check to see if there is a next page
    val next = if (curationObjectList.nonEmpty) {
      val last = Formatters.iso8601(curationObjectList.last.created)
      val ds = curations.listSpace(last, nextPage = true, Some(1), space)
      if (ds.nonEmpty && ds.head.id != curationObjectList.last.id) {
        last
      } else {
        ""
      }
    } else {
      ""
    }

    //Code to read the cookie data. On default calls, without a specific value for the mode, the cookie value is used.
    //Note that this cookie will, in the long run, pertain to all the major high-level views that have the similar
    //modal behavior for viewing data. Currently the options are tile and list views. MMF - 12/14
    val viewMode: Option[String] =
      request.cookies.get("view-mode") match {
        case Some(cookie) => Some(cookie.value)
        case None => None //If there is no cookie, and a mode was not passed in, the view will choose its default
      }

    Ok(views.html.curationObjectList(curationObjectList, prev, next, limit, viewMode, space, title))
  }

  /**
   * Controller flow to create a new publication request/curation object. On success,
   * the browser is redirected to the new Publication Request page.
   */
  def submit(datasetId: UUID, spaceId: UUID) = PermissionAction(Permission.EditStagingArea, Some(ResourceRef(ResourceRef.space, spaceId)))(parse.multipartFormData) { implicit request =>

    //get name, des, space from request
    val COName = request.body.asFormUrlEncoded.getOrElse("name", null)
    val CODesc = request.body.asFormUrlEncoded.getOrElse("description", null)
    val COCreators = request.body.asFormUrlEncoded.getOrElse("creators", List.empty)

    implicit val user = request.user
    user match {
      case Some(identity) => {

        datasets.get(datasetId) match {
          case Some(dataset) => {
            if (spaces.get(spaceId) != None) {

              //copy file list from FileDAO. and save curation file metadata. metadataCount is 0 since
              // metadatas.getMetadataByAttachTo will increase metadataCount
              var newFiles: List[UUID] = List.empty
              files.get(dataset.files).found.foreach(f => {
                // Pull sha512 from metadata of file rather than file object itself
                var sha512 = ""
                metadatas.getMetadataByAttachTo(ResourceRef(ResourceRef.file, f.id)).map { md =>
                  val sha = (md.content \\ "sha512")
                  if (sha.length > 0)
                    sha512 = sha(0).toString
                }

                val cf = CurationFile(fileId = f.id, author = f.author, filename = f.filename, uploadDate = f.uploadDate,
                  contentType = f.contentType, length = f.length, showPreviews = f.showPreviews, sections = f.sections, previews = f.previews, tags = f.tags,
                  thumbnail_id = f.thumbnail_id, metadataCount = 0, licenseData = f.licenseData, sha512 = sha512)
                curations.insertFile(cf)
                newFiles = cf.id :: newFiles
                metadatas.getMetadataByAttachTo(ResourceRef(ResourceRef.file, f.id)).map(m => {
                  val metadataId = metadatas.addMetadata(m.copy(id = UUID.generate(), attachedTo = ResourceRef(ResourceRef.curationFile, cf.id)))
                  val mdMap = m.getExtractionSummary

                  //send RabbitMQ message
                  routing.metadataAddedToResource(metadataId, ResourceRef(ResourceRef.file, f.id), mdMap, Utils.baseUrl(request),
                      request.apiKey, request.user)
                })
              })

              //the model of CO have multiple datasets and collections, here we insert a list containing one dataset
              val newCuration = CurationObject(
                name = COName(0),
                author = identity,
                description = CODesc(0),
                created = new Date,
                submittedDate = None,
                publishedDate = None,
                space = spaceId,
                datasets = List(dataset),
                files = newFiles,
                folders = List.empty,
                repository = None,
                status = "In Preparation",
                creators = COCreators(0).split(",").toList.map(x => URLDecoder.decode(x, "UTF-8")))

              // insert curation
              Logger.debug("create curation object: " + newCuration.id)
              curations.insert(newCuration)

              dataset.folders.map(f => copyFolders(f, newCuration.id, "dataset", newCuration.id, request.host,
                request.apiKey, request.user))
              metadatas.getMetadataByAttachTo(ResourceRef(ResourceRef.dataset, dataset.id))
                .map(m => {
                  if ((m.content \ "Creator").isInstanceOf[JsUndefined]) {
                    val metadataId = metadatas.addMetadata(m.copy(id = UUID.generate(), attachedTo =
                      ResourceRef(ResourceRef.curationObject, newCuration.id)))
                    val mdMap = m.getExtractionSummary
                    //send RabbitMQ message
                    routing.metadataAddedToResource(metadataId, ResourceRef(ResourceRef.dataset, dataset.id), mdMap,
                        Utils.baseUrl(request), request.apiKey, request.user)
                  }
                })
              Redirect(routes.CurationObjects.getCurationObject(newCuration.id))
            } else {
              InternalServerError(spaceTitle + " not found")
            }
          }
          case None => InternalServerError("Dataset Not found")
        }
      }
      case None => InternalServerError("User Not found")
    }
  }

  private def copyFolders(id: UUID, parentId: UUID, parentType: String, parentCurationObjectId: UUID,
    requestHost: String, apiKey: Option[String], user: Option[User]): Unit = {
    folders.get(id) match {
      case Some(folder) => {
        var newFiles: List[UUID] = List.empty
        files.get(folder.files).found.foreach(f => {
          // Pull sha512 from metadata of file rather than file object itself
          var sha512 = ""
          metadatas.getMetadataByAttachTo(ResourceRef(ResourceRef.file, f.id)).map { md =>
            val sha = (md.content \\ "sha512")
            if (sha.length > 0)
              sha512 = sha(0).toString
          }

          val cf = CurationFile(fileId = f.id, author = f.author, filename = f.filename, uploadDate = f.uploadDate,
            contentType = f.contentType, length = f.length, showPreviews = f.showPreviews, sections = f.sections, previews = f.previews, tags = f.tags,
            thumbnail_id = f.thumbnail_id, metadataCount = 0, licenseData = f.licenseData, sha512 = sha512)
          curations.insertFile(cf)
          newFiles = cf.id :: newFiles
          metadatas.getMetadataByAttachTo(ResourceRef(ResourceRef.file, f.id))
            .map(m => {
              val curationRef = ResourceRef(ResourceRef.curationFile, cf.id)
              val metadataId = metadatas.addMetadata(m.copy(id = UUID.generate(), attachedTo = curationRef))
              val mdMap = m.getExtractionSummary
              //send RabbitMQ message
              routing.metadataAddedToResource(metadataId, curationRef, mdMap, requestHost, apiKey, user)
            })
        })

        val newCurationFolder = CurationFolder(
          folderId = id,
          author = folder.author,
          created = folder.created,
          name = folder.name,
          displayName = folder.displayName,
          files = newFiles,
          folders = List.empty,
          parentId = parentId,
          parentType = parentType.toLowerCase(),
          parentCurationObjectId = parentCurationObjectId)
        curations.insertFolder(newCurationFolder)
        curations.addCurationFolder(parentType, parentId, newCurationFolder.id)

        folder.folders.map(f => copyFolders(f, newCurationFolder.id, "folder", parentCurationObjectId,
          requestHost, apiKey, user))
      }
      case None => {
        Logger.error("Folder Not found in Publication Request")

      }
    }
  }

  def editCuration(id: UUID) = PermissionAction(Permission.EditStagingArea, Some(ResourceRef(ResourceRef.curationObject, id))) {
    implicit request =>
      implicit val user = request.user
      curations.get(id) match {
        case Some(c) => {
          val perms = Permission.checkPermissions(Permission.EditStagingArea,
            c.datasets.head.spaces.map(ResourceRef(ResourceRef.space, _)))
          val spaceByDataset = spaces.get(perms.approved.map(_.id)).found
          Ok(views.html.curations.newCuration(id, c.name, c.description, spaces.get(c.space), spaceByDataset,
            RequiredFieldsConfig.isNameRequired, true, false, c.creators))
        }
        case None => BadRequest(views.html.notFound("Publication Request does not exist."))
      }
  }

  def updateCuration(id: UUID, spaceId: UUID) = PermissionAction(Permission.EditStagingArea, Some(ResourceRef(ResourceRef.curationObject, id)))(parse.multipartFormData) {
    implicit request =>
      implicit val user = request.user
      curations.get(id) match {
        case Some(c) => {
          val COName = request.body.asFormUrlEncoded.getOrElse("name", null)
          val CODesc = request.body.asFormUrlEncoded.getOrElse("description", null)
          val COCreators = request.body.asFormUrlEncoded.getOrElse("creators", List.empty)
          curations.updateInformation(id, CODesc(0), COName(0), c.space, spaceId, COCreators(0).split(",").toList.map(URLDecoder.decode(_, "UTF-8")))
          events.addObjectEvent(user, id, COName(0), "update_curation_information")

          Redirect(routes.CurationObjects.getCurationObject(id))
        }
        case None => BadRequest(views.html.notFound("Publication Request does not exist."))
      }
  }

  /**
   * Delete publication request / curation object.
   */
  def deleteCuration(id: UUID) = PermissionAction(Permission.EditStagingArea, Some(ResourceRef(ResourceRef.curationObject, id))) {
    implicit request =>
      implicit val user = request.user

      curations.get(id) match {
        case Some(c) => {
          Logger.debug("delete Publication Request / Curation object: " + c.id)
          val spaceId = c.space

          curations.remove(id, Utils.baseUrl(request), request.apiKey, request.user)
          //spaces.get(spaceId) is checked in Space.stagingArea
          Redirect(routes.Spaces.stagingArea(spaceId))
        }
        case None => InternalServerError("Publication Request Not found")
      }
  }

  def getCurationObject(curationId: UUID, limit: Int) = PermissionAction(Permission.EditStagingArea, Some(ResourceRef(ResourceRef.curationObject, curationId))) { implicit request =>
    implicit val user = request.user
    curations.get(curationId) match {
      case Some(cOld) => {
        spaces.get(cOld.space) match {
          case Some(s) => {
            // this update is not written into MongoDB, only for page view purpose
            val c = datasets.get(cOld.datasets(0).id) match {
              case Some(dataset) => cOld.copy(datasets = List(dataset))
              // dataset is deleted
              case None => cOld
            }
            // metadata of curation files are getting from getUpdatedFilesAndFolders
            val m = metadatas.getMetadataByAttachTo(ResourceRef(ResourceRef.curationObject, c.id))
            val isRDFExportEnabled = current.plugin[RDFExportService].isDefined
            val fileByDataset = curations.getCurationFiles(curations.getAllCurationFileIds(c.id))
            if (c.status != "In Preparation") {
              Ok(views.html.spaces.submittedCurationObject(c, fileByDataset, m, limit, s.name))
            } else {
              Ok(views.html.spaces.curationObject(c, m, isRDFExportEnabled, limit))
            }
          }
          case None => BadRequest(views.html.notFound("Space does not exist."))
        }

      }
      case None => BadRequest(views.html.notFound("Publication Request does not exist."))
    }
  }

  def getUpdatedFilesAndFolders(curationId: UUID, curationFolderId: String, limit: Int, pageIndex: Int) = PermissionAction(Permission.EditStagingArea, Some(ResourceRef(ResourceRef.curationObject, curationId))) { implicit request =>
    implicit val user = request.user
    val filepageUpdate = if (pageIndex < 0) 0 else pageIndex
    curations.get(curationId) match {
      case Some(c) => {
        curationFolderId match {
          // curationFolderId is set to "None" if it is currently on curation page
          case "None" => {
            val foldersList = c.folders.reverse.slice(limit * filepageUpdate, limit * (filepageUpdate + 1)).map(curations.getCurationFolder(_)).flatten
            val limitFileIds: List[UUID] = c.files.reverse.slice(limit * filepageUpdate - c.folders.length, limit * (filepageUpdate + 1) - c.folders.length)
            val limitFileList: List[CurationFile] = curations.getCurationFiles(limitFileIds).map(cf =>
              files.get(cf.fileId) match {
                case Some(currentFile) => cf.copy(filename = currentFile.filename)
                case None => cf
              })
            val mCurationFile = c.files.map(f => metadatas.getMetadataByAttachTo(ResourceRef(ResourceRef.curationFile, f))).flatten

            val folderHierarchy = new ListBuffer[CurationFolder]()
            val next = c.files.length + c.folders.length > limit * (filepageUpdate + 1)
            Ok(views.html.curations.filesAndFolders(c, None, foldersList, folderHierarchy.reverse.toList, pageIndex, next, limitFileList.toList, mCurationFile))
          }
          // Otherwise it is on a curation folder's page
          case _ => {
            curations.getCurationFolder(UUID(curationFolderId)) match {
              case Some(cf) => {
                val foldersList = cf.folders.reverse.slice(limit * filepageUpdate, limit * (filepageUpdate + 1)).map(curations.getCurationFolder(_)).flatten
                val limitFileIds: List[UUID] = cf.files.reverse.slice(limit * filepageUpdate - cf.folders.length, limit * (filepageUpdate + 1) - cf.folders.length)
                val limitFileList: List[CurationFile] = curations.getCurationFiles(limitFileIds).map(cf =>
                  files.get(cf.fileId) match {
                    case Some(currentFile) => cf.copy(filename = currentFile.filename)
                    case None => cf
                  })
                val mCurationFile = limitFileIds.map(f => metadatas.getMetadataByAttachTo(ResourceRef(ResourceRef.curationFile, f))).flatten
                var folderHierarchy = new ListBuffer[CurationFolder]()
                folderHierarchy += cf
                var f1: CurationFolder = cf
                while (f1.parentType == "folder") {
                  curations.getCurationFolder(f1.parentId) match {
                    case Some(fparent) => {
                      folderHierarchy += fparent
                      f1 = fparent
                    }
                    case None =>
                  }
                }
                val next = cf.files.length + cf.folders.length > limit * (filepageUpdate + 1)
                Ok(views.html.curations.filesAndFolders(c, Some(cf.id.stringify), foldersList, folderHierarchy.reverse.toList, pageIndex, next, limitFileList.toList, mCurationFile))
              }
              case None => BadRequest(views.html.notFound("Folder does not exist in Publication Request."))
            }
          }

        }

      }
      case None => BadRequest(views.html.notFound("Publication Request does not exist."))
    }
  }

  def findMatchingRepositories(curationId: UUID) = PermissionAction(Permission.EditStagingArea, Some(ResourceRef(ResourceRef.curationObject, curationId))) {
    implicit request =>
      implicit val user = request.user
      curations.get(curationId) match {
        case Some(cOld) => {
          spaces.get(cOld.space) match {
            case Some(s) => {
              val c = cOld.copy(datasets = datasets.get(cOld.datasets(0).id).toList)
              val propertiesMap: Map[String, List[String]] =
                if (s.isTrial) {
                  Map("Purpose" -> List("Testing-Only"))
                } else {
                  Map("Purpose" -> List("Production", "Testing-Only"))
                }

              val mmResp = callMatchmaker(c, user)(request)
              user match {
                case Some(usr) => {
                  val repPreferences = usr.repositoryPreferences.map { value => value._1 -> value._2.toString().split(",").toList }

                  Ok(views.html.spaces.matchmakerResult(c, propertiesMap, repPreferences, mmResp))
                }
                case None => Results.Redirect(routes.Error.authenticationRequiredMessage("You must be logged in to perform that action.", request.uri))
              }
            }
            case None => BadRequest(views.html.notFound("Space does not exist."))
          }

        }
        case None => BadRequest(views.html.notFound("Publication Request does not exist."))
      }
  }

  def callMatchmaker(c: CurationObject, user: Option[User])(implicit request: Request[Any]): List[MatchMakerResponse] = {
    val https = controllers.Utils.https(request)
    val hostUrl = api.routes.CurationObjects.getCurationObjectOre(c.id).absoluteURL(https) + "#aggregation"
    var userPrefMap = userService.findById(c.author.id).map(usr => usr.repositoryPreferences.map(pref => if (pref._1 != "Purpose") { pref._1 -> Json.toJson(pref._2.toString().split(",").toList) } else { pref._1 -> Json.toJson(pref._2.toString()) })).getOrElse(Map.empty)
    if (spaces.get(c.space).get.isTrial) userPrefMap += ("Purpose" -> Json.toJson("Testing-Only"))
    var userPreferences = userPrefMap + ("Repository" -> Json.toJson(c.repository))
    user.map(usr => usr.profile match {
      case Some(prof) => prof.institution match {
        case Some(institution) => userPreferences += ("Affiliations" -> Json.toJson(institution))
        case None =>
      }
      case None =>
    })
    val fileIds = curations.getAllCurationFileIds(c.id)
    val files = curations.getCurationFiles(fileIds)
    val maxDataset = if (!fileIds.isEmpty) files.map(_.length).max else 0
    val totalSize = if (!fileIds.isEmpty) files.map(_.length).sum else 0
    var metadataList = scala.collection.mutable.ListBuffer.empty[MetadataPair]
    var metadataKeys = Set.empty[String]
    metadatas.getMetadataByAttachTo(ResourceRef(ResourceRef.curationObject, c.id)).filter(_.creator.typeOfAgent == "cat:user").map {
      item =>
        for ((key, value) <- buildMetadataMap(item.content)) {
          metadataList += MetadataPair(key, value)
          metadataKeys += key
        }
    }
    var metadataJson = scala.collection.mutable.Map.empty[String, JsValue]
    for (key <- metadataKeys) {
      metadataJson = metadataJson ++ Map(key -> Json.toJson(metadataList.filter(_.label == key).map { item => item.content }toList))
    }
    val metadataDefsMap = scala.collection.mutable.Map.empty[String, JsValue]
    for (md <- metadatas.getDefinitions(Some(c.space))) {
      metadataDefsMap((md.json \ "label").asOpt[String].getOrElse("").toString()) = Json.toJson((md.json \ "uri").asOpt[String].getOrElse(""))
    }

    val creator = userService.findById(c.author.id).map(usr => usr.profile match {
      case Some(prof) => prof.orcidID match {
        case Some(oid) => oid
        case None => api.routes.Users.findById(usr.id).absoluteURL(https)
      }
      case None => api.routes.Users.findById(usr.id).absoluteURL(https)

    })
    val rightsholder = user.map(usr => usr.profile match {
      case Some(prof) => prof.orcidID match {
        case Some(oid) => oid
        case None => api.routes.Users.findById(usr.id).absoluteURL(https)
      }
      case None => api.routes.Users.findById(usr.id).absoluteURL(https)

    })

    val format = new java.text.SimpleDateFormat("dd-MM-yyyy")
    var aggregation = metadataJson.toMap ++ Map(
      "Identifier" -> Json.toJson(controllers.routes.CurationObjects.getCurationObject(c.id).absoluteURL(https)),
      "@id" -> Json.toJson(hostUrl),
      "Title" -> Json.toJson(c.name),
      "Uploaded By" -> Json.toJson(creator),
      "similarTo" -> Json.toJson(controllers.routes.Datasets.dataset(c.datasets(0).id).absoluteURL(https)),
      "Publishing Project" -> Json.toJson(controllers.routes.Spaces.getSpace(c.space).absoluteURL(https)),
      "Creation Date" -> Json.toJson(format.format(c.created)))
    if (metadataJson.contains("Creator")) {
      val value = c.creators ++ metadataList.filter(_.label == "Creator").map { item => item.content.as[String] }.toList
      aggregation = aggregation ++ Map("Creator" -> Json.toJson(value))
    } else {
      aggregation = aggregation ++ Map("Creator" -> Json.toJson(c.creators))
    }
    if (!metadataDefsMap.contains("Creator")) {
      metadataDefsMap("Creator") = Json.toJson(Map("@id" -> "http://purl.org/dc/terms/creator", "@container" -> "@list"))
    }
    if (metadataJson.contains("Abstract")) {
      val value = List(c.description) ++ metadataList.filter(_.label == "Abstract").map { item => item.content.as[String] }
      aggregation = aggregation ++ Map("Abstract" -> Json.toJson(value))
    } else {
      aggregation = aggregation ++ Map("Abstract" -> Json.toJson(c.description))
    }
    if (!metadataDefsMap.contains("Abstract")) {
      metadataDefsMap("Abstract") = Json.toJson("http://purl.org/dc/terms/abstract")
    }
    val valuetoSend = Json.obj(
      "@context" -> Json.toJson(Seq(
        Json.toJson("https://w3id.org/ore/context"),
        Json.toJson(metadataDefsMap.toMap ++ Map(
          "Identifier" -> Json.toJson("http://purl.org/dc/elements/1.1/identifier"),
          "Rights Holder" -> Json.toJson("http://purl.org/dc/terms/rightsHolder"),
          "Aggregation" -> Json.toJson("http://sead-data.net/terms/aggregation"),
          "Title" -> Json.toJson("http://purl.org/dc/elements/1.1/title"),
          "similarTo" -> Json.toJson("http://sead-data.net/terms/similarTo"),
          "Uploaded By" -> Json.toJson("http://purl.org/dc/elements/1.1/creator"),
          "Preferences" -> Json.toJson("http://sead-data.net/terms/publicationpreferences"),
          "Aggregation Statistics" -> Json.toJson("http://sead-data.net/terms/publicationstatistics"),
          "Data Mimetypes" -> Json.toJson("http://purl.org/dc/elements/1.1/format"),
          "Max Collection Depth" -> Json.toJson("http://sead-data.net/terms/maxcollectiondepth"),
          "Total Size" -> Json.toJson("tag:tupeloproject.org,2006:/2.0/files/length"),
          "Max Dataset Size" -> Json.toJson("http://sead-data.net/terms/maxdatasetsize"),
          "Number of Datasets" -> Json.toJson("http://sead-data.net/terms/datasetcount"),
          "Number of Collections" -> Json.toJson("http://sead-data.net/terms/collectioncount"),
          "Affiliations" -> Json.toJson("http://sead-data.net/terms/affiliations"),
          "Access" -> Json.toJson("http://sead-data.net/terms/access"),
          "License" -> Json.toJson("http://purl.org/dc/terms/license"),
          "Cost" -> Json.toJson("http://sead-data.net/terms/cost"),
          "Repository" -> Json.toJson("http://sead-data.net/terms/requestedrepository"),
          "Alternative title" -> Json.toJson("http://purl.org/dc/terms/alternative"),
          "Contact" -> Json.toJson("http://sead-data.net/terms/contact"),
          "name" -> Json.toJson("http://sead-data.net/terms/name"),
          "email" -> Json.toJson("http://schema.org/Person/email"),
          "Description" -> Json.toJson("http://purl.org/dc/elements/1.1/description"),
          "Audience" -> Json.toJson("http://purl.org/dc/terms/audience"),
          "Abstract" -> Json.toJson("http://purl.org/dc/terms/abstract"),
          "Bibliographic citation" -> Json.toJson("http://purl.org/dc/terms/bibliographicCitation"),
          "Purpose" -> Json.toJson("http://sead-data.net/vocab/publishing#Purpose"),
          "Publishing Project" -> Json.toJson("http://sead-data.net/terms/publishingProject"),
          "Creation Date" -> Json.toJson("http://purl.org/dc/terms/created"),
          "Spatial Reference" ->
            Json.toJson(
              Map(
                "@id" -> Json.toJson("tag:tupeloproject.org,2006:/2.0/gis/hasGeoPoint"),
                "Longitude" -> Json.toJson("http://www.w3.org/2003/01/geo/wgs84_pos#long"),
                "Latitude" -> Json.toJson("http://www.w3.org/2003/01/geo/wgs84_pos#lat"),
                "Altitude" -> Json.toJson("http://www.w3.org/2003/01/geo/wgs84_pos#alt"))))))),
      "Rights Holder" -> Json.toJson(rightsholder),
      "Aggregation" ->
        Json.toJson(aggregation),
      "Preferences" -> userPreferences,
      "Aggregation Statistics" ->
        Map(
          "Data Mimetypes" -> Json.toJson(files.map(_.contentType).toSet),
          "Max Collection Depth" -> Json.toJson(curations.maxCollectionDepth(c).toString()),
          "Max Dataset Size" -> Json.toJson(maxDataset.toString),
          "Total Size" -> Json.toJson(totalSize.toString),
          "Number of Datasets" -> Json.toJson(fileIds.length),
          "Number of Collections" -> Json.toJson(c.datasets.length)))
    implicit val context = scala.concurrent.ExecutionContext.Implicits.global
    val endpoint = play.Play.application().configuration().getString("matchmaker.uri").replaceAll("/$", "")
    val futureResponse = WS.url(endpoint).post(valuetoSend)
    Logger.debug("Value to send matchmaker: " + valuetoSend)
    var jsonResponse: play.api.libs.json.JsValue = new JsArray()
    val result = futureResponse.map {
      case response =>
        if (response.status >= 200 && response.status < 300 || response.status == 304) {
          jsonResponse = response.json
          Logger.debug(jsonResponse.toString())
        } else {
          Logger.error("Error Calling Matchmaker: " + response.getAHCResponse.getResponseBody())
        }
    }

    val rs = Await.result(result, Duration.Inf)

    jsonResponse.as[List[MatchMakerResponse]]
  }

  def submitRepositorySelection(curationId: UUID) = PermissionAction(Permission.EditStagingArea, Some(ResourceRef(ResourceRef.curationObject, curationId)))(parse.multipartFormData) {
    implicit request =>
      implicit val user = request.user
      user match {
        case Some(usr) => {
          curations.get(curationId) match {
            case Some(cOld) => {
              val c = cOld.copy(datasets = datasets.get(cOld.datasets(0).id).toList)
              val repository = request.body.asFormUrlEncoded.getOrElse("repository", null)
              val purpose = request.body.asFormUrlEncoded.getOrElse("purpose", null)
              curations.updateRepository(c.id, repository(0))
              val mmResp = callMatchmaker(c, user).filter(_.orgidentifier == repository(0))
              if (purpose != null) {
                val userPreferences: Map[String, String] = Map("Purpose" -> purpose(0))
                userService.updateRepositoryPreferences(usr.id, userPreferences)
              }
              var repPreferences = usr.repositoryPreferences.map { value => value._1 -> value._2.toString().split(",").toList }
              val isTrial = spaces.get(c.space) match {
                case None => true
                case Some(s) => s.isTrial
              }
              if (isTrial) {
                repPreferences = repPreferences ++ Map("Purpose" -> List("Testing-Only"))
              }
              Ok(views.html.spaces.curationDetailReport(c, mmResp(0), repository(0), repPreferences))
            }
            case None => InternalServerError(spaceTitle + " not found")
          }
        }
        case None => InternalServerError("User Not Found")
      }
  }

  def compareToRepository(curationId: UUID, repository: String) = PermissionAction(Permission.EditStagingArea, Some(ResourceRef(ResourceRef.curationObject, curationId))) {
    implicit request =>
      implicit val user = request.user
      user match {
        case Some(usr) => {
          curations.get(curationId) match {
            case Some(c) => {
              curations.updateRepository(c.id, repository)
              val mmResp = callMatchmaker(c, user).filter(_.orgidentifier == repository)
              val repPreferences = usr.repositoryPreferences.map { value => value._1 -> value._2.toString().split(",").toList }
              Ok(views.html.spaces.curationDetailReport(c, mmResp(0), repository, repPreferences))
            }
            case None => InternalServerError("Publication Request not found")
          }
        }
        case None => InternalServerError("User not found")
      }

  }

  def sendToRepository(curationId: UUID) = PermissionAction(Permission.EditStagingArea, Some(ResourceRef(ResourceRef.curationObject, curationId))) {
    implicit request =>
      implicit val user = request.user

      curations.get(curationId) match {
        case Some(c) =>
          var success = false
          var repository: String = ""
          c.repository match {
            case Some(s) => repository = s
            case None => Ok(views.html.spaces.curationSubmitted(c, "No Repository Provided", success))
          }
          val key = play.api.Play.configuration.getString("commKey").getOrElse("")
          val https = controllers.Utils.https(request)
          val hostUrl = api.routes.CurationObjects.getCurationObjectOre(c.id).absoluteURL(https) + "?key=" + key
          val dsLicense = c.datasets(0).licenseData.m_licenseType match {
            case "license1" => "All Rights Reserved " + c.datasets(0).author.fullName
            case "license2" => "http://creativecommons.org/licenses/by-nc-nd/3.0/"
            case "license3" => "http://creativecommons.org/publicdomain/zero/1.0/"
          }
          val userPrefMap = userService.findById(c.author.id).map(usr => usr.repositoryPreferences.map(pref => if (pref._1 != "Purpose") { pref._1 -> Json.toJson(pref._2.toString().split(",").toList) } else { pref._1 -> Json.toJson(pref._2.toString()) })).getOrElse(Map.empty)
          var userPreferences = userPrefMap ++ Map("License" -> Json.toJson(dsLicense))
          user.map(usr => usr.profile match {
            case Some(prof) => prof.institution match {
              case Some(institution) => userPreferences += ("Affiliations" -> Json.toJson(institution))
              case None =>
            }
            case None =>
          })
          val fileIds = curations.getAllCurationFileIds(c.id)
          val files = curations.getCurationFiles(fileIds)
          val maxDataset = if (!files.isEmpty) files.map(_.length).max else 0
          val totalSize = if (!files.isEmpty) files.map(_.length).sum else 0
          var metadataList = scala.collection.mutable.ListBuffer.empty[MetadataPair]
          var metadataKeys = Set.empty[String]
          metadatas.getMetadataByAttachTo(ResourceRef(ResourceRef.curationObject, c.id)).filter(_.creator.typeOfAgent == "cat:user").map {
            item =>
              for ((key, value) <- buildMetadataMap(item.content)) {
                metadataList += MetadataPair(key, value)
                metadataKeys += key
              }
          }
          var metadataJson = scala.collection.mutable.Map.empty[String, JsValue]
          for (key <- metadataKeys) {
            metadataJson = metadataJson ++ Map(key -> Json.toJson(metadataList.filter(_.label == key).map { item => item.content }toList))
          }
          val creator = Json.toJson(userService.findById(c.author.id).map(usr => usr.profile match {
            case Some(prof) => prof.orcidID match {
              case Some(oid) => oid
              case None => api.routes.Users.findById(usr.id).absoluteURL(https)
            }
            case None => api.routes.Users.findById(usr.id).absoluteURL(https)

          }))
          if (metadataJson.contains("Abstract")) {
            val value = List(c.description) ++ metadataList.filter(_.label == "Abstract").map { item => item.content.as[String] }
            metadataJson = metadataJson ++ Map("Abstract" -> Json.toJson(value))
          } else {
            metadataJson = metadataJson ++ Map("Abstract" -> Json.toJson(c.description))
          }
          val metadataToAdd = metadataJson.toMap
          val metadataDefsMap = scala.collection.mutable.Map.empty[String, JsValue]
          for (md <- metadatas.getDefinitions(Some(c.space))) {
            metadataDefsMap((md.json \ "label").asOpt[String].getOrElse("").toString()) = Json.toJson((md.json \ "uri").asOpt[String].getOrElse(""))
          }
          if (!metadataDefsMap.contains("Abstract")) {
            metadataDefsMap("Abstract") = Json.toJson("http://purl.org/dc/terms/abstract")
          }
          val format = new java.text.SimpleDateFormat("dd-MM-yyyy")
          val spaceName = spaces.get(c.space) match {
            case Some(space) => space.name
            case None => AppConfiguration.getDisplayName
          }
          var aggregation = metadataToAdd ++
            Map(
              "Identifier" -> Json.toJson("urn:uuid:" + curationId),
              "@id" -> Json.toJson(hostUrl),
              "@type" -> Json.toJson("Aggregation"),
              "Title" -> Json.toJson(c.name),
              "Uploaded By" -> Json.toJson(creator),
              "Publishing Project" -> Json.toJson(controllers.routes.Spaces.getSpace(c.space).absoluteURL(https)),
              "Publishing Project Name" -> Json.toJson(spaceName),
              "Creation Date" -> Json.toJson(format.format(c.created)))
          if (metadataJson.contains("Creator")) {
            val value = c.creators ++ metadataList.filter(_.label == "Creator").map { item => item.content.as[String] }.toList
            aggregation = aggregation ++ Map("Creator" -> Json.toJson(value))
          } else {
            aggregation = aggregation ++ Map("Creator" -> Json.toJson(c.creators))
          }
          if (!metadataDefsMap.contains("Creator")) {
            metadataDefsMap("Creator") = Json.toJson(Map("@id" -> "http://purl.org/dc/terms/creator", "@container" -> "@list"))
          }

          val rightsholder = user.map(usr => usr.profile match {
            case Some(prof) => prof.orcidID match {
              case Some(oid) => oid
              case None => api.routes.Users.findById(usr.id).absoluteURL(https)
            }
            case None => api.routes.Users.findById(usr.id).absoluteURL(https)

          })
          val license = c.datasets(0).licenseData.m_licenseText
          val valuetoSend = Json.toJson(
            Map(
              "@context" -> Json.toJson(Seq(
                Json.toJson("https://w3id.org/ore/context"),
                Json.toJson(metadataDefsMap.toMap ++
                  Map(
                    "Identifier" -> Json.toJson("http://purl.org/dc/elements/1.1/identifier"),
                    "Aggregation Statistics" -> Json.toJson("http://sead-data.net/terms/publicationstatistics"),
                    "Data Mimetypes" -> Json.toJson("http://purl.org/dc/elements/1.1/format"),
                    "Affiliations" -> Json.toJson("http://sead-data.net/terms/affiliations"),
                    "Preferences" -> Json.toJson("http://sead-data.net/terms/publicationpreferences"),
                    "Max Collection Depth" -> Json.toJson("http://sead-data.net/terms/maxcollectiondepth"),
                    "Total Size" -> Json.toJson("tag:tupeloproject.org,2006:/2.0/files/length"),
                    "Max Dataset Size" -> Json.toJson("http://sead-data.net/terms/maxdatasetsize"),
                    "Uploaded By" -> Json.toJson("http://purl.org/dc/elements/1.1/creator"),
                    "Repository" -> Json.toJson("http://sead-data.net/terms/requestedrepository"),
                    "Aggregation" -> Json.toJson("http://sead-data.net/terms/aggregation"),
                    "Title" -> Json.toJson("http://purl.org/dc/elements/1.1/title"),
                    "Abstract" -> Json.toJson("http://purl.org/dc/terms/abstract"),
                    "Number of Datasets" -> Json.toJson("http://sead-data.net/terms/datasetcount"),
                    "Number of Collections" -> Json.toJson("http://sead-data.net/terms/collectioncount"),
                    "Publication Callback" -> Json.toJson("http://sead-data.net/terms/publicationcallback"),
                    "Environment Key" -> Json.toJson("http://sead-data.net/terms/environmentkey"),
                    "Bearer Token" -> Json.toJson("http:sead-data.net/vocab/rfc6750/bearer-token"),
                    "Access" -> Json.toJson("http://sead-data.net/terms/access"),
                    "License" -> Json.toJson("http://purl.org/dc/terms/license"),
                    "Rights Holder" -> Json.toJson("http://purl.org/dc/terms/rightsHolder"),
                    "Cost" -> Json.toJson("http://sead-data.net/terms/cost"),
                    "Dataset Description" -> Json.toJson("http://sead-data.net/terms/datasetdescription"),
                    "Purpose" -> Json.toJson("http://sead-data.net/vocab/publishing#Purpose"),
                    "Publishing Project" -> Json.toJson("http://sead-data.net/terms/publishingProject"),
                    "Publishing Project Name" -> Json.toJson("http://sead-data.net/terms/publishingProjectName"),
                    "Creation Date" -> Json.toJson("http://purl.org/dc/terms/created"))))),
              "Repository" -> Json.toJson(repository),
              "Preferences" -> Json.toJson(
                userPreferences),
              "Aggregation" -> Json.toJson(aggregation),
              "Aggregation Statistics" -> Json.toJson(
                Map(
                  "Max Collection Depth" -> Json.toJson(curations.maxCollectionDepth(c).toString()),
                  "Data Mimetypes" -> Json.toJson(files.map(_.contentType).toSet),
                  "Max Dataset Size" -> Json.toJson(maxDataset.toString),
                  "Total Size" -> Json.toJson(totalSize.toString),
                  "Number of Datasets" -> Json.toJson(fileIds.length),
                  "Number of Collections" -> Json.toJson(c.datasets.length))),
              "Rights Holder" -> Json.toJson(rightsholder),
              "Publication Callback" -> Json.toJson(api.routes.CurationObjects.savePublishedObject(c.id).absoluteURL(https) + "?key=" + key),
              "Environment Key" -> Json.toJson(play.api.Play.configuration.getString("commKey").getOrElse(""))))
          Logger.debug("Submitting request for publication: " + valuetoSend)

          implicit val context = scala.concurrent.ExecutionContext.Implicits.global
          val endpoint = play.Play.application().configuration().getString("stagingarea.uri").replaceAll("/$", "")
          val futureResponse = WS.url(endpoint).post(valuetoSend)
          var jsonResponse: play.api.libs.json.JsValue = new JsArray()
          val result = futureResponse.map {
            case response =>
              if (response.status >= 200 && response.status < 300 || response.status == 304) {
                curations.setSubmitted(c.id)
                jsonResponse = response.json
                success = true
              } else {

                Logger.error("Error Submitting to Repository: " + response.getAHCResponse.getResponseBody())
              }
          }

          val rs = Await.result(result, Duration.Inf)

          Ok(views.html.spaces.curationSubmitted(c, repository, success))
      }
  }

  /**
   * Endpoint for getting status from repository.
   */
  def getStatusFromRepository(id: UUID) = Action.async { implicit request =>
    implicit val context = scala.concurrent.ExecutionContext.Implicits.global
    curations.get(id) match {

      case Some(c) => {

        val endpoint = play.Play.application().configuration().getString("stagingarea.uri").replaceAll("/$", "") + "/urn:uuid:" + id.toString()
        Logger.debug(endpoint)
        val futureResponse = WS.url(endpoint).get()

        futureResponse.map {
          case response =>
            if (response.status >= 200 && response.status < 300 || response.status == 304) {
              (response.json \ "Status").asOpt[JsValue]
              Ok(response.json)
            } else {
              Logger.error("Error Getting Status: " + response.getAHCResponse.getResponseBody)
              InternalServerError(toJson("Status object not found."))
            }
        }
      }
      case None => Future(InternalServerError(toJson("Publication Request not found.")))
    }
  }

  def buildMetadataMap(content: JsValue): Map[String, JsValue] = {
    var out = scala.collection.mutable.Map.empty[String, JsValue]
    content match {
      case o: JsObject => {
        for ((key, value) <- o.fields) {
          value match {
            case o: JsObject => value match {
              case b: JsArray => out(key) = Json.toJson(buildMetadataMap(value))
              case b: JsString => out(key) = Json.toJson(b.value)
              case _ => out(key) = value
            }
            case o: JsArray => value match {
              case b: JsArray => out(key) = Json.toJson(buildMetadataMap(value))
              case b: JsString => out(key) = Json.toJson(b.value)
              case _ => out(key) = value
            }
            case _ => value match {
              case b: JsArray => out(key) = Json.toJson(buildMetadataMap(value))
              case b: JsString => out(key) = Json.toJson(b.value)
              case _ => out(key) = value
            }
          }
        }
      }
      case a: JsArray => {
        for ((value, i) <- a.value.zipWithIndex) {
          out = out ++ buildMetadataMap(value)
        }
      }

    }

    out.toMap
  }

  def getPublishedData(space: String) = UserAction(needActive = false) { implicit request =>
    implicit val user = request.user
    implicit val context = scala.concurrent.ExecutionContext.Implicits.global

    val endpoint = play.Play.application().configuration().getString("publishData.list.uri").replaceAll("/$", "")
    Logger.debug(endpoint)
    val futureResponse = WS.url(endpoint).get()
    var publishDataList: List[Map[String, String]] = List.empty
    /*
    val result = futureResponse.map {
      case response =>
        if (response.status >= 200 && response.status < 300 || response.status == 304) {
          val rawDataList = response.json.as[List[JsValue]]
          rawDataList.reverse
        } else {
          Logger.error("Error Getting published data: " + response.getAHCResponse.getResponseBody)
          List.empty
        }
    }

    val rs = Await.result(result, Duration.Inf)
*/
    Ok(views.html.curations.publishedData(Publications.getPublications(space, spaces), play.Play.application().configuration().getString("SEADservices.uri")))

  }

 
}

