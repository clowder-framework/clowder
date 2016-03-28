package controllers

import java.util.Date
import javax.inject.Inject
import api.Permission
import models._
import play.api.Logger
import play.api.libs.json._
import play.api.libs.json.Json._
import play.api.libs.json.JsArray
import services._
import _root_.util.RequiredFieldsConfig
import play.api.Play._
import scala.collection.mutable.ListBuffer
import scala.concurrent.Future
import scala.concurrent.Await
import play.api.mvc.{Request, Action, Results}
import play.api.libs.ws._
import scala.concurrent.duration._
import play.api.libs.json.Reads._
import java.net.{URL, URI}

/**
 * Methods for interacting with the curation objects in the staging area.
 */
class CurationObjects @Inject()(
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
  contextService: ContextLDService) extends SecuredController {

  def newCO(datasetId:UUID, spaceId: String) = PermissionAction(Permission.EditDataset, Some(ResourceRef(ResourceRef.dataset, datasetId))) { implicit request =>
    implicit val user = request.user
    val (name, desc, spaceByDataset) = datasets.get(datasetId) match {
      case Some(dataset) => (dataset.name, dataset.description, dataset.spaces.map(id => spaces.get(id)).flatten
        .filter (space => Permission.checkPermission(Permission.EditStagingArea, ResourceRef(ResourceRef.space, space.id))))
      case None => ("", "", List.empty)
    }
    //default space is the space from which user access to the dataset
    val defaultspace = spaceId match {
      case "" => {
        if(spaceByDataset.length ==1) {
          spaceByDataset.lift(0)
        } else {
          None
        }
      }
      case _ => spaces.get(UUID(spaceId))
    }

    Ok(views.html.curations.newCuration(datasetId, name, desc, defaultspace, spaceByDataset, RequiredFieldsConfig.isNameRequired,
      true, true))
  }

  /**
   * Controller flow to create a new curation object. On success,
   * the browser is redirected to the new Curation page.
   */
  def submit(datasetId:UUID, spaceId:UUID) = PermissionAction(Permission.EditStagingArea, Some(ResourceRef(ResourceRef.space, spaceId))) (parse.multipartFormData)  { implicit request =>

    //get name, des, space from request
    val COName = request.body.asFormUrlEncoded.getOrElse("name", null)
    val CODesc = request.body.asFormUrlEncoded.getOrElse("description", null)

    implicit val user = request.user
    user match {
      case Some(identity) => {

        datasets.get(datasetId) match {
          case Some(dataset) => {
            if (spaces.get(spaceId) != None) {

              //copy file list from FileDAO. and save curation file metadata. metadataCount is 0 since
              // metadatas.getMetadataByAttachTo will increase metadataCount
              var newFiles: List[UUID]= List.empty
              for ( fileId <- dataset.files) {
                files.get(fileId) match {
                  case Some(f) => {
                    val cf = CurationFile(fileId = f.id, path= f.path, author = f.author, filename = f.filename, uploadDate = f.uploadDate,
                      contentType = f.contentType, length = f.length, showPreviews = f.showPreviews, sections = f.sections, previews = f.previews, tags = f.tags,
                      thumbnail_id = f.thumbnail_id, metadataCount = 0, licenseData = f.licenseData, sha512 = f.sha512)
                    curations.insertFile(cf)
                    newFiles = cf.id :: newFiles
                    metadatas.getMetadataByAttachTo(ResourceRef(ResourceRef.file, f.id)).map(m => metadatas.addMetadata(m.copy(id = UUID.generate(), attachedTo = ResourceRef(ResourceRef.curationFile, cf.id))))
                  }
                }
              }

              //the model of CO have multiple datasets and collections, here we insert a list containing one dataset
              val newCuration = CurationObject(
                name = COName(0),
                author = identity,
                description = CODesc(0),
                created = new Date,
                submittedDate = None,
                publishedDate= None,
                space = spaceId,
                datasets = List(dataset),
                files = newFiles,
                folders = List.empty,
                repository = None,
                status = "In Curation"
              )

              // insert curation
              Logger.debug("create curation object: " + newCuration.id)
              curations.insert(newCuration)

              metadatas.getMetadataByAttachTo(ResourceRef(ResourceRef.dataset, dataset.id)).map{m =>
                val newm = m.copy(id = UUID.generate(), attachedTo = ResourceRef(ResourceRef.curationObject, newCuration.id))
                metadatas.addMetadata(newm)
              }
              //add description as abstract
              val userURI = controllers.routes.Application.index().absoluteURL() + "api/users/" + identity.id
              val context = Json.obj("Abstract" -> "http://purl.org/dc/terms/abstract")

              val newabstract = Metadata(attachedTo = ResourceRef(ResourceRef.curationObject, newCuration.id),
                contextId = context.asOpt[JsObject].map(contextService.addContext(new JsString("context name"), _)),
                createdAt = new Date(),
                creator =  UserAgent(identity.id, "cat:user", MiniUser(identity.id, identity.fullName, identity.avatarUrl.getOrElse(""), identity.email), Some(new URL(userURI))),
                content = Json.obj("Abstract" -> CODesc(0))
              )
              metadatas.addMetadata(newabstract)
              dataset.folders.map(f => copyFolders(f, newCuration.id, "dataset",  newCuration.id))

              Redirect(routes.CurationObjects.getCurationObject(newCuration.id))
            }
            else {
              InternalServerError("Space not found")
            }
          }
          case None => InternalServerError("Dataset Not found")
        }
      }
      case None => InternalServerError("User Not found")
    }
  }

  private def copyFolders(id:UUID, parentId: UUID, parentType:String, parentCurationObjectId:UUID):Unit = {
    folders.get(id) match {
      case Some(folder) =>{
        var newFiles: List[UUID]= List.empty
        for ( fileId <- folder.files) {
          files.get(fileId) match {
            case Some(f) => {
              val cf = CurationFile(fileId = f.id, path= f.path, author = f.author, filename = f.filename, uploadDate = f.uploadDate,
                contentType = f.contentType, length = f.length, showPreviews = f.showPreviews, sections = f.sections, previews = f.previews, tags = f.tags,
                thumbnail_id = f.thumbnail_id, metadataCount = 0, licenseData = f.licenseData, sha512 = f.sha512)
              curations.insertFile(cf)
              newFiles = cf.id :: newFiles
              metadatas.getMetadataByAttachTo(ResourceRef(ResourceRef.file, f.id))
                .map(m => metadatas.addMetadata(m.copy(id = UUID.generate(), attachedTo = ResourceRef(ResourceRef.curationFile, cf.id))))
            }
          }
        }

        val newCurationFolder = CurationFolder(
          folderId = id,
          author = folder.author,
          created = folder.created,
          name =folder.name,
          displayName = folder.displayName,
          files = newFiles,
          folders = List.empty,
          parentId = parentId,
          parentType = parentType.toLowerCase(),
          parentCurationObjectId = parentCurationObjectId
        )
        curations.insertFolder(newCurationFolder)
        curations.addCurationFolder(parentType, parentId, newCurationFolder.id)

        folder.folders.map(f => copyFolders(f,newCurationFolder.id, "folder", parentCurationObjectId ))
      }
      case None => {
        Logger.error("Curation Folder Not found")

      }
    }
  }

  def editCuration(id: UUID) = PermissionAction(Permission.EditStagingArea, Some(ResourceRef(ResourceRef.curationObject, id))) {
    implicit request =>
      implicit val user = request.user
      curations.get(id) match {
        case Some(c) =>
          val (name, desc, spaceByDataset, defaultspace) = (c.name, c.description, c.datasets.head.spaces.map(id => spaces.get(id)).flatten
            .filter(space => Permission.checkPermission(Permission.EditStagingArea, ResourceRef(ResourceRef.space, space.id))), spaces.get(c.space))

          Ok(views.html.curations.newCuration(id, name, desc, defaultspace, spaceByDataset, RequiredFieldsConfig.isNameRequired,
            true, false))

        case None => InternalServerError("Curation Object Not found")
      }
  }

  def updateCuration(id: UUID, spaceId:UUID) = PermissionAction(Permission.EditStagingArea, Some(ResourceRef(ResourceRef.curationObject, id))) (parse.multipartFormData) {
    implicit request =>
      implicit val user = request.user
      user match {
        case Some(identity) => {
          curations.get(id) match {
            case Some(c) => {
              val COName = request.body.asFormUrlEncoded.getOrElse("name", null)
              val CODesc = request.body.asFormUrlEncoded.getOrElse("description", null)
              curations.getAbstract(id) match{
                case Some(ab) => metadatas.removeMetadata(ab)
                case None =>
              }
              curations.updateInformation(id, CODesc(0), COName(0), c.space, spaceId)

              val userURI = controllers.routes.Application.index().absoluteURL() + "api/users/" + identity.id
              val context = Json.obj("Abstract" -> "http://purl.org/dc/terms/abstract")

              val newabstract = Metadata(attachedTo = ResourceRef(ResourceRef.curationObject, id),
                contextId = context.asOpt[JsObject].map(contextService.addContext(new JsString("context name"), _)),
                createdAt = new Date(),
                creator =  UserAgent(identity.id, "cat:user", MiniUser(identity.id, identity.fullName, identity.avatarUrl.getOrElse(""), identity.email), Some(new URL(userURI))),
                content = Json.obj("Abstract" -> CODesc(0))
              )
              metadatas.addMetadata(newabstract)

              events.addObjectEvent(user, id, COName(0), "update_curation_information")

              Redirect(routes.CurationObjects.getCurationObject(id))
            }
            case None => InternalServerError("Curation Object Not found")
          }
        }
        case None => InternalServerError("User Not found")
      }
  }

  /**
   * Delete curation object.
   */
  def deleteCuration(id: UUID) = PermissionAction(Permission.EditStagingArea, Some(ResourceRef(ResourceRef.curationObject, id))) {
    implicit request =>
      implicit val user = request.user

      curations.get(id) match {
        case Some(c) => {
          Logger.debug("delete Curation object: " + c.id)
          val spaceId = c.space

          curations.remove(id)
          //spaces.get(spaceId) is checked in Space.stagingArea
          Redirect(routes.Spaces.stagingArea(spaceId))
        }
        case None => InternalServerError("Curation Object Not found")
      }
  }



  def getCurationObject(curationId: UUID, limit: Int) = PermissionAction(Permission.EditStagingArea, Some(ResourceRef(ResourceRef.curationObject, curationId))) {    implicit request =>
    implicit val user = request.user
    curations.get(curationId) match {
      case Some(c) => {
        // metadata of curation files are getting from getUpdatedFilesAndFolders
        val requiredAbstract = curations.getAbstract(curationId).getOrElse(UUID(""))
        val m = metadatas.getMetadataByAttachTo(ResourceRef(ResourceRef.curationObject, c.id)).filterNot(m => m.id == requiredAbstract)
        val isRDFExportEnabled = current.plugin[RDFExportService].isDefined
        val fileByDataset = curations.getCurationFiles(curations.getAllCurationFileIds(c.id))
        if (c.status != "In Curation") {

          Ok(views.html.spaces.submittedCurationObject(c, fileByDataset, m, limit ))
        } else {
          Ok(views.html.spaces.curationObject(c, m , isRDFExportEnabled, limit))
        }
      }
      case None => InternalServerError("Curation Object Not found")
    }
  }

  def getUpdatedFilesAndFolders(curationId: UUID, curationFolderId: String, limit: Int, pageIndex:Int) = PermissionAction(Permission.EditStagingArea, Some(ResourceRef(ResourceRef.curationObject, curationId))) { implicit request =>
    implicit val user = request.user
    val filepageUpdate = if (pageIndex < 0) 0 else pageIndex
    curations.get(curationId) match {
      case Some(c) => {
        curationFolderId match{
        // curationFolderId is set to "None" if it is currently on curation page
          case "None" =>{
            val foldersList = c.folders.reverse.slice(limit * filepageUpdate, limit * (filepageUpdate+1)).map(f => curations.getCurationFolder(f)).flatten
            val limitFileIds : List[UUID] = c.files.reverse.slice(limit * filepageUpdate - c.folders.length, limit * (filepageUpdate+1) - c.folders.length)
            val limitFileList : List[CurationFile]=  curations.getCurationFiles( limitFileIds)
            val mCurationFile = c.files.map(f => metadatas.getMetadataByAttachTo(ResourceRef(ResourceRef.curationFile, f))).flatten

            val folderHierarchy = new ListBuffer[CurationFolder]()
            val next = c.files.length + c.folders.length > limit * (filepageUpdate+1)
            Ok(views.html.curations.filesAndFolders(c, None, foldersList, folderHierarchy.reverse.toList, pageIndex, next, limitFileList.toList, mCurationFile))
          }
          // Otherwise it is on a curation folder's page
          case _ => {
            curations.getCurationFolder (UUID(curationFolderId)) match {
              case Some (cf) => {
                val foldersList = cf.folders.reverse.slice(limit * filepageUpdate, limit * (filepageUpdate+1)).map(f => curations.getCurationFolder(f)).flatten
                val limitFileIds : List[UUID] = cf.files.reverse.slice(limit * filepageUpdate - cf.folders.length, limit * (filepageUpdate+1) - cf.folders.length)
                val limitFileList : List[CurationFile]= curations.getCurationFiles(limitFileIds)
                val mCurationFile = limitFileIds.map(f => metadatas.getMetadataByAttachTo(ResourceRef(ResourceRef.curationFile, f))).flatten
                var folderHierarchy = new ListBuffer[CurationFolder]()
                folderHierarchy += cf
                var f1: CurationFolder = cf
                while(f1.parentType == "folder") {
                  curations.getCurationFolder(f1.parentId) match {
                    case Some(fparent) => {
                      folderHierarchy += fparent
                      f1 = fparent
                    }
                    case None =>
                  }
                }
                val next = cf.files.length + cf.folders.length > limit * (filepageUpdate+1)
                Ok(views.html.curations.filesAndFolders(c, Some(cf.id.stringify), foldersList, folderHierarchy.reverse.toList, pageIndex, next, limitFileList.toList, mCurationFile))
              }
              case None => InternalServerError ("Curation Folder Not found")
            }
          }

        }

      }
      case None => InternalServerError("Curation Object Not found")
    }
  }

  def findMatchingRepositories(curationId: UUID) = PermissionAction(Permission.EditStagingArea, Some(ResourceRef(ResourceRef.curationObject, curationId))) {
    implicit request =>
      implicit val user = request.user
          curations.get(curationId) match {
            case Some(c) => {
              val propertiesMap: Map[String, List[String]] = Map("Purpose" -> List("Testing-Only"))
              val mmResp = callMatchmaker(c, user)(request)
              user match {
                case Some(usr) => {
                  val repPreferences = usr.repositoryPreferences.map{ value => value._1 -> value._2.toString().split(",").toList}
                  Ok(views.html.spaces.matchmakerResult(c, propertiesMap, repPreferences, mmResp))
                }
                case None =>Results.Redirect(routes.Error.authenticationRequiredMessage("You must be logged in to perform that action.", request.uri ))
              }
            }
            case None => InternalServerError("Curation Object not found")
          }
  }

  def callMatchmaker(c: CurationObject, user: Option[User])(implicit request: Request[Any]): List[MatchMakerResponse] = {
    val https = controllers.Utils.https(request)
    val hostUrl = api.routes.CurationObjects.getCurationObjectOre(c.id).absoluteURL(https) + "#aggregation"
    val userPrefMap = userService.findByIdentity(c.author).map(usr => usr.repositoryPreferences.map( pref => if(pref._1 != "Purpose") { pref._1-> Json.toJson(pref._2.toString().split(",").toList)} else {pref._1-> Json.toJson(pref._2.toString())})).getOrElse(Map.empty)
    var userPreferences = userPrefMap + ("Repository" -> Json.toJson(c.repository))
    user.map ( usr => usr.profile match {
      case Some(prof) => prof.institution match {
        case Some(institution) => userPreferences += ("Affiliations" -> Json.toJson(institution))
        case None =>
      }
      case None =>
    })
    val fileIds = curations.getAllCurationFileIds(c.id)
    val files = curations.getCurationFiles(fileIds)
    val maxDataset = if (!fileIds.isEmpty)  files.map(_.length).max else 0
    val totalSize = if (!fileIds.isEmpty) files.map(_.length).sum else 0
    var metadataList = scala.collection.mutable.ListBuffer.empty[MetadataPair]
    var metadataKeys = Set.empty[String]
    metadatas.getMetadataByAttachTo(ResourceRef(ResourceRef.curationObject, c.id)).filter(_.creator.typeOfAgent == "cat:user").map {
      item =>
        for((key, value) <- buildMetadataMap(item.content)) {
          metadataList += MetadataPair(key, value)
          metadataKeys += key
        }
    }
    var metadataJson = scala.collection.mutable.Map.empty[String, JsValue]
    for(key <- metadataKeys) {
      metadataJson = metadataJson ++ Map(key -> Json.toJson(metadataList.filter(_.label == key).map{item => item.content}toList))
    }
    val metadataDefsMap = scala.collection.mutable.Map.empty[String, JsValue]
    for(md <- metadatas.getDefinitions()) {
      metadataDefsMap((md.json\ "label").asOpt[String].getOrElse("").toString()) = Json.toJson((md.json \ "uri").asOpt[String].getOrElse(""))
    }

    val creator = userService.findByIdentity(c.author).map ( usr => usr.profile match {
      case Some(prof) => prof.orcidID match {
        case Some(oid) => oid
        case None => api.routes.Users.findById(usr.id).absoluteURL(https)
      }
        case None => api.routes.Users.findById(usr.id).absoluteURL(https)

    })
    val rightsholder = user.map ( usr => usr.profile match {
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
      "Publishing Project"-> Json.toJson(controllers.routes.Spaces.getSpace(c.space).absoluteURL(https)),
      "Creation Date" -> Json.toJson(format.format(c.created))
      )
    if(!metadataJson.contains("Creator")) {
      aggregation = aggregation ++ Map("Creator" -> Json.toJson(creator))
    }
    if(!metadataDefsMap.contains("Creator")){
      metadataDefsMap("Creator") = Json.toJson("http://purl.org/dc/terms/creator")
    }
    val valuetoSend = Json.obj(
      "@context" -> Json.toJson(Seq(
        Json.toJson("https://w3id.org/ore/context"),
        Json.toJson(metadataDefsMap.toMap ++ Map (
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
                "Altitude" -> Json.toJson("http://www.w3.org/2003/01/geo/wgs84_pos#alt")

              ))
      )))),
      "Rights Holder" -> Json.toJson(rightsholder),
      "Aggregation" ->
        Json.toJson(aggregation),
      "Preferences" -> userPreferences ,
      "Aggregation Statistics" ->
        Map(
          "Data Mimetypes" -> Json.toJson(files.map(_.contentType).toSet),
          "Max Collection Depth" -> Json.toJson("0"),
          "Max Dataset Size" -> Json.toJson(maxDataset.toString),
          "Total Size" -> Json.toJson(totalSize.toString),
          "Number of Datasets" -> Json.toJson(fileIds.length),
          "Number of Collections" -> Json.toJson(c.datasets.length)
        )
    )
    implicit val context = scala.concurrent.ExecutionContext.Implicits.global
    val endpoint = play.Play.application().configuration().getString("matchmaker.uri").replaceAll("/$","")
    val futureResponse = WS.url(endpoint).post(valuetoSend)
    Logger.debug("Value to send matchmaker: " + valuetoSend)
    var jsonResponse: play.api.libs.json.JsValue = new JsArray()
    val result = futureResponse.map {
      case response =>
        if(response.status >= 200 && response.status < 300 || response.status == 304) {
          jsonResponse = response.json
          Logger.debug(jsonResponse.toString())
        }
        else {
          Logger.error("Error Calling Matchmaker: " + response.getAHCResponse.getResponseBody())
        }
    }

    val rs = Await.result(result, Duration.Inf)

    jsonResponse.as[List[MatchMakerResponse]]
  }

  def compareToRepository(curationId: UUID, repository: String) = PermissionAction(Permission.EditStagingArea, Some(ResourceRef(ResourceRef.curationObject, curationId))) {
    implicit request =>
      implicit val user = request.user

       curations.get(curationId) match {
         case Some(c) => {
           curations.updateRepository(c.id, repository)
           val mmResp = callMatchmaker(c, user).filter(_.orgidentifier == repository)

           Ok(views.html.spaces.curationDetailReport( c, mmResp(0), repository))
        }
        case None => InternalServerError("Space not found")
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
            case Some (s) => repository = s
            case None => Ok(views.html.spaces.curationSubmitted( c, "No Repository Provided", success))
          }
          val key = play.api.Play.configuration.getString("commKey").getOrElse("")
          val https = controllers.Utils.https(request)
          val hostUrl = api.routes.CurationObjects.getCurationObjectOre(c.id).absoluteURL(https) + "?key=" + key
          val userPrefMap = userService.findByIdentity(c.author).map(usr => usr.repositoryPreferences.map( pref => if(pref._1 != "Purpose") { pref._1-> Json.toJson(pref._2.toString().split(",").toList)} else {pref._1-> Json.toJson(pref._2.toString())})).getOrElse(Map.empty)
          var userPreferences = userPrefMap
          user.map ( usr => usr.profile match {
            case Some(prof) => prof.institution match {
              case Some(institution) => userPreferences += ("Affiliations" -> Json.toJson(institution))
              case None =>
            }
            case None =>
          })
          val fileIds = curations.getAllCurationFileIds(c.id)
          val files = curations.getCurationFiles(fileIds)
          val maxDataset = if (!files.isEmpty)  files.map(_.length).max else 0
          val totalSize = if (!files.isEmpty) files.map(_.length).sum else 0
          var metadataList = scala.collection.mutable.ListBuffer.empty[MetadataPair]
          var metadataKeys = Set.empty[String]
          metadatas.getMetadataByAttachTo(ResourceRef(ResourceRef.curationObject, c.id)).filter(_.creator.typeOfAgent == "cat:user").map {
            item =>
              for((key, value) <- buildMetadataMap(item.content)) {
                metadataList += MetadataPair(key, value)
                metadataKeys += key
              }
          }
          var metadataJson = scala.collection.mutable.Map.empty[String, JsValue]
          for(key <- metadataKeys) {
            metadataJson = metadataJson ++ Map(key -> Json.toJson(metadataList.filter(_.label == key).map{item => item.content}toList))
          }
          val creator = Json.toJson(userService.findByIdentity(c.author).map ( usr => usr.profile match {
            case Some(prof) => prof.orcidID match {
              case Some(oid) => oid
              case None =>  api.routes.Users.findById(usr.id).absoluteURL(https)
            }
            case None =>  api.routes.Users.findById(usr.id).absoluteURL(https)

          }))
          var metadataToAdd = metadataJson.toMap
          if(metadataJson.toMap.get("Abstract") == None) {
            metadataToAdd = metadataJson.toMap.+("Abstract" -> Json.toJson(c.description))
          }
          val metadataDefsMap = scala.collection.mutable.Map.empty[String, JsValue]
          for(md <- metadatas.getDefinitions()) {
            metadataDefsMap((md.json\ "label").asOpt[String].getOrElse("").toString()) = Json.toJson((md.json \ "uri").asOpt[String].getOrElse(""))
          }
          val format = new java.text.SimpleDateFormat("dd-MM-yyyy")
          var aggregation = metadataToAdd ++
            Map(
              "Identifier" -> Json.toJson("urn:uuid:"+curationId),
              "@id" -> Json.toJson(hostUrl),
              "@type" -> Json.toJson("Aggregation"),
              "Title" -> Json.toJson(c.name),
              "Uploaded By" -> Json.toJson(creator),
              "Publishing Project"-> Json.toJson(controllers.routes.Spaces.getSpace(c.space).absoluteURL(https)),
              "Creation Date" -> Json.toJson(format.format(c.created))
            )
          if(!metadataToAdd.contains("Creator")) {
            aggregation = aggregation ++ Map("Creator" -> Json.toJson(creator))
          }
          if(!metadataDefsMap.contains("Creator")){
            metadataDefsMap("Creator") = Json.toJson("http://purl.org/dc/terms/creator")
          }
          val rightsholder = user.map ( usr => usr.profile match {
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
                    "Creation Date" -> Json.toJson("http://purl.org/dc/terms/created")
                )
              ))),
                "Repository" -> Json.toJson(repository.toLowerCase()),
                "Preferences" -> Json.toJson(
                  userPreferences
                ),
                "Aggregation" -> Json.toJson(aggregation),
                "Aggregation Statistics" -> Json.toJson(
                  Map(
                    "Max Collection Depth" -> Json.toJson("0"),
                    "Data Mimetypes" -> Json.toJson(files.map(_.contentType).toSet),
                    "Max Dataset Size" -> Json.toJson(maxDataset.toString),
                    "Total Size" -> Json.toJson(totalSize.toString),
                    "Number of Datasets" -> Json.toJson(fileIds.length),
                    "Number of Collections" -> Json.toJson(c.datasets.length)
                  )),
                "Rights Holder" -> Json.toJson(rightsholder),
                "Publication Callback" -> Json.toJson(controllers.routes.CurationObjects.savePublishedObject(c.id).absoluteURL(https) +"?key=" + key),
                "Environment Key" -> Json.toJson(play.api.Play.configuration.getString("commKey").getOrElse("")),
                "License" -> Json.toJson(license)
              )
            )
          Logger.debug("Submitting request for publication: " + valuetoSend)

          implicit val context = scala.concurrent.ExecutionContext.Implicits.global
          val endpoint =play.Play.application().configuration().getString("stagingarea.uri").replaceAll("/$","")
          val futureResponse = WS.url(endpoint).post(valuetoSend)
          var jsonResponse: play.api.libs.json.JsValue = new JsArray()
          val result = futureResponse.map {
            case response =>
              if(response.status >= 200 && response.status < 300 || response.status == 304) {
                curations.setSubmitted(c.id)
                jsonResponse = response.json
                success = true
              }
              else {

                Logger.error("Error Submitting to Repository: " + response.getAHCResponse.getResponseBody())
              }
          }

          val rs = Await.result(result, Duration.Inf)

          Ok(views.html.spaces.curationSubmitted( c, repository, success))
      }
  }

  /**
   * Endpoint for receiving status/ uri from repository.
   */
  def savePublishedObject(id: UUID) = UserAction(needActive = true) (parse.json) {
    implicit request =>
      Logger.debug("get infomation from repository")

      curations.get(id) match {

        case Some(c) => {
          c.status match {

            case "In Curation" => BadRequest(toJson(Map("status" -> "ERROR", "message" -> "Curation object hasn't been submitted yet.")))
            //sead2 receives status once from repository,
            case "Published" | "ERROR" | "Reject" => BadRequest(toJson(Map("status" -> "ERROR", "message" -> "Curation object already received status from repository.")))
            case "Submitted" => {
              //parse status from request's body
              val statusList = (request.body \ "status").asOpt[String]

              statusList.size match {
                case 0 => {
                  if ((request.body \ "uri").asOpt[String].isEmpty) {
                    BadRequest(toJson(Map("status" -> "ERROR", "message" -> "Receive empty request.")))
                  } else {
                    (request.body \ "uri").asOpt[String].map {
                      externalIdentifier => {
                        //set published when uri is provided
                        curations.setPublished(id)
                        if (externalIdentifier.startsWith("doi:") || externalIdentifier.startsWith("10.")) {
                          val DOI_PREFIX = "http://dx.doi.org/"
                          curations.updateExternalIdentifier(id, new URI(DOI_PREFIX + externalIdentifier.replaceAll("^doi:", "")))
                        } else {
                          curations.updateExternalIdentifier(id, new URI(externalIdentifier))
                        }
                      }
                    }
                    Ok(toJson(Map("status" -> "OK")))
                  }
                }
                case 1 => {
                  statusList.map {
                    status =>
                      if (status.compareToIgnoreCase("Published") == 0 || status.compareToIgnoreCase("Publish") == 0) {
                        curations.setPublished(id)
                      } else {
                        //other status except Published, such as ERROR, Rejected
                        curations.updateStatus(id, status)
                      }
                  }

                  (request.body \ "uri").asOpt[String].map {
                    externalIdentifier => {
                      if (externalIdentifier.startsWith("doi:") || externalIdentifier.startsWith("10.")) {
                        val DOI_PREFIX = "http://dx.doi.org/"
                        curations.updateExternalIdentifier(id, new URI(DOI_PREFIX + externalIdentifier.replaceAll("^doi:", "")))
                      } else {
                        curations.updateExternalIdentifier(id, new URI(externalIdentifier))
                      }
                    }
                  }
                  Ok(toJson(Map("status" -> "OK")))
                }
                //multiple status
                case _ => BadRequest(toJson(Map("status" -> "ERROR", "message" -> "Curation object has unrecognized status .")))
              }

            }
          }
        }
        case None => BadRequest(toJson(Map("status" -> "ERROR", "message" -> "Curation object not found.")))
      }
  }


  /**
   * Endpoint for getting status from repository.
   */
  def getStatusFromRepository (id: UUID)  = Action.async { implicit request =>
    implicit val context = scala.concurrent.ExecutionContext.Implicits.global
    curations.get(id) match {

      case Some(c) => {

        val endpoint = play.Play.application().configuration().getString("stagingarea.uri").replaceAll("/$", "") + "/urn:uuid:" +id.toString()
        Logger.debug(endpoint)
        val futureResponse = WS.url(endpoint).get()

        futureResponse.map{
          case response =>
            if(response.status >= 200 && response.status < 300 || response.status == 304) {
              (response.json \ "Status").asOpt[JsValue]
              Ok(response.json)
            } else {
              Logger.error("Error Getting Status: " + response.getAHCResponse.getResponseBody)
              InternalServerError(toJson("Status object not found."))
            }
        }
      }
      case None => Future(InternalServerError(toJson("Curation object not found.")))
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
        for((value, i) <- a.value.zipWithIndex){
          out = out ++ buildMetadataMap(value)
        }
      }

    }

    out.toMap
  }
}

