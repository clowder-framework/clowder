package api

import java.io._
import java.io.{File => JFile}
import java.net.URL
import java.security.{DigestInputStream, MessageDigest}
import java.text.SimpleDateFormat
import java.util.{Calendar, Date}

import api.Permission.Permission
import java.util.zip._

import javax.inject.{Inject, Singleton}
import controllers.{Previewers, Utils}
import jsonutils.JsonUtil
import models._
import org.apache.commons.codec.binary.Hex
import org.json.JSONObject
import play.api.Logger
import play.api.Play.{configuration, current, routes}
import play.api.i18n.Messages
import play.api.libs.concurrent.Execution.Implicits._
import play.api.libs.iteratee.Enumerator
import play.api.libs.json._
import play.api.libs.json.Json._
import play.api.mvc.{Action, AnyContent, MultipartFormData, SimpleResult}
import services._
import _root_.util._
import controllers.Utils.https
import org.json.simple.{JSONArray, JSONObject => SimpleJSONObject}
import org.json.simple.parser.JSONParser
import play.api.libs.Files
import play.api.libs.Files.TemporaryFile
import scalax.file.Path.createTempFile

import scala.concurrent.{ExecutionContext, Future}
import scala.collection.mutable.{ListBuffer, Map => MutaMap}

/**
 * Dataset API.
 *
 */
@Singleton
class  Datasets @Inject()(
  datasets: DatasetService,
  files: FileService,
  collections: CollectionService,
  sections: SectionService,
  comments: CommentService,
  extractions: ExtractionService,
  metadataService: MetadataService,
  contextService: ContextLDService,
  rdfsparql: RdfSPARQLService,
  events: EventService,
  spaces: SpaceService,
  folders: FolderService,
  relations: RelationService,
  userService: UserService,
  thumbnailService : ThumbnailService,
  routing: ExtractorRoutingService,
  appConfig: AppConfigurationService,
  esqueue: ElasticsearchQueue,
  sinkService: EventSinkService) extends ApiController {

  lazy val chunksize = play.Play.application().configuration().getInt("clowder.chunksize", 1024*1024)

  def get(id: UUID) = PermissionAction(Permission.ViewDataset, Some(ResourceRef(ResourceRef.dataset, id))) { implicit request =>
    datasets.get(id) match {
      case Some(d) => Ok(toJson(d))
      case None => BadRequest(toJson(s"Could not find dataset with id [${id.stringify}]"))
    }
  }

  def list(title: Option[String], date: Option[String], limit: Int, exact: Boolean) = PrivateServerAction { implicit request =>
    Ok(toJson(listDatasets(title, date, limit, Set[Permission](Permission.ViewDataset), request.user, request.user.fold(false)(_.superAdminMode), exact)))
  }

  def listCanEdit(title: Option[String], date: Option[String], limit: Int, exact: Boolean) = PrivateServerAction { implicit request =>
      Ok(toJson(listDatasets(title, date, limit, Set[Permission](Permission.AddResourceToDataset, Permission.EditDataset), request.user, request.user.fold(false)(_.superAdminMode), exact)))
  }

  def listMoveFileToDataset(file_id: UUID, title: Option[String], limit: Int, exact: Boolean) = PrivateServerAction { implicit request =>
    if (play.Play.application().configuration().getBoolean("datasetFileWithinSpace")) {
      Ok(toJson(listDatasetsInSpace(file_id, title, limit, Set[Permission](Permission.AddResourceToDataset, Permission.EditDataset), request.user, request.user.fold(false)(_.superAdminMode), exact)))
    } else {
      Ok(toJson(listDatasets(title, None, limit, Set[Permission](Permission.AddResourceToDataset, Permission.EditDataset), request.user, request.user.fold(false)(_.superAdminMode), exact)))
    }
  }

  /**
    * Returns list of datasets based on space restrictions and permissions. The spaceId is obtained from the file itself
    */
  private def listDatasetsInSpace(file_id: UUID, title: Option[String], limit: Int, permission: Set[Permission], user: Option[User], superAdmin: Boolean, exact: Boolean) : List[Dataset] = {
    var datasetAll = List[Dataset]()
    val datasetList = datasets.findByFileIdDirectlyContain(file_id)
    datasetList match {
      case Nil => {
        val folderList = folders.findByFileId(file_id)
        folderList match {
          case f :: fs => {
            datasets.get(f.parentDatasetId) match {
              case Some(d) => {
                if (d.spaces.isEmpty) {
                  title match {
                    case Some(t) => {
                      datasetAll = datasets.listAccess(limit, t, permission, user, superAdmin, true,false, exact)
                    }
                    case None => {
                      datasetAll = datasets.listAccess(limit, permission, user, superAdmin, true,false)
                    }
                  }
                } else {
                  for (sid <- d.spaces) {
                    title match {
                      case Some(t) => {
                        //merge two lists, both with dataset objects from different spaces
                        datasetAll = datasetAll ++ datasets.listSpaceAccess(limit, t, permission, sid.toString(), user, superAdmin, true)
                      }
                      case None => {
                        datasetAll = datasetAll ++ datasets.listSpaceAccess(limit, permission, sid.toString(), user, superAdmin, true)
                      }
                    }
                  }
                }
              }
              case None =>
            }
          }
        }
      }
      case x :: xs => {
        if (x.spaces.isEmpty) {
          title match {
            case Some(t) => {
              datasetAll = datasets.listAccess(limit, t, permission, user, superAdmin, true,false, exact)
            }
            case None => {
              datasetAll = datasets.listAccess(limit, permission, user, superAdmin, true,false)
            }
          }
        } else {
          for (sid <- x.spaces) {
            title match {
              case Some(t) => {
                datasetAll = datasetAll ++ datasets.listSpaceAccess(limit, t, permission, sid.toString(), user, superAdmin, true)
              }
              case None => {
                datasetAll = datasetAll ++ datasets.listSpaceAccess(limit, permission, sid.toString(), user, superAdmin, true)
              }
            }
          }
        }
      }
    }
    datasetAll.distinct
  }

  /**
    * Returns list of datasets based on parameters and permissions.
    */
  private def listDatasets(title: Option[String], date: Option[String], limit: Int, permission: Set[Permission], user: Option[User], superAdmin: Boolean, exact: Boolean) : List[Dataset] = {
    (title, date) match {
      case (Some(t), Some(d)) => {
        datasets.listAccess(d, true, limit, t, permission, user, superAdmin, true,false, exact)
      }
      case (Some(t), None) => {
        datasets.listAccess(limit, t, permission, user, superAdmin, true,false, exact)
      }
      case (None, Some(d)) => {
        datasets.listAccess(d, true, limit, permission, user, superAdmin, true,false)
      }
      case (None, None) => {
        datasets.listAccess(limit, permission, user, superAdmin, true,false)
      }
    }
  }

  /**
    * List all datasets outside a collection.
    */
  def listOutsideCollection(collectionId: UUID) = PrivateServerAction { implicit request =>
    collections.get(collectionId) match {
      case Some(collection) => {
        val list = for (dataset <- datasets.listAccess(0, Set[Permission](Permission.ViewDataset), request.user, request.user.fold(false)(_.superAdminMode), false,false); if (!datasets.isInCollection(dataset, collection)))
          yield dataset
        Ok(toJson(list))
      }
      case None => {
        val list = datasets.listAccess(0, Set[Permission](Permission.ViewDataset), request.user, request.user.fold(false)(_.superAdminMode), false,false)
        Ok(toJson(list))
      }
    }
  }

  /**
    * Create new dataset. name, file_id are required, description and space,  are optional. If the space & file_id is wrong, refuse the request
    */
  def createDataset() = PermissionAction(Permission.CreateDataset)(parse.json) { implicit request =>
    Logger.debug("--- API Creating new dataset ----")
    (request.body \ "name").asOpt[String].map { name =>
      val description = (request.body \ "description").asOpt[String].getOrElse("")

      var d : Dataset = null
      implicit val user = request.user
      user match {
        case Some(identity) => {
          (request.body \ "space").asOpt[String] match {
            case None | Some("default") => d = Dataset(name=name,description=description, created=new Date(), author=identity, licenseData = License.fromAppConfig(), stats = new Statistics())
            case Some(spaceId) =>
              spaces.get(UUID(spaceId)) match {
                case Some(s) => d = Dataset(name=name,description=description, created=new Date(), author=identity, licenseData = License.fromAppConfig(), spaces = List(UUID(spaceId)), stats = new Statistics())
                case None => BadRequest(toJson("Bad space = " + spaceId))
              }
          }
        }
        case None => InternalServerError("User Not found")
      }
      appConfig.incrementCount('datasets, 1)

      //event will be added whether creation is success.
      events.addObjectEvent(request.user, d.id, d.name, EventType.CREATE_DATASET.toString)
      datasets.index(d.id)

      (request.body \ "file_id").asOpt[String] match {
        case Some(file_id) => {
          files.get(UUID(file_id)) match {
            case Some(file) =>
              datasets.insert(d) match {
                case Some(id) => {
                  d.spaces.map( spaceId => spaces.get(spaceId)).flatten.map{ s =>
                    spaces.addDataset(d.id, s.id)
                    events.addSourceEvent(request.user, d.id, d.name, s.id, s.name, EventType.ADD_DATASET_SPACE.toString)
                  }
                  attachExistingFileHelper(UUID(id), file.id, d, file, request.user)
                  files.index(UUID(file_id))
                  if (!file.xmlMetadata.isEmpty) {
                    val xmlToJSON = files.getXMLMetadataJSON(UUID(file_id))
                    datasets.addXMLMetadata(UUID(id), UUID(file_id), xmlToJSON)
                    current.plugin[ElasticsearchPlugin].foreach {
                      _.index(SearchUtils.getElasticsearchObject(d))
                    }
                  } else {
                    current.plugin[ElasticsearchPlugin].foreach {
                      _.index(SearchUtils.getElasticsearchObject(d))
                    }
                  }

                  current.plugin[AdminsNotifierPlugin].foreach {
                    _.sendAdminsNotification(Utils.baseUrl(request), "Dataset", "added", id, name)
                  }


                  Ok(toJson(Map("id" -> id)))
                }
                case None => Ok(toJson(Map("status" -> "error")))
              }
            case None => BadRequest(toJson("Bad file_id = " + file_id))

          }
        }
        case None => BadRequest(toJson("Missing parameter [file_id]"))
      }
    }.getOrElse(BadRequest(toJson("Missing parameter [name]")))
  }

  // Create a dataset by uploading a compliant BagIt archive
  def createFromBag() = PermissionAction(Permission.CreateDataset)(parse.multipartFormData) { implicit request: UserRequest[MultipartFormData[Files.TemporaryFile]] =>
    Logger.info("--- API Creating new dataset from zip archive ----")

    var mainXML: Option[ZipEntry] = None
    var dsInfo: Option[ZipEntry] = None   // dataset _info.json
    var dsMeta: Option[ZipEntry] = None   // dataset _metadata.json

    val fileInfos: MutaMap[String, ZipEntry] = MutaMap.empty  // filename -> file _info.json
    val fileBytes: MutaMap[String, ZipEntry] = MutaMap.empty  // filename -> actual file bytes
    val fileMetas: MutaMap[String, ZipEntry] = MutaMap.empty  // filename -> file _metadata.json
    val fileFolds: MutaMap[String, String] = MutaMap.empty    // filename -> folder (data/foo/bar/file.txt -> foo/bar/)
    val distinctFolders: ListBuffer[String] = ListBuffer.empty

    implicit val user = request.user

    var outcome: SimpleResult = BadRequest("Something went wrong.")
    user match {
      case Some(identity) => {
        request.body.files.foreach { f =>
          if (f.filename.endsWith(".zip")) {
            // TODO: Is it dangerous to blindly open uploaded zip file? Decompression bomb, malicious contents, etc?
            val bag = new ZipFile(f.ref.file)
            val entries = bag.entries // Need assignment to be explicit here or an infinite loop occurs

            // Check each file in the zip looking for known filenames
            while (entries.hasMoreElements()) {
              val entry = entries.nextElement()
              val entryName = entry.getName
              val inDataDir = entryName.startsWith("data/") || entryName.contains("/data/")
              entryName match {
                // TODO: case "metadata/clowder.xml" => mainXML = Some(entry) // overrides datacite.xml
                case path if path.endsWith("metadata/datacite.xml") => if (!mainXML.isDefined) mainXML = Some(entry)
                case path if inDataDir && path.endsWith("data/_dataset_metadata.json") => dsMeta = Some(entry)
                case path if inDataDir && path.endsWith("/_info.json") => dsInfo = Some(entry)
                case path if inDataDir && path.endsWith("_info.json") => {
                  val filename = path.split("/").last.replace("_info.json", "")
                  fileInfos += (filename -> entry)
                }
                case path if inDataDir && path.endsWith("_metadata.json") => {
                  val filename = path.split("/").last.replace("_metadata.json", "")
                  fileMetas += (filename -> entry)
                }
                case path if inDataDir && !path.endsWith("/") => {
                  val filename = path.split("/").last
                  if (filename != "data") {
                    val folderstart = if (path.startsWith("data/")) {5} else {path.indexOf("/data/")+6}
                    val foldername = path.substring(folderstart).replace(filename, "")
                    fileBytes += (filename -> entry)
                    fileFolds += (filename -> foldername)
                    distinctFolders.append(foldername)
                  }
                }
                case _ => {}
              }
            }

            /** LOGIC FLOW
             * prep data from the _info.json files
             * if any files are referenced rather than included, check links. any dead links = abort the upload
             * create dataset
             *   populate metadata from JSON
             * create each file
             *   download one by one if necessary to avoid staging too much data at once
             *   (if any download fails, try to undo what has been done to this point and stop upload)
             *   populate metadata from JSON
             */

            if (mainXML.isDefined) {
              // Create dataset itself
              val xmldata = scala.xml.XML.load(bag.getInputStream(mainXML.get))
              var dsName: Option[String] = None
              var dsDesc: Option[String] = None
              (xmldata \\ "title").foreach(node => dsName = Some(node.text))
              (xmldata \\ "description").foreach(node => dsDesc = Some(node.text))
              val dsCreators = (xmldata \\ "creatorName").map(node => node.text)

              if (dsName.isDefined) {
                val ds = Dataset(name=dsName.get, description=dsDesc.getOrElse(""), created=new Date(), author=identity,
                  licenseData=License.fromAppConfig(), stats=new Statistics(), creators=dsCreators.toList)
                datasets.insert(ds) match {
                  case Some(dsid) => {
                    // Add dataset metadata if necessary
                    if (dsMeta.isDefined) {
                      val mdStream = bag.getInputStream(dsMeta.get)
                      val datasetMeta = parseJsonFileInZip(mdStream)
                      try {
                        datasetMeta.asInstanceOf[JsArray].value.foreach(v => {
                          processBagMetadataJsonLD(ResourceRef(ResourceRef.dataset, UUID(dsid)), v)
                        })
                      } catch {
                        case e: Exception =>
                          processBagMetadataJsonLD(ResourceRef(ResourceRef.dataset, UUID(dsid)), datasetMeta)
                      }
                    }

                    // Add folders to dataset with proper nesting
                    var folderIds: MutaMap[String, String] = MutaMap.empty
                    distinctFolders.distinct.sorted.foreach(folderPath => {
                      if (!(folderPath == "" || folderPath == "/"))
                        folderIds = ensureFolderExistsInDataset(ds, folderPath, folderIds)
                    })

                    // Add files to corresponding folders (if any files fail, the whole upload should fail)
                    var criticalFail: Option[String] = None
                    val filenames = if (fileInfos.toList.length > 0) fileInfos.keys else fileBytes.keys
                    filenames.foreach(filename => {
                      if (criticalFail.isEmpty) {
                        fileBytes.get(filename) match {
                          case Some(bytes) => {

                            // Get file metadata and folder info
                            val folderId = fileFolds.get(filename) match {
                              case Some(fid) if (fid != "" && fid != "/") =>
                                folderIds.get(fid)
                              case _ => None
                            }

                            // Unzip file to temporary location
                            val is = bag.getInputStream(bytes)
                            val outfile = createTempFile(suffix=filename).jfile
                            val outstream = new FileOutputStream(outfile)
                            val buffer = new Array[Byte](1024)
                            var chunk = is.read(buffer);
                            while (chunk > 0) {
                              outstream.write(buffer, 0, chunk)
                              chunk = is.read(buffer)
                            }
                            outstream.close()

                            val newfile = FileUtils.processBagFile(outfile, filename, user.get, ds, folderId)
                            newfile match {
                              case Some(nf) => {
                                // Add file metadata if necessary
                                val fileMeta = fileMetas.get(filename)
                                if (fileMeta.isDefined) {
                                  val mdStream = bag.getInputStream(fileMeta.get)
                                  val fMeta = parseJsonFileInZip(mdStream)
                                  try {
                                    fMeta.asInstanceOf[JsArray].value.foreach(v => {
                                      processBagMetadataJsonLD(ResourceRef(ResourceRef.file, nf.id), v)
                                    })
                                  } catch {
                                    case e: Exception =>
                                      processBagMetadataJsonLD(ResourceRef(ResourceRef.file, nf.id), fMeta)
                                  }
                                }
                              }
                              case None => {
                                Logger.error("Problem creating "+filename+" - removing dataset.")
                                datasets.removeDataset(ds.id, request.host, request.apiKey, user)
                                criticalFail = Some("Not all files in zip file could be proceesed ("+filename+")")
                              }
                            }
                          }
                          case None => {
                            // TODO: Check if file has remote path to download it
                            criticalFail = Some("Not all files found in archive ("+filename+" missing)")
                          }
                        }
                      }
                    })

                    if (criticalFail.isDefined)
                      outcome = BadRequest(criticalFail.get)
                    else
                      outcome = Ok("Created dataset "+ds.id.stringify)
                  }
                  case None => outcome = BadRequest("Error creating new dataset")
                }
              } else outcome = BadRequest("No dataset info found in "+mainXML.get)
            } else outcome = BadRequest("No supported XML metadata file found.")
          }
        }
      }
      case None => outcome = BadRequest("Must provide an associated user account.")
    }
    outcome
  }

  // Add JSON data from a JSON-LD file into a dataset
  private def processBagMetadataJsonLD(resource: ResourceRef, json: JsValue) = {
    //parse request for agent/creator info
    json.validate[Agent] match {
      case s: JsSuccess[Agent] => {
        // TODO: This code is similar to addMetadataJsonLD() but skips RMQ message and event - combine them cleanly?
        val creator = s.get

        // check if the context is a URL to external endpoint
        val contextURL: Option[URL] = (json \ "@context").asOpt[String].map(new URL(_))

        // check if context is a JSON-LD document
        val contextID: Option[UUID] = (json \ "@context").asOpt[JsObject]
          .map(contextService.addContext(new JsString("context name"), _))

        // when the new metadata is added
        val createdAt = new Date()

        //parse the rest of the request to create a new models.Metadata object
        val attachedTo = resource
        val content = (json \ "content")
        val version = None
        val metadata = models.Metadata(UUID.generate, attachedTo, contextID, contextURL, createdAt, creator,
          content, version)
        metadataService.addMetadata(metadata)
      }
      case e: JsError => {
        Logger.error("Error getting creator");
      }
    }
  }

  // Open JSON data in a zipfile as a JsValue
  private def parseJsonFileInZip(is: InputStream): JsValue = {
    val reader = new BufferedReader(new InputStreamReader(is, "UTF-8"))
    var outstr = ""
    var line = reader.readLine
    while (line != null) {
      outstr += line
      line = reader.readLine
    }
    Json.parse(outstr)
  }

  // Recursively create folder levels in a dataset that is being created from bag
  private def ensureFolderExistsInDataset(dataset: Dataset, folderPath: String, folderIds: MutaMap[String, String]): MutaMap[String, String] = {
    var updatedFolderIds = folderIds
    folderIds.get(folderPath) match {
      case Some(_) => {}
      case None => {
        // Folder still needs to be created
        val folderLevels = folderPath.split('/')
        val baseFolder = folderLevels.last
        val parentFolder = folderLevels.take(folderLevels.length-1).mkString("/")

        if (parentFolder.trim.length == 0) {
          // Folder has no parent to add directly to dataset
          val f = Folder(author=dataset.author, created=new Date(), name=baseFolder, displayName=baseFolder,
            files=List.empty, folders=List.empty, parentId=dataset.id, parentType="dataset", parentDatasetId=dataset.id)
          folders.insert(f)
          datasets.addFolder(dataset.id, f.id)
          updatedFolderIds += (folderPath -> f.id.stringify)
        } else {
          // Folder has parent so add to parent folder
          updatedFolderIds = ensureFolderExistsInDataset(dataset, parentFolder, updatedFolderIds)
          val parentId = folderIds.get(parentFolder).get
          val f = Folder(author=dataset.author, created=new Date(), name=baseFolder, displayName=baseFolder,
            files=List.empty, folders=List.empty, parentId=UUID(parentId), parentType="folder", parentDatasetId=dataset.id)
          folders.insert(f)
          folders.addSubFolder(UUID(parentId), f.id)
        }
      }
    }
    updatedFolderIds
  }

  /**
    * Create new dataset with no file required. However if there are comma separated file IDs passed in, add all of those as existing
    * files. This is to facilitate multi-file-uploader usage for new files, as well as to allow multiple existing files to be
    * added as part of dataset creation.
    *
    * A JSON document is the payload for this endpoint. Required elements are name, description, and space. Optional element is
    * existingfiles, which will be a comma separated String of existing file IDs to be added to the new dataset.
    */
  def createEmptyDataset() = PermissionAction(Permission.CreateDataset)(parse.json) { implicit request =>
    (request.body \ "name").asOpt[String].map { name =>

      val description = (request.body \ "description").asOpt[String].getOrElse("")
      val access =
        if(play.Play.application().configuration().getBoolean("verifySpaces")){
           //verifySpaces == true && access set to trial if not specified otherwise
           (request.body \ "access").asOpt[String].getOrElse(DatasetStatus.TRIAL.toString)
        } else {
          (request.body \ "access").asOpt[String].getOrElse(DatasetStatus.DEFAULT.toString)
        }
        var d : Dataset = null
        implicit val user = request.user
        user match {
          case Some(identity) => {
            (request.body \ "space").asOpt[List[String]] match {
              case None | Some(List("default"))=> {
                d = Dataset(name = name, description = description, created = new Date(), author = identity, licenseData = License.fromAppConfig(), stats = new Statistics(), status = access)
              }

              case Some(space) => {
                var spaceList: List[UUID] = List.empty
                space.map {
                  aSpace => if (spaces.get(UUID(aSpace)).isDefined) {
                    spaceList = UUID(aSpace) :: spaceList

                  } else {
                    BadRequest(toJson("Bad space = " + aSpace))
                  }
                }
                d = Dataset(name = name, description = description, created = new Date(), author = identity, licenseData = License.fromAppConfig(), spaces = spaceList, stats = new Statistics(), status = access)
              }

            }
          }
          case None => InternalServerError("User Not found")
        }
        events.addObjectEvent(request.user, d.id, d.name, EventType.CREATE_DATASET.toString)

        datasets.insert(d) match {
          case Some(id) => {
            //In this case, the dataset has been created and inserted. Now notify the space service and check
            //for the presence of existing files.
            appConfig.incrementCount('datasets, 1)

            datasets.index(d.id)
            Logger.debug("About to call addDataset on spaces service")
            d.spaces.map( spaceId => spaces.get(spaceId)).flatten.map{ s =>
              spaces.addDataset(d.id, s.id)
              events.addSourceEvent(request.user, d.id, d.name, s.id, s.name, EventType.ADD_DATASET_SPACE.toString)

            }
            //Add this dataset to a collection if needed
            (request.body \ "collection").asOpt[List[String]] match {
              case None | Some(List("default"))=>
              case Some(collectionList) => {
                collectionList.map{c => collections.addDataset(UUID(c), d.id)}
              }
            }
            //Below call is not what is needed? That already does what we are doing in the Dataset constructor...
            //Items from space model still missing. New API will be needed to update it most likely.
            (request.body \ "existingfiles").asOpt[String].map { fileString =>
              var idArray = fileString.split(",").map(_.trim())
              datasets.get(UUID(id)) match {
                case Some(dataset) => {
                  val fileHits = files.get(idArray.map({fid => UUID(fid)}).toList)
                  if (fileHits.missing.length > 0)
                    BadRequest(toJson(Map("status" -> "Not all file IDs were found.",
                      "missing" -> fileHits.missing.mkString(","))))
                  else {
                    fileHits.found.foreach(file => {
                      attachExistingFileHelper(dataset.id, file.id, dataset, file, request.user)
                    })
                    Ok(toJson(Map("status" -> "success")))
                  }
                }
                case None => {
                  Logger.error("Error getting dataset" + id)
                  BadRequest(toJson(s"The given dataset id $id is not a valid ObjectId."))
                }
              }

              Ok(toJson(Map("id" -> id)))
            }.getOrElse(Ok(toJson(Map("id" -> id))))
          }
          case None => Ok(toJson(Map("status" -> "error")))
        }
    }.getOrElse(BadRequest(toJson("Missing parameter [name]")))
  }

  /**
    * Create new dataset with no file required. However if there are comma separated file IDs passed in, add all of those as existing
    * files. This is to facilitate multi-file-uploader usage for new files, as well as to allow multiple existing files to be
    * added as part of dataset creation.
    *
    * A JSON document is the payload for this endpoint. Required elements are name and description. Optional element is existingfiles,
    * which will be a comma separated String of existing file IDs to be added to the new dataset.
    */
  def attachMultipleFiles() = PermissionAction(Permission.AddResourceToDataset)(parse.json) { implicit request =>
    (request.body \ "datasetid").asOpt[String].map { dsId =>
      (request.body \ "existingfiles").asOpt[String].map { fileString =>
        val idArray = fileString.split(",").map(_.trim())

          datasets.get(UUID(dsId)) match {
            case Some(dataset) => {
              val fileHits = files.get(idArray.map(UUID(_)).toList)
              if (fileHits.missing.length > 0)
                BadRequest(toJson(Map("status" -> "Not all file IDs were found.",
                  "missing" -> fileHits.missing.mkString(","))))
              else {
                fileHits.found.foreach(file => {
                  attachExistingFileHelper(dataset.id, file.id, dataset, file, request.user)
                })
                Ok(toJson(Map("status" -> "success")))
              }
            }
            case None => {
              Logger.error("Error getting dataset" + dsId)
              BadRequest(toJson(s"The given dataset id $dsId is not a valid ObjectId."))
            }
          }

        Ok(toJson(Map("id" -> dsId)))
      }.getOrElse(BadRequest(toJson("Missing parameter [existingfiles]")))
    }.getOrElse(BadRequest(toJson("Missing parameter [datasetid]")))
  }

  /**
    * Reindex the given dataset, if recursive is set to true it will
    * also reindex all files in that dataset.
    */
  def reindex(id: UUID, recursive: Boolean) = PermissionAction(Permission.CreateDataset, Some(ResourceRef(ResourceRef.dataset, id))) { implicit request =>
    datasets.get(id) match {
      case Some(ds) => {
        val success = esqueue.queue("index_dataset", new ResourceRef('dataset, id), new ElasticsearchParameters(recursive=recursive))
        if (success) Ok(toJson(Map("status" -> "reindex successfully queued")))
        else BadRequest(toJson(Map("status" -> "reindex queuing failed, Elasticsearch may be disabled")))
      }
      case None => {
        Logger.error("Error getting dataset" + id)
        BadRequest(toJson(s"The given dataset id $id is not a valid ObjectId."))
      }
    }
  }

  /**
    * Functionality broken out from attachExistingFile, in order to allow the core work of file attachment to be called from
    * multiple API endpoints.
    *
    * @param dsId A UUID that specifies the dataset that will be modified
    * @param fileId A UUID that specifies the file to attach to the dataset
    * @param dataset Reference to the model of the dataset that is specified
    * @param file Reference to the model of the file that is specified
    */
  def attachExistingFileHelper(dsId: UUID, fileId: UUID, dataset: Dataset, file: models.File, user: Option[User]) = {
    if (!files.isInDataset(file, dataset)) {
      datasets.addFile(dsId, file)
      events.addSourceEvent(user , file.id, file.filename, dataset.id, dataset.name, "attach_file_dataset")
      files.index(fileId)
      if (!file.xmlMetadata.isEmpty){
        datasets.index(dsId)
      }

      if(dataset.thumbnail_id.isEmpty && !file.thumbnail_id.isEmpty){
        datasets.updateThumbnail(dataset.id, UUID(file.thumbnail_id.get))

        collections.get(dataset.collections).found.foreach(collection => {
          if(collection.thumbnail_id.isEmpty){
            collections.updateThumbnail(collection.id, UUID(file.thumbnail_id.get))
          }
        })
      }

      //add file to RDF triple store if triple store is used
      if (file.filename.endsWith(".xml")) {
        configuration.getString("userdfSPARQLStore").getOrElse("no") match {
          case "yes" => rdfsparql.linkFileToDataset(fileId, dsId)
          case _ => Logger.trace("Skipping RDF store. userdfSPARQLStore not enabled in configuration file")
        }
      }
      Logger.debug("----- Adding file to dataset completed")
    } else {
        Logger.debug("File was already in dataset.")
    }
  }

  def attachExistingFile(dsId: UUID, fileId: UUID) = PermissionAction(Permission.AddResourceToDataset, Some(ResourceRef(ResourceRef.dataset, dsId))) { implicit request =>
    datasets.get(dsId) match {
      case Some(dataset) => {
        files.get(fileId) match {
          case Some(file) => {
            attachExistingFileHelper(dsId, fileId, dataset, file, request.user)
            Ok(toJson(Map("status" -> "success")))
          }
          case None => {
            Logger.error("Error getting file" + fileId)
            BadRequest(toJson(s"The given dataset id $dsId is not a valid ObjectId."))
          }
        }
      }
      case None => {
        Logger.error("Error getting dataset" + dsId)
        BadRequest(toJson(s"The given dataset id $dsId is not a valid ObjectId."))
      }
    }
  }

  def detachFile(datasetId: UUID, fileId: UUID, ignoreNotFound: String) = PermissionAction(Permission.CreateDataset, Some(ResourceRef(ResourceRef.dataset, datasetId))) { implicit request =>
    datasets.get(datasetId) match{
      case Some(dataset) => {
        detachFileHelper(datasetId, fileId, dataset, request.user)
      }
      case None => {
        ignoreNotFound match {
          case "True" => Ok(toJson(Map("status" -> "success")))
          case "False" => Logger.error(s"Error getting dataset $datasetId"); InternalServerError
        }
      }
    }
  }

  /**
    * Utility function to consolidate the utility portions of the detach file functionality
    * so that it can be easily called from multiple API operations.
    *
    * @param datasetId The id of the dataset that a file is being detached from
    * @param fileId The id of the file to detach from the dataset
    * @param dataset The reference to the model of the dataset being operated on
    *
    */
  def detachFileHelper(datasetId: UUID, fileId: UUID, dataset: models.Dataset, user: Option[User]) = {
    files.get(fileId) match {
      case Some(file) => {
        if(files.isInDataset(file, dataset)){
          //remove file from dataset
          datasets.removeFile(dataset.id, file.id)
          events.addSourceEvent(user , file.id, file.filename, dataset.id, dataset.name, "detach_file_dataset")
          files.index(fileId)
          if (!file.xmlMetadata.isEmpty)
            datasets.index(datasetId)

          Logger.debug("----- Removing a file from dataset completed")

          if(!dataset.thumbnail_id.isEmpty && !file.thumbnail_id.isEmpty){
            if(dataset.thumbnail_id.get == file.thumbnail_id.get){
              datasets.createThumbnail(dataset.id)

              collections.get(dataset.collections).found.foreach(collection => {
                if(!collection.thumbnail_id.isEmpty){
                  if(collection.thumbnail_id.get == dataset.thumbnail_id.get){
                    collections.createThumbnail(collection.id)
                  }
                }
              })
            }
          }

          //remove link between dataset and file from RDF triple store if triple store is used
          if (file.filename.endsWith(".xml")) {
            configuration.getString("userdfSPARQLStore").getOrElse("no") match {
              case "yes" => rdfsparql.detachFileFromDataset(fileId, datasetId)
              case _ => Logger.trace("Skipping RDF store. userdfSPARQLStore not enabled in configuration file")
            }
          }
        }
        else  Logger.debug("----- File was already out of the dataset.")
        Ok(toJson(Map("status" -> "success")))
      }
      case None => {
        Logger.debug("----- detach helper NONE case")
        Ok(toJson(Map("status" -> "success")))
      }
    }
  }

  def moveFileBetweenDatasets(datasetId: UUID, toDatasetId: UUID, fileId: UUID) = PermissionAction(Permission.AddResourceToDataset, Some(ResourceRef(ResourceRef.dataset, datasetId))) { implicit request =>
    datasets.get(datasetId) match {
      case Some (dataset) => {
        datasets.get (toDatasetId) match {
          case Some (toDataset) => {
            files.get (fileId) match {
              case Some (file) => {
                attachExistingFileHelper (toDatasetId, fileId, toDataset, file, request.user)
                detachFileHelper(datasetId, fileId, dataset, request.user)
                Logger.debug ("----- Successfully moved File between datasets.")
                Ok (toJson (Map ("status" -> "success") ) )
              }
              case None => {
                Logger.error ("Error getting file" + fileId)
                BadRequest (toJson (s"The given file id $fileId is not a valid ObjectId.") )
              }
            }
          }
          case None => {
            Logger.error ("Error getting dataset" + toDatasetId)
            BadRequest (toJson (s"The given dataset id $toDatasetId is not a valid ObjectId.") )
          }
        }
      }
      case None => {
        Logger.error ("Error getting dataset" + datasetId)
        BadRequest (toJson (s"The given dataset id $datasetId is not a valid ObjectId.") )
      }
    }
  }
  //////////////////

  def listInCollection(collectionId: UUID) = PermissionAction(Permission.ViewCollection, Some(ResourceRef(ResourceRef.collection, collectionId))) { implicit request =>
    Ok(toJson(datasets.listCollection(collectionId.stringify, request.user)))
  }

  def addMetadata(id: UUID) = PermissionAction(Permission.AddMetadata, Some(ResourceRef(ResourceRef.dataset, id)))(parse.json) { implicit request =>
    Logger.debug(s"Adding metadata to dataset $id")

    datasets.get(id) match {
      case Some(x) => {
        val json = request.body
        //parse request for agent/creator info
        //creator can be UserAgent or ExtractorAgent
        val creator = ExtractorAgent(id = UUID.generate(),
          extractorId = Some(new URL("http://clowder.ncsa.illinois.edu/extractors/deprecatedapi")))

        // check if the context is a URL to external endpoint
        val contextURL: Option[URL] = None

        // check if context is a JSON-LD document
        val contextID: Option[UUID] = None

        // when the new metadata is added
        val createdAt = new Date()

        //parse the rest of the request to create a new models.Metadata object
        val attachedTo = ResourceRef(ResourceRef.dataset, id)
        val version = None
        val metadata = models.Metadata(UUID.generate, attachedTo, contextID, contextURL, createdAt, creator,
          json, version)

        //add metadata to mongo
        val metadataId = metadataService.addMetadata(metadata)
        val mdMap = metadata.getExtractionSummary

        //send RabbitMQ message
        routing.metadataAddedToResource(metadataId, metadata.attachedTo, mdMap, Utils.baseUrl(request), request.apiKey, request.user)

        events.addObjectEvent(request.user, id, x.name, EventType.ADD_METADATA_DATASET.toString)

        datasets.index(id)
        Ok(toJson(Map("status" -> "success")))
      }
      case None => Logger.error(s"Error getting dataset $id"); NotFound
    }
  }

  /**
    * Add metadata in JSON-LD format.
    */
  def addMetadataJsonLD(id: UUID) =
     PermissionAction(Permission.AddMetadata, Some(ResourceRef(ResourceRef.dataset, id)))(parse.json) { implicit request =>

        datasets.get(id) match {
          case Some(x) => {
            val json = request.body
            //parse request for agent/creator info
            json.validate[Agent] match {
              case s: JsSuccess[Agent] => {
                val creator = s.get

                // check if the context is a URL to external endpoint
                val contextURL: Option[URL] = (json \ "@context").asOpt[String].map(new URL(_))

                // check if context is a JSON-LD document
                val contextID: Option[UUID] = (json \ "@context").asOpt[JsObject]
                  .map(contextService.addContext(new JsString("context name"), _))

                // when the new metadata is added
                val createdAt = new Date()

                //parse the rest of the request to create a new models.Metadata object
                val attachedTo = ResourceRef(ResourceRef.dataset, id)
                val content = (json \ "content")
                val version = None
                val metadata = models.Metadata(UUID.generate, attachedTo, contextID, contextURL, createdAt, creator,
                  content, version)

                //add metadata to mongo
                val metadataId = metadataService.addMetadata(metadata)
                val mdMap = metadata.getExtractionSummary

                //send RabbitMQ message
                routing.metadataAddedToResource(metadataId, metadata.attachedTo, mdMap, Utils.baseUrl(request), request.apiKey, request.user)

                events.addObjectEvent(request.user, id, x.name, EventType.ADD_METADATA_DATASET.toString)

                datasets.index(id)
                Ok(toJson("Metadata successfully added to db"))
              }
              case e: JsError => {
                Logger.error("Error getting creator");
                BadRequest(toJson(s"Creator data is missing or incorrect."))
              }
            }
          }
          case None => Logger.error(s"Error getting dataset $id"); NotFound
        }
     }

  def getMetadataDefinitions(id: UUID, currentSpace: Option[String]) = PermissionAction(Permission.AddMetadata, Some(ResourceRef(ResourceRef.dataset, id))) { implicit request =>
    implicit val user = request.user
    datasets.get(id) match {
      case Some(dataset) => {
        val metadataDefinitions = collection.mutable.HashSet[models.MetadataDefinition]()
        var spacesToCheck = List.empty[UUID]
        currentSpace match {
          case Some(spaceId) => {
            spaces.get(UUID(spaceId)) match {
              case Some(space) => spacesToCheck = List(space.id)
              case None => spacesToCheck = dataset.spaces
            }
          }
          case None => {
            spacesToCheck = dataset.spaces
          }
        }
        spacesToCheck.foreach { spaceId =>
          spaces.get(spaceId) match {
            case Some(space) => metadataService.getDefinitions(Some(space.id)).foreach{definition => metadataDefinitions += definition}
            case None =>
          }
        }
        if(dataset.spaces.length == 0) {
          metadataService.getDefinitions().foreach{definition => metadataDefinitions += definition}
        }
        Ok(toJson(metadataDefinitions.toList.sortWith( _.json.\("label").asOpt[String].getOrElse("") < _.json.\("label").asOpt[String].getOrElse("") )))
      }
      case None => BadRequest(toJson("The requested dataset does not exist"))
    }
  }

  def getMetadataJsonLD(id: UUID, extFilter: Option[String]) = PermissionAction(Permission.ViewMetadata, Some(ResourceRef(ResourceRef.dataset, id))) { implicit request =>
    val (baseUrlExcludingContext, isHttps) = RequestUtils.getBaseUrlAndProtocol(request, false)
    datasets.get(id) match {
      case Some(dataset) => {
        //get metadata and also fetch context information
        val listOfMetadata = extFilter match {
          case Some(f) => metadataService.getExtractedMetadataByAttachTo(ResourceRef(ResourceRef.dataset, id), f)
                                    .map(JSONLD.jsonMetadataWithContext(_, baseUrlExcludingContext, isHttps))
          case None => metadataService.getMetadataByAttachTo(ResourceRef(ResourceRef.dataset, id))
                                    .map(JSONLD.jsonMetadataWithContext(_, baseUrlExcludingContext, isHttps))
        }
        Ok(toJson(listOfMetadata))
      }
      case None => {
        Logger.error("Error getting dataset  " + id);
        BadRequest(toJson("Error getting dataset  " + id))
      }
    }
  }

  def removeMetadataJsonLD(id: UUID, extractorId: Option[String]) =
    PermissionAction(Permission.DeleteMetadata, Some(ResourceRef(ResourceRef.dataset, id))) { implicit request =>
    datasets.get(id) match {
      case Some(dataset) => {
        val metadataIds = extractorId match {
          case Some(f) => metadataService.removeMetadataByAttachToAndExtractor(ResourceRef(ResourceRef.dataset, id), f,
            Utils.baseUrl(request), request.apiKey, request.user)
          case None => metadataService.removeMetadataByAttachTo(ResourceRef(ResourceRef.dataset, id),
            Utils.baseUrl(request), request.apiKey, request.user)
        }

        // send extractor message after attached to resource
        metadataIds.foreach { mId =>
          routing.metadataRemovedFromResource(mId, ResourceRef(ResourceRef.dataset, id), Utils.baseUrl(request), request.apiKey, request.user)
        }

        Ok(toJson(Map("status" -> "success", "count" -> metadataIds.size.toString)))
      }
      case None => {
        Logger.error("Error getting dataset  " + id)
        BadRequest(toJson("Error getting dataset  " + id))
      }
    }
  }

  def addUserMetadata(id: UUID) = PermissionAction(Permission.AddMetadata, Some(ResourceRef(ResourceRef.dataset, id)))(parse.json) { implicit request =>
    implicit val user = request.user
    Logger.debug(s"Adding user metadata to dataset $id")
    datasets.addUserMetadata(id, Json.stringify(request.body))

    datasets.get(id) match {
      case Some(dataset) => {
        events.addObjectEvent(user, id, dataset.name, EventType.ADD_METADATA_DATASET.toString)
      }
    }

    datasets.index(id)
    configuration.getString("userdfSPARQLStore").getOrElse("no") match {
      case "yes" => datasets.setUserMetadataWasModified(id, true)
      case _ => Logger.debug("userdfSPARQLStore not enabled")
    }
    Ok(toJson(Map("status" -> "success")))
  }

  def datasetFilesGetIdByDatasetAndFilename(datasetId: UUID, filename: String): Option[String] = {
    datasets.get(datasetId) match {
      case Some(dataset) => {
        files.get(dataset.files).found.foreach(file => {
          if (file.filename.equals(filename))
            return Some(file.id.toString)
        })
        None
      }
      case None => Logger.error(s"Error getting dataset $datasetId."); None
    }
  }

  def datasetFilesList(id: UUID, count: Boolean = false) = PermissionAction(Permission.ViewDataset, Some(ResourceRef(ResourceRef.dataset, id))) { implicit request =>
    datasets.get(id) match {
      case Some(dataset) => {
        if (count == true) {
          Ok(toJson(dataset.files.length))
        } else {
          val list: List[JsValue]= files.get(dataset.files).found.map(file => {
            val serveradmin = request.user match {
              case Some(u) => (u.status==UserStatus.Admin)
              case None => false
            }
            jsonFile(file, serveradmin)
          })
          Ok(toJson(list))
        }
      }
      case None => Logger.error("Error getting dataset" + id); InternalServerError
    }
  }

  /**
    * List all files withing a dataset and its nested folders.
    *
    * @param id dataset id
    * @param max max number of files to return, default is
    * @return
    */
  def datasetAllFilesList(id: UUID, max: Int = -1) = PermissionAction(Permission.ViewDataset, Some(ResourceRef(ResourceRef.dataset, id))) { implicit request =>
    datasets.get(id) match {
      case Some(dataset) => {
        val serveradmin = request.user match {
          case Some(u) => (u.status==UserStatus.Admin)
          case None => false
        }
        val listFiles = new ListBuffer[JsValue]()
        var resultCount = 0

        files.get(dataset.files).found.foreach(f => {
          // Only get file from database if below max requested value
          if (max < 0 || resultCount < max) {
            listFiles += jsonFile(f, serveradmin)
            resultCount += 1
          }
        })

        // Only get more files from folders if max size isn't reached
        val list = if (max < 0 || resultCount < max) {
          listFiles.toList ++ getFilesWithinFolders(id, serveradmin, max-resultCount)
        } else {
          listFiles.toList
        }
        Ok(toJson(list))
      }
      case None => Logger.error("Error getting dataset" + id); InternalServerError
    }
  }

  def uploadToDatasetFile(dataset_id: UUID) = PermissionAction(Permission.AddResourceToDataset, Some(ResourceRef(ResourceRef.dataset, dataset_id)))(parse.multipartFormData) { implicit request =>
    datasets.get(dataset_id) match {
      case Some(dataset) => {
        val uploadedFiles = FileUtils.uploadFilesMultipart(request, Some(dataset), apiKey=request.apiKey)
        uploadedFiles.length match {
          case 0 => BadRequest("No files uploaded")
          case 1 => Ok(Json.obj("id" -> uploadedFiles.head.id))
          case _ => Ok(Json.obj("ids" -> uploadedFiles.toList))
        }
      }
      case None => {
        BadRequest(s"Dataset with id=${dataset_id} does not exist")
      }
    }
  }

  def uploadToDatasetJSON(dataset_id: UUID) = PermissionAction(Permission.AddResourceToDataset, Some(ResourceRef(ResourceRef.dataset, dataset_id)))(parse.json) { implicit request =>
    datasets.get(dataset_id) match {
      case Some(dataset) => {
        val uploadedFiles = FileUtils.uploadFilesJSON(request, Some(dataset), apiKey=request.apiKey)
        uploadedFiles.length match {
          case 0 => BadRequest("No files uploaded")
          case 1 => Ok(Json.obj("id" -> uploadedFiles.head.id))
          case _ => Ok(Json.obj("ids" -> uploadedFiles.toList))
        }
      }
      case None => {
        BadRequest(s"Dataset with id=${dataset_id} does not exist")
      }
    }
  }

  private def getFilesWithinFolders(id: UUID, serveradmin: Boolean=false, max: Int = -1): List[JsValue] = {
    val output = new ListBuffer[JsValue]()
    var resultCount = 0
    datasets.get(id) match {
      case Some(dataset) => {
        folders.findByParentDatasetId(id).map { folder =>
          files.get(folder.files).found.foreach(file => {
            if (max < 0 || resultCount < max) {
              output += jsonFile(file, serveradmin)
              resultCount += 1
            }
          })
        }
      }
      case None => Logger.error(s"Error getting dataset $id")
    }
    output.toList
  }

  def jsonFile(file: models.File, serverAdmin: Boolean = false): JsValue = {
    val defaultMap = Map(
      "id" -> file.id.toString,
      "filename" -> file.filename,
      "contentType" -> file.contentType,
      "date-created" -> file.uploadDate.toString(),
      "size" -> file.length.toString)

    // Only include filepath if using DiskByte storage and user is serverAdmin
    val jsonMap = file.loader match {
      case "services.filesystem.DiskByteStorageService" => {
        if (serverAdmin)
          Map(
            "id" -> file.id.toString,
            "filename" -> file.filename,
            "filepath" -> file.loader_id,
            "contentType" -> file.contentType,
            "date-created" -> file.uploadDate.toString(),
            "size" -> file.length.toString)
        else
          defaultMap
      }
      case _ => defaultMap
    }
    toJson(jsonMap)
  }

  //Update Dataset Information code starts

  /**
    * REST endpoint: POST: update the administrative information associated with a specific Dataset
    *
    *  Takes one arg, id:
    *
    *  id, the UUID associated with this dataset
    *
    *  The data contained in the request body will contain data to be updated associated by the following String key-value pairs:
    *
    *  description -> The text for the updated description for the dataset
    *  name -> The text for the updated name for this dataset
    *
    *  Currently description and owner are the only fields that can be modified, however this api is extensible enough to add other existing
    *  fields, or new fields, in the future.
    *
    */
  def updateInformation(id: UUID) = PermissionAction(Permission.EditDataset, Some(ResourceRef(ResourceRef.dataset, id)))(parse.json) { implicit request =>
    implicit val user = request.user
    if (UUID.isValid(id.stringify)) {

      //Set up the vars we are looking for
      var description: String = null;
      var name: String = null;

      var aResult: JsResult[String] = (request.body \ "description").validate[String]

      // Pattern matching
      aResult match {
        case s: JsSuccess[String] => {
          description = s.get
        }
        case e: JsError => {
          Logger.error("Errors: " + JsError.toFlatJson(e).toString())
          BadRequest(toJson(s"description data is missing."))
        }
      }

      aResult = (request.body \ "name").validate[String]

      // Pattern matching
      aResult match {
        case s: JsSuccess[String] => {
          name = s.get
        }
        case e: JsError => {
          Logger.error("Errors: " + JsError.toFlatJson(e).toString())
          BadRequest(toJson(s"name data is missing."))
        }
      }
      Logger.debug(s"updateInformation for dataset with id  $id. Args are $description and $name")

      datasets.updateInformation(id, description, name)
      datasets.get(id) match {
        case Some(dataset) => {
          events.addObjectEvent(user, id, dataset.name, EventType.UPDATE_DATASET_INFORMATION.toString)
        }
      }
      datasets.index(id)
      Ok(Json.obj("status" -> "success"))
    }
    else {
      Logger.error(s"The given id $id is not a valid ObjectId.")
      BadRequest(toJson(s"The given id $id is not a valid ObjectId."))
    }
  }

  def updateName(id: UUID) = PermissionAction(Permission.EditDataset, Some(ResourceRef(ResourceRef.dataset, id)))(parse.json) { implicit request =>
    implicit val user = request.user
    if (UUID.isValid(id.stringify)) {

      //Set up the vars we are looking for
      var name: String = null

      val aResult = (request.body \ "name").validate[String]

      // Pattern matching
      aResult match {
        case s: JsSuccess[String] => {
          name = s.get
        }
        case e: JsError => {
          Logger.error("Errors: " + JsError.toFlatJson(e).toString())
          BadRequest(toJson(s"name data is missing."))
        }
      }
      Logger.debug(s"updateInformation for dataset with id  $id. New name is: $name")

      datasets.updateName(id, name)
      datasets.get(id) match {
        case Some(dataset) => {
          events.addObjectEvent(user, id, dataset.name, EventType.UPDATE_DATASET_INFORMATION.toString)
          datasets.index(id)
          // file in this dataset need to be indexed as well since dataset name will show in file list
          dataset.files.map(files.index(_))
          folders.findByParentDatasetId(id).map(_.files).flatten.map(files.index(_))
        }
      }
      Ok(Json.obj("status" -> "success"))
    }
    else {
      Logger.error(s"The given id $id is not a valid ObjectId.")
      BadRequest(toJson(s"The given id $id is not a valid ObjectId."))
    }
  }

  def updateDescription(id: UUID) = PermissionAction(Permission.EditDataset, Some(ResourceRef(ResourceRef.dataset, id)))(parse.json) { implicit request =>
    implicit val user = request.user
    if (UUID.isValid(id.stringify)) {

      //Set up the vars we are looking for
      var description: String = null

      val aResult: JsResult[String] = (request.body \ "description").validate[String]

      // Pattern matching
      aResult match {
        case s: JsSuccess[String] => {
          description = s.get
        }
        case e: JsError => {
          Logger.error("Errors: " + JsError.toFlatJson(e).toString())
          BadRequest(toJson(s"description data is missing."))
        }
      }
      Logger.debug(s"updateInformation for dataset with id  $id. New description is:  $description ")

      datasets.updateDescription(id, description)
      datasets.get(id) match {
        case Some(dataset) => {
          events.addObjectEvent(user, id, dataset.name, EventType.UPDATE_DATASET_INFORMATION.toString)
        }
      }
      datasets.index(id)
      Ok(Json.obj("status" -> "success"))
    }
    else {
      Logger.error(s"The given id $id is not a valid ObjectId.")
      BadRequest(toJson(s"The given id $id is not a valid ObjectId."))
    }
  }
  //End, Update Dataset Information code

  def addCreator(id: UUID) = PermissionAction(Permission.EditDataset, Some(ResourceRef(ResourceRef.dataset, id)))(parse.json) { implicit request =>
    implicit val user = request.user
    if (UUID.isValid(id.stringify)) {

      //Set up the vars we are looking for
      var creator: String = null

      val aResult: JsResult[String] = (request.body \ "creator").validate[String]

      // Pattern matching
      aResult match {
        case s: JsSuccess[String] => {
          creator = s.get
        }
        case e: JsError => {
          Logger.error("Errors: " + JsError.toFlatJson(e).toString())
          BadRequest(toJson(s"creator data is missing."))
        }
      }
      Logger.debug(s"updateInformation for dataset with id  $id. New creator is:  $creator ")

      datasets.addCreator(id, creator)
      datasets.get(id) match {
        case Some(dataset) => {
          events.addObjectEvent(user, id, dataset.name, EventType.UPDATE_DATASET_INFORMATION.toString)
        }
      }
      datasets.index(id)
      Ok(Json.obj("status" -> "success"))
    }
    else {
      Logger.error(s"The given id $id is not a valid ObjectId.")
      BadRequest(toJson(s"The given id $id is not a valid ObjectId."))
    }
  }
  //End, Update Dataset Information code  
    
  def removeCreator(id: UUID, creator: String) = PermissionAction(Permission.EditDataset, Some(ResourceRef(ResourceRef.dataset, id))) { implicit request =>
    implicit val user = request.user
    if (UUID.isValid(id.stringify)) {

      Logger.debug(s"Remove Creator for dataset with id  $id. :  $creator ")

      datasets.removeCreator(id, creator)
      datasets.get(id) match {
        case Some(dataset) => {
          events.addObjectEvent(user, id, dataset.name, EventType.UPDATE_DATASET_INFORMATION.toString)
        }
      }
      datasets.index(id)
      Ok(Json.obj("status" -> "success"))
    }
    else {
      Logger.error(s"The given id $id is not a valid ObjectId.")
      BadRequest(toJson(s"The given id $id is not a valid ObjectId."))
    }
  }
  //End, removeCreator  
    
  def moveCreator(id: UUID, creator: String, newPos: Int) = PermissionAction(Permission.EditDataset, Some(ResourceRef(ResourceRef.dataset, id))) { implicit request =>
    implicit val user = request.user
    if (UUID.isValid(id.stringify)) {
      
      Logger.debug(s"Move Creator for dataset with id  $id. :  $creator  to $newPos")
      datasets.moveCreator(id, creator, newPos)
      datasets.get(id) match {
        case Some(dataset) => {
          events.addObjectEvent(user, id, dataset.name, EventType.UPDATE_DATASET_INFORMATION.toString)
        }
      }
      datasets.index(id)
      Ok(Json.obj("status" -> "success"))
    }
    else {
      Logger.error(s"The given id $id is not a valid ObjectId.")
      BadRequest(toJson(s"The given id $id is not a valid ObjectId."))
    }
  }
  //End, move Creator
  
  
  //Update License code
  /**
    * REST endpoint: POST: update the license data associated with a specific Dataset
    *
    *  Takes one arg, id:
    *
    *  id, the UUID associated with this dataset
    *
    *  The data contained in the request body will be containe the following key-value pairs:
    *
    *  licenseType, currently:
    *        license1 - corresponds to Limited
    *        license2 - corresponds to Creative Commons
    *        license3 - corresponds to Public Domain
    *
    *  rightsHolder, currently only required if licenseType is license1. Reflects the specific name of the organization or person that holds the rights
    *
    *  licenseText, currently tied to the licenseType
    *        license1 - Free text that a user can enter to describe the license
    *        license2 - 1 of 6 options (or their abbreviations) that reflects the specific set of
    *        options associated with the Creative Commons license, these are:
    *            Attribution-NonCommercial-NoDerivs (by-nc-nd)
    *            Attribution-NoDerivs (by-nd)
    *            Attribution-NonCommercial (by-nc)
    *            Attribution-NonCommercial-ShareAlike (by-nc-sa)
    *            Attribution-ShareAlike (by-sa)
    *            Attribution (by)
    *        license3 - Public Domain Dedication
    *
    *  licenseUrl, free text that a user can enter to go with the licenseText in the case of license1. Fixed URL's for the other 2 cases.
    *
    *  allowDownload, true or false, whether the file or dataset can be downloaded. Only relevant for license1 type.
    */
  def updateLicense(id: UUID) = PermissionAction(Permission.EditLicense, Some(ResourceRef(ResourceRef.dataset, id)))(parse.json) { implicit request =>
    if (UUID.isValid(id.stringify)) {

      //Set up the vars we are looking for
      var licenseType: String = null;
      var rightsHolder: String = null;
      var licenseText: String = null;
      var licenseUrl: String = null;
      var allowDownload: String = null;

      var aResult: JsResult[String] = (request.body \ "licenseType").validate[String]
      // Pattern matching
      aResult match {
        case s: JsSuccess[String] => {
          licenseType = s.get
        }
        case e: JsError => {
          Logger.error("Errors: " + JsError.toFlatJson(e).toString())
          BadRequest(toJson(s"licenseType data is missing."))
        }
      }

      aResult = (request.body \ "rightsHolder").validate[String]

      // Pattern matching
      aResult match {
        case s: JsSuccess[String] => {
          rightsHolder = s.get
        }
        case e: JsError => {
          Logger.error("Errors: " + JsError.toFlatJson(e).toString())
          BadRequest(toJson(s"rightsHolder data is missing."))
        }
      }

      aResult = (request.body \ "licenseText").validate[String]

      // Pattern matching
      aResult match {
        case s: JsSuccess[String] => {
          licenseText = s.get

          //Modify the abbreviations if they were sent in that way
          if (licenseText == "by-nc-nd") {
            licenseText = "Attribution-NonCommercial-NoDerivs"
          }
          else if (licenseText == "by-nd") {
            licenseText = "Attribution-NoDerivs"
          }
          else if (licenseText == "by-nc") {
            licenseText = "Attribution-NonCommercial"
          }
          else if (licenseText == "by-nc-sa") {
            licenseText = "Attribution-NonCommercial-ShareAlike"
          }
          else if (licenseText == "by-sa") {
            licenseText = "Attribution-ShareAlike"
          }
          else if (licenseText == "by") {
            licenseText = "Attribution"
          }
        }
        case e: JsError => {
          Logger.error("Errors: " + JsError.toFlatJson(e).toString())
          BadRequest(toJson(s"licenseText data is missing."))
        }
      }

      aResult = (request.body \ "licenseUrl").validate[String]

      // Pattern matching
      aResult match {
        case s: JsSuccess[String] => {
          licenseUrl = s.get
        }
        case e: JsError => {
          Logger.error("Errors: " + JsError.toFlatJson(e).toString())
          BadRequest(toJson(s"licenseUrl data is missing."))
        }
      }

      aResult = (request.body \ "allowDownload").validate[String]

      // Pattern matching
      aResult match {
        case s: JsSuccess[String] => {
          allowDownload = s.get
        }
        case e: JsError => {
          Logger.error("Errors: " + JsError.toFlatJson(e).toString())
          BadRequest(toJson(s"allowDownload data is missing."))
        }
      }

      Logger.debug(s"updateLicense for dataset with id  $id. Args are $licenseType, $rightsHolder, $licenseText, $licenseUrl, $allowDownload")

      datasets.updateLicense(id, licenseType, rightsHolder, licenseText, licenseUrl, allowDownload)
      Ok(Json.obj("status" -> "success"))
    }
    else {
      Logger.error(s"The given id $id is not a valid ObjectId.")
      BadRequest(toJson(s"The given id $id is not a valid ObjectId."))
    }
  }
  //End, Update License code

  // ---------- Tags related code starts ------------------
  /**
    * REST endpoint: GET: get the tag data associated with this section.
    * Returns a JSON object of multiple fields.
    * One returned field is "tags", containing a list of string values.
    */
  def getTags(id: UUID) = PermissionAction(Permission.ViewDataset, Some(ResourceRef(ResourceRef.dataset, id))) { implicit request =>
    Logger.debug(s"Getting tags for dataset with id  $id.")
    /* Found in testing: given an invalid ObjectId, a runtime exception
     * ("IllegalArgumentException: invalid ObjectId") occurs.  So check it first.
     */
    if (UUID.isValid(id.stringify)) {
      datasets.get(id) match {
        case Some(dataset) =>
          Ok(Json.obj("id" -> dataset.id.toString, "name" -> dataset.name, "tags" -> Json.toJson(dataset.tags.map(_.name))))
        case None => {
          Logger.error(s"The dataset with id $id is not found.")
          NotFound(toJson(s"The dataset with id $id is not found."))
        }
      }
    } else {
      Logger.error(s"The given id $id is not a valid ObjectId.")
      BadRequest(toJson(s"The given id $id is not a valid ObjectId."))
    }
  }

  def removeTag(id: UUID) = PermissionAction(Permission.DeleteTag, Some(ResourceRef(ResourceRef.dataset, id)))(parse.json) { implicit request =>
    Logger.debug("Removing tag " + request.body)
    request.body.\("tagId").asOpt[String].map {
      tagId =>
        Logger.debug(s"Removing $tagId from $id.")
        datasets.removeTag(id, UUID(tagId))
        datasets.index(id)
    }
    Ok(toJson(""))
  }

  /**
    * REST endpoint: POST: Add tags to a dataset.
    * Requires that the request body contains a "tags" field of List[String] type.
    */
  def addTags(id: UUID) = PermissionAction(Permission.AddTag, Some(ResourceRef(ResourceRef.dataset, id)))(parse.json) { implicit request =>
    addTagsHelper(TagCheck_Dataset, id, request)
  }

  /**
    * REST endpoint: POST: remove tags.
    * Requires that the request body contains a "tags" field of List[String] type.
    */
  def removeTags(id: UUID) = PermissionAction(Permission.DeleteTag, Some(ResourceRef(ResourceRef.dataset, id)))(parse.json) { implicit request =>
    removeTagsHelper(TagCheck_Dataset, id, request)
  }

  /*
 *  Helper function to handle adding and removing tags for files/datasets/sections.
 *  Input parameters:
 *      obj_type: one of the three TagCheckObjType's: TagCheck_File, TagCheck_Dataset or TagCheck_Section
 *      op_type:  one of the two strings "add", "remove"
 *      id:       the id in the original addTags call
 *      request:  the request in the original addTags call
 *  Return type:
 *      play.api.mvc.SimpleResult[JsValue]
 *      in the form of Ok, NotFound and BadRequest
 *      where: Ok contains the JsObject: "status" -> "success", the other two contain a JsString,
 *      which contains the cause of the error, such as "No 'tags' specified", and
 *      "The file with id 5272d0d7e4b0c4c9a43e81c8 is not found".
 */
  def addTagsHelper(obj_type: TagCheckObjType, id: UUID, request: UserRequest[JsValue]) = {
    val tagCheck = checkErrorsForTag(obj_type, id, request)

    val error_str = tagCheck.error_str
    val not_found = tagCheck.not_found
    val userOpt = tagCheck.userOpt
    val extractorOpt = tagCheck.extractorOpt
    val tags = tagCheck.tags

    // Now the real work: adding the tags.
    if ("" == error_str) {
      // Clean up leading, trailing and multiple contiguous white spaces and drop empty tags
      val tagsCleaned = tags.get.map(_.trim().replaceAll("\\s+", " ")).filter(!_.isEmpty)
      (obj_type) match {
        case TagCheck_File => files.addTags(id, userOpt, extractorOpt, tagsCleaned)
        case TagCheck_Dataset => {
          datasets.addTags(id, userOpt, extractorOpt, tagsCleaned)
          datasets.get(id) match {
            case Some(dataset) => {
              events.addObjectEvent(request.user, id, dataset.name, EventType.ADD_TAGS_DATASET.toString)
            }
          }
          datasets.index(id)
        }
        case TagCheck_Section => sections.addTags(id, userOpt, extractorOpt, tagsCleaned)
      }
      Ok(Json.obj("status" -> "success"))
    } else {
      Logger.error(error_str)
      if (not_found) {
        NotFound(toJson(error_str))
      } else {
        BadRequest(toJson(error_str))
      }
    }
  }

  def removeTagsHelper(obj_type: TagCheckObjType, id: UUID, request: UserRequest[JsValue]) = {
    val tagCheck = checkErrorsForTag(obj_type, id, request)

    val error_str = tagCheck.error_str
    val not_found = tagCheck.not_found
    val tags = tagCheck.tags

    // Now the real work: removing the tags.
    if ("" == error_str) {
      // Clean up leading, trailing and multiple contiguous white spaces.
      val tagsCleaned = tags.get.map(_.trim().replaceAll("\\s+", " "))
      (obj_type) match {
        case TagCheck_File => files.removeTags(id, tagsCleaned)
        case TagCheck_Dataset => {
          datasets.removeTags(id, tagsCleaned)
          datasets.get(id) match {
            case Some(dataset) => {
              events.addObjectEvent(request.user, id, dataset.name, EventType.REMOVE_TAGS_DATASET.toString)
            }
          }
          datasets.index(id)

        }

        case TagCheck_Section => sections.removeTags(id, tagsCleaned)
      }
      Ok(Json.obj("status" -> "success"))
    } else {
      Logger.error(error_str)
      if (not_found) {
        NotFound(toJson(error_str))
      } else {
        BadRequest(toJson(error_str))
      }
    }
  }

  // TODO: move helper methods to standalone service
  val USERID_ANONYMOUS = "anonymous"

  // Helper class and function to check for error conditions for tags.
  class TagCheck {
    var error_str: String = ""
    var not_found: Boolean = false
    var userOpt: Option[String] = None
    var extractorOpt: Option[String] = None
    var tags: Option[List[String]] = None
  }

  /*
  *  Helper function to check for error conditions.
  *  Input parameters:
  *      obj_type: one of the three TagCheckObjType's: TagCheck_File, TagCheck_Dataset or TagCheck_Section
  *      id:       the id in the original addTags call
  *      request:  the request in the original addTags call
  *  Returns:
  *      tagCheck: a TagCheck object, containing the error checking results:
  *
  *      If error_str == "", then no error is found;
  *      otherwise, it contains the cause of the error.
  *      not_found is one of the error conditions, meaning the object with
  *      the given id is not found in the DB.
  *      userOpt, extractorOpt and tags are set according to the request's content,
  *      and will remain None if they are not specified in the request.
  *      We change userOpt from its default None value, only if the userId
  *      is not USERID_ANONYMOUS.  The use case for this is the extractors
  *      posting to the REST API -- they'll use the commKey to post, and the original
  *      userId of these posts is USERID_ANONYMOUS -- in this case, we'd like to
  *      record the extractor_id, but omit the userId field, so we leave userOpt as None.
  */
  def checkErrorsForTag(obj_type: TagCheckObjType, id: UUID, request: UserRequest[JsValue]): TagCheck = {
    val userObj = request.user
    Logger.debug("checkErrorsForTag: user id: " + userObj.get.identityId.userId + ", user.firstName: " + userObj.get.firstName
      + ", user.LastName: " + userObj.get.lastName + ", user.fullName: " + userObj.get.fullName)
    val userId = userObj.get.identityId.userId
    if (USERID_ANONYMOUS == userId) {
      Logger.debug("checkErrorsForTag: The user id is \"anonymous\".")
    }

    var userOpt: Option[String] = None
    var extractorOpt: Option[String] = None
    var error_str = ""
    var not_found = false
    val tags = request.body.\("tags").asOpt[List[String]]

    if (tags.isEmpty) {
      error_str = "No \"tags\" specified, request.body: " + request.body.toString
    } else if (!UUID.isValid(id.stringify)) {
      error_str = "The given id " + id + " is not a valid ObjectId."
    } else {
      obj_type match {
        case TagCheck_File => not_found = files.get(id).isEmpty
        case TagCheck_Dataset => not_found = datasets.get(id).isEmpty
        case TagCheck_Section => not_found = sections.get(id).isEmpty
        case _ => error_str = "Only file/dataset/section is supported in checkErrorsForTag()."
      }
      if (not_found) {
        error_str = s"The $obj_type with id $id is not found"
      }
    }
    if ("" == error_str) {
      if (USERID_ANONYMOUS == userId) {
        val eid = request.body.\("extractor_id").asOpt[String]
        eid match {
          case Some(extractor_id) => extractorOpt = eid
          case None => error_str = "No \"extractor_id\" specified, request.body: " + request.body.toString
        }
      } else {
        userOpt = Option(userId)
      }
    }
    val tagCheck = new TagCheck
    tagCheck.error_str = error_str
    tagCheck.not_found = not_found
    tagCheck.userOpt = userOpt
    tagCheck.extractorOpt = extractorOpt
    tagCheck.tags = tags
    tagCheck
  }

  /**
    * REST endpoint: POST: remove all tags.
    */
  def removeAllTags(id: UUID) = PermissionAction(Permission.DeleteTag, Some(ResourceRef(ResourceRef.dataset, id))) { implicit request =>
    Logger.debug(s"Removing all tags for dataset with id: $id.")
    if (UUID.isValid(id.stringify)) {
      datasets.get(id) match {
        case Some(dataset) => {
          datasets.removeAllTags(id)
          datasets.index(id)

          Ok(Json.obj("status" -> "success"))
        }
        case None => {
          val msg = s"The dataset with id $id is not found."
          Logger.error(msg)
          NotFound(toJson(msg))
        }
      }
    } else {
      val msg = s"The given id $id is not a valid ObjectId."
      Logger.error(msg)
      BadRequest(toJson(msg))
    }
  }

  // ---------- Tags related code ends ------------------

  def comment(id: UUID) = PermissionAction(Permission.AddComment, Some(ResourceRef(ResourceRef.dataset, id)))(parse.json) { implicit request =>
    request.user match {
      case Some(identity) => {
        request.body.\("text").asOpt[String] match {
          case Some(text) => {
            val comment = new Comment(identity, text, dataset_id = Some(id))
            comments.insert(comment)
            datasets.index(id)
            datasets.get(id) match {
              case Some(dataset) => {
                events.addSourceEvent(request.user, comment.id, comment.text , dataset.id, dataset.name, EventType.ADD_COMMENT_DATASET.toString)
              }
            }
            Ok(comment.id.toString())
          }
          case None => {
            Logger.error("no text specified.")
            BadRequest
          }
        }
      }
      case None => BadRequest
    }
  }

  /**
    * List datasets satisfying a user metadata search tree.
    */
  def searchDatasetsUserMetadata = PermissionAction(Permission.ViewDataset)(parse.json) { implicit request =>
    Logger.debug("Searching datasets' user metadata for search tree.")

    var searchJSON = Json.stringify(request.body)
    Logger.debug("thejsson: " + searchJSON)
    var searchTree = JsonUtil.parseJSON(searchJSON).asInstanceOf[java.util.LinkedHashMap[String, Any]]

    var searchQuery = datasets.searchUserMetadataFormulateQuery(searchTree)

    //searchQuery = searchQuery.reverse

    Logger.debug("Search completed. Returning datasets list.")

    val list = for (dataset <- searchQuery) yield dataset
    Logger.debug("thelist: " + toJson(list))
    Ok(toJson(list))
  }

  /**
    * List datasets satisfying a general metadata search tree.
    */
  def searchDatasetsGeneralMetadata = PermissionAction(Permission.ViewDataset)(parse.json) { implicit request =>
    Logger.debug("Searching datasets' metadata for search tree.")

    var searchJSON = Json.stringify(request.body)
    Logger.debug("thejsson: " + searchJSON)
    var searchTree = JsonUtil.parseJSON(searchJSON).asInstanceOf[java.util.LinkedHashMap[String, Any]]

    var searchQuery = datasets.searchAllMetadataFormulateQuery(searchTree)

    //searchQuery = searchQuery.reverse

    Logger.debug("Search completed. Returning datasets list.")

    val list = for (dataset <- searchQuery) yield dataset
    Logger.debug("thelist: " + toJson(list))
    Ok(toJson(list))
  }

  /**
    * Return whether a dataset is currently being processed.
    */
  def isBeingProcessed(id: UUID) = PermissionAction(Permission.ViewDataset, Some(ResourceRef(ResourceRef.dataset, id))) { implicit request =>
    datasets.get(id) match {
      case Some(dataset) => {
        var isActivity = "false"
        try {
          for (fid <- dataset.files) {
            extractions.findIfBeingProcessed(fid) match {
              case false =>
              case true => {
                isActivity = "true"
                throw ActivityFound
              }
            }
          }
        } catch {
          case ActivityFound =>
        }

        Ok(toJson(Map("isBeingProcessed" -> isActivity)))
      }
      case None => {
        Logger.error(s"Error getting dataset $id"); InternalServerError
      }
    }
  }

  // TODO make a case class to represent very long tuple below
  def jsonPreviewsFiles(filesList: List[(models.File, List[(java.lang.String, String, String, String, java.lang.String, String, Long)])]): JsValue = {
    val list = for (filePrevs <- filesList) yield jsonPreviews(filePrevs._1, filePrevs._2)
    toJson(list)
  }

  // TODO make a case class to represent very long tuple below
  def jsonPreviews(prvFile: models.File, prvs: List[(java.lang.String, String, String, String, java.lang.String, String, Long)]): JsValue = {
    val list = for (prv <- prvs) yield jsonPreview(prv._1, prv._2, prv._3, prv._4, prv._5, prv._6, prv._7)
    val listJson = toJson(list.toList)
    toJson(Map[String, JsValue]("file_id" -> JsString(prvFile.id.toString), "previews" -> listJson))
  }

  def jsonPreview(pvId: String, pId: String, pPath: String, pMain: String, pvRoute: String, pvContentType: String, pvLength: Long): JsValue = {
    if (pId.equals("X3d"))
      toJson(Map("pv_id" -> pvId, "p_id" -> pId,
        "p_path" -> controllers.routes.Assets.at(pPath).toString,
        "p_main" -> pMain, "pv_route" -> pvRoute,
        "pv_contenttype" -> pvContentType, "pv_length" -> pvLength.toString,
        "pv_annotationsEditPath" -> api.routes.Previews.editAnnotation(UUID(pvId)).toString,
        "pv_annotationsListPath" -> api.routes.Previews.listAnnotations(UUID(pvId)).toString,
        "pv_annotationsAttachPath" -> api.routes.Previews.attachAnnotation(UUID(pvId)).toString))
    else
      toJson(Map("pv_id" -> pvId, "p_id" -> pId, "p_path" -> controllers.routes.Assets.at(pPath).toString, "p_main" -> pMain, "pv_route" -> pvRoute, "pv_contenttype" -> pvContentType, "pv_length" -> pvLength.toString))
  }

  def getPreviews(id: UUID) = PermissionAction(Permission.ViewDataset, Some(ResourceRef(ResourceRef.dataset, id))) { implicit request =>
    datasets.get(id) match {
      case Some(dataset) => {
        val datasetWithFiles = dataset.copy(files = dataset.files)
        val datasetFiles = files.get(datasetWithFiles.files).found
        val previewers = Previewers.findDatasetPreviewers()
        //NOTE Should the following code be unified somewhere since it is duplicated in Datasets and Files for both api and controllers
        val previewslist = for (f <- datasetFiles; if (f.showPreviews.equals("DatasetLevel"))) yield {
          val pvf = for (p <- previewers; pv <- f.previews; if (p.contentType.contains(pv.contentType))) yield {
            (pv.id.toString, p.id, p.path, p.main, api.routes.Previews.download(pv.id).toString, pv.contentType, pv.length)
          }
          if (pvf.length > 0) {
            (f -> pvf)
          } else {
            val ff = for (p <- previewers; if (p.contentType.contains(f.contentType))) yield {
              //Change here. If the license allows the file to be downloaded by the current user, go ahead and use the
              //file bytes as the preview, otherwise return the String null and handle it appropriately on the front end
              if (f.licenseData.isDownloadAllowed(request.user)) {
                (f.id.toString, p.id, p.path, p.main, controllers.routes.Files.file(f.id) + "/blob", f.contentType, f.length)
              }
              else {
                (f.id.toString, p.id, p.path, p.main, "null", f.contentType, f.length)
              }
            }
            (f -> ff)
          }
        }
        Ok(jsonPreviewsFiles(previewslist.asInstanceOf[List[(models.File, List[(java.lang.String, String, String, String, java.lang.String, String, Long)])]]))
      }
      case None => {
        Logger.error("Error getting dataset" + id); InternalServerError
      }
    }
  }

  //Detach and delete dataset code
  /**
    * REST endpoint: DELETE: detach all files from a dataset and then delete the dataset
    *
    *  Takes one arg, id:
    *
    *  @param id, the UUID associated with the dataset to detach all files from and then delete.
    *
    */
  def detachAndDeleteDataset(id: UUID) = PermissionAction(Permission.DeleteDataset, Some(ResourceRef(ResourceRef.dataset, id))) { implicit request =>
    datasets.get(id) match{
      case Some(dataset) => {
        val useTrash = play.api.Play.configuration.getBoolean("useTrash").getOrElse(false)
        if (!useTrash || (useTrash && dataset.trash)) {
          for (f <- dataset.files) {
            detachFileHelper(dataset.id, f, dataset, request.user)
          }
          deleteDatasetHelper(dataset.id, request)
          Ok(toJson(Map("status" -> "success")))
        } else {
          datasets.update(dataset.copy(trash = true, dateMovedToTrash = Some(new Date())))
          events.addObjectEvent(request.user, id, dataset.name, "move_dataset_trash")
          Ok(toJson(Map("status" -> "success")))
        }
      }
      case None=> {
        Ok(toJson(Map("status" -> "success")))
      }
    }
  }

  /**
    * Utility function to consolidate the utility portions of the delete dataset functionality
    * so that it can be easily called from multiple API operations.
    *
    * @param id The id of the dataset that a file is being detached from
    * @param request The implicit request parameter which is part of the REST API call
    *
    */
  def deleteDatasetHelper(id: UUID, request: UserRequest[AnyContent]) = {
    datasets.get(id) match {
      case Some(dataset) => {
        //remove dataset from RDF triple store if triple store is used
        configuration.getString("userdfSPARQLStore").getOrElse("no") match {
          case "yes" => rdfsparql.removeDatasetFromGraphs(id)
          case _ => Logger.debug("userdfSPARQLStore not enabled")
        }
        events.addObjectEvent(request.user, dataset.id, dataset.name, EventType.DELETE_DATASET.toString)
        datasets.removeDataset(id, Utils.baseUrl(request), request.apiKey, request.user)

        current.plugin[AdminsNotifierPlugin].foreach{_.sendAdminsNotification(Utils.baseUrl(request), "Dataset","removed",dataset.id.stringify, dataset.name)}
        Ok(toJson(Map("status"->"success")))
      }
      case None => Ok(toJson(Map("status" -> "success")))
    }
  }

  def deleteDataset(id: UUID) = PermissionAction(Permission.DeleteDataset, Some(ResourceRef(ResourceRef.dataset, id))) { implicit request =>
    datasets.get(id) match {
      case Some(ds) => {
        val useTrash = play.api.Play.configuration.getBoolean("useTrash").getOrElse(false)
        if (!useTrash || (useTrash && ds.trash)){
          deleteDatasetHelper(id, request)
        } else {
          datasets.update(ds.copy(trash = true, dateMovedToTrash = Some(new Date())))
          events.addObjectEvent(request.user, id, ds.name, "move_dataset_trash")
          Ok(toJson(Map("status"->"success")))
        }
      }
      case None => BadRequest("No dataset found with id " + id)
    }
  }

  def restoreDataset(id : UUID) = PermissionAction(Permission.DeleteDataset, Some(ResourceRef(ResourceRef.dataset, id))) {implicit request=>
    implicit val user = request.user
    user match {
      case Some(u) => {
        datasets.get(id) match {
          case Some(ds) => {
            datasets.update(ds.copy(trash = false, dateMovedToTrash=None))
            events.addObjectEvent(user, ds.id, ds.name, "restore_dataset_trash")

            Ok(toJson(Map("status" -> "success")))
          }
          case None => InternalServerError("Update Access failed")
        }
      }
      case None => BadRequest("No user supplied")
    }
  }

  def emptyTrash() = PrivateServerAction {implicit request =>
    val user = request.user
    user match {
      case Some(u) => {
        val trashDatasets = datasets.listUserTrash(request.user,0)
        for (ds <- trashDatasets){
          events.addObjectEvent(request.user, ds.id, ds.name, EventType.DELETE_DATASET.toString)
          datasets.removeDataset(ds.id, Utils.baseUrl(request), request.apiKey, request.user)
        }
      }
      case None =>
    }
    Ok(toJson("Done emptying trash"))
  }

  def listDatasetsInTrash(limit : Int) = PrivateServerAction {implicit request =>
    val user = request.user
    user match {
      case Some(u) => {
        val trashDatasets = datasets.listUserTrash(user,limit)
        Ok(toJson(trashDatasets))
      }
      case None => BadRequest("No user supplied")
    }
  }

  def clearOldDatasetsTrash(days : Int) = ServerAdminAction {implicit request =>

    val deleteBeforeCalendar : Calendar = Calendar.getInstance()
    deleteBeforeCalendar.add(Calendar.DATE,-days)
    val deleteBeforeDateTime = deleteBeforeCalendar.getTimeInMillis()
    val allDatasetsInTrash = datasets.listUserTrash(None,0)
    allDatasetsInTrash.foreach(d => {
      val dateInTrash = d.dateMovedToTrash.getOrElse(new Date())
      if (dateInTrash.getTime() < deleteBeforeDateTime){
        deleteDatasetHelper(d.id, request)
      }
    })
    Ok(toJson("Deleted all datasets in trash older than " + days + " days"))

  }

  def getRDFUserMetadata(id: UUID, mappingNumber: String="1") = PermissionAction(Permission.ViewMetadata, Some(ResourceRef(ResourceRef.dataset, id))) { implicit request =>
    current.plugin[RDFExportService].isDefined match{
      case true => {
        current.plugin[RDFExportService].get.getRDFUserMetadataDataset(id.toString, mappingNumber) match{
          case Some(resultFile) =>{
            Ok.chunked(Enumerator.fromStream(new FileInputStream(resultFile), chunksize))
              .withHeaders(CONTENT_TYPE -> "application/rdf+xml")
              .withHeaders(CONTENT_DISPOSITION -> (FileUtils.encodeAttachment(resultFile.getName(),request.headers.get("user-agent").getOrElse(""))))
          }
          case None => BadRequest(toJson("Dataset not found " + id))
        }
      }
      case _ => Ok("RDF export plugin not enabled")
    }
  }

  def jsonToXML(theJSON: String): java.io.File = {

    val jsonObject = new JSONObject(theJSON)
    var xml = org.json.XML.toString(jsonObject)

    Logger.debug("thexml: " + xml)

    //Remove spaces from XML tags
    var currStart = xml.indexOf("<")
    var currEnd = -1
    var xmlNoSpaces = ""
    while (currStart != -1) {
      xmlNoSpaces = xmlNoSpaces + xml.substring(currEnd + 1, currStart)
      currEnd = xml.indexOf(">", currStart + 1)
      xmlNoSpaces = xmlNoSpaces + xml.substring(currStart, currEnd + 1).replaceAll(" ", "_")
      currStart = xml.indexOf("<", currEnd + 1)
    }
    xmlNoSpaces = xmlNoSpaces + xml.substring(currEnd + 1)

    val xmlFile = java.io.File.createTempFile("xml", ".xml")
    val fileWriter = new BufferedWriter(new FileWriter(xmlFile))
    fileWriter.write(xmlNoSpaces)
    fileWriter.close()

    return xmlFile
  }

  def getRDFURLsForDataset(id: UUID) = PermissionAction(Permission.ViewMetadata, Some(ResourceRef(ResourceRef.dataset, id))) { implicit request =>
    current.plugin[RDFExportService].isDefined match{
      case true =>{
        current.plugin[RDFExportService].get.getRDFURLsForDataset(id.toString)  match {
          case Some(listJson) => {
            Ok(listJson)
          }
          case None => Logger.error(s"Error getting dataset $id"); InternalServerError
        }
      }
      case false => {
        Ok("RDF export plugin not enabled")
      }
    }
  }

  def getTechnicalMetadataJSON(id: UUID) = PermissionAction(Permission.ViewMetadata, Some(ResourceRef(ResourceRef.dataset, id))) { implicit request =>
    datasets.get(id) match {
      case Some(dataset) => {
        val listOfMetadata = metadataService.getMetadataByAttachTo(ResourceRef(ResourceRef.dataset, id))
          .filter(metadata => metadata.creator.typeOfAgent == "extractor" || metadata.creator.typeOfAgent == "cat:extractor")
          .map(JSONLD.jsonMetadataWithContext(_) \ "content")
        Ok(toJson(listOfMetadata))
      }
      case None => Logger.error("Error finding dataset" + id); InternalServerError
    }
  }

  def getXMLMetadataJSON(id: UUID) = PermissionAction(Permission.ViewMetadata, Some(ResourceRef(ResourceRef.dataset, id))) { implicit request =>
    datasets.get(id)  match {
      case Some(dataset) => {
        Ok(datasets.getXMLMetadataJSON(id))
      }
      case None => {Logger.error("Error finding dataset" + id); InternalServerError}
    }
  }

  def getUserMetadataJSON(id: UUID) = PermissionAction(Permission.ViewMetadata, Some(ResourceRef(ResourceRef.dataset, id))) { implicit request =>
    datasets.get(id)  match {
      case Some(dataset) => {
        Ok(datasets.getUserMetadataJSON(id))
      }
      case None => {
        Logger.error("Error finding dataset" + id);
        InternalServerError
      }

    }
  }

  def dumpDatasetGroupings = ServerAdminAction { request =>

    val unsuccessfulDumps = datasets.dumpAllDatasetGroupings
    if(unsuccessfulDumps.size == 0)
      Ok("Dumping of dataset file groupings was successful for all datasets.")
    else{
      var unsuccessfulMessage = "Dumping of dataset file groupings was successful for all datasets except dataset(s) with id(s) "
      for(badDataset <- unsuccessfulDumps){
        unsuccessfulMessage = unsuccessfulMessage + badDataset + ", "
      }
      unsuccessfulMessage = unsuccessfulMessage.substring(0, unsuccessfulMessage.length()-2) + "."
      Ok(unsuccessfulMessage)
    }
  }

  def dumpDatasetsMetadata = ServerAdminAction { request =>

    val unsuccessfulDumps = datasets.dumpAllDatasetMetadata
    if(unsuccessfulDumps.size == 0)
      Ok("Dumping of datasets metadata was successful for all datasets.")
    else{
      var unsuccessfulMessage = "Dumping of datasets metadata was successful for all datasets except dataset(s) with id(s) "
      for(badDataset <- unsuccessfulDumps){
        unsuccessfulMessage = unsuccessfulMessage + badDataset + ", "
      }
      unsuccessfulMessage = unsuccessfulMessage.substring(0, unsuccessfulMessage.length()-2) + "."
      Ok(unsuccessfulMessage)
    }
  }

  def follow(id: UUID) = AuthenticatedAction {
    request =>
      val user = request.user
      user match {
        case Some(loggedInUser) => {
          datasets.get(id) match {
            case Some(dataset) => {
              events.addObjectEvent(user, id, dataset.name, EventType.FOLLOW_DATASET.toString)
              datasets.addFollower(id, loggedInUser.id)
              userService.followDataset(loggedInUser.id, id)

              val recommendations = getTopRecommendations(id, loggedInUser)
              recommendations match {
                case x::xs => Ok(Json.obj("status" -> "success", "recommendations" -> recommendations))
                case Nil => Ok(Json.obj("status" -> "success"))
              }
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

  def unfollow(id: UUID) = AuthenticatedAction { implicit request =>
    implicit val user = request.user

    user match {
      case Some(loggedInUser) => {
        datasets.get(id) match {
          case Some(dataset) => {
            events.addObjectEvent(user, id, dataset.name, EventType.UNFOLLOW_DATASET.toString)
            datasets.removeFollower(id, loggedInUser.id)
            userService.unfollowDataset(loggedInUser.id, id)
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

  def getTopRecommendations(followeeUUID: UUID, follower: User): List[MiniEntity] = {
    val followeeModel = datasets.get(followeeUUID)
    followeeModel match {
      case Some(followeeModel) => {
        val sourceFollowerIDs = followeeModel.followers
        val excludeIDs = follower.followedEntities.map(typedId => typedId.id) ::: List(followeeUUID, follower.id)
        val num = play.api.Play.configuration.getInt("number_of_recommendations").getOrElse(10)
        userService.getTopRecommendations(sourceFollowerIDs, excludeIDs, num)
      }
      case None => {
        List.empty
      }
    }
  }

  /**
    * Create a mapping for each file to their unique location
    */
  def listFilesInFolder(fileids: List[UUID], folderids: List[UUID], parent: String, filenameMap: scala.collection.mutable.Map[UUID, String], inputFiles: scala.collection.mutable.ListBuffer[models.File]): Unit = {
    // get all file objects
    val fileobjs = files.get(fileids).found

    // map fileobj to filename, make sure filename is unique
    // potential improvemnt would be to keep a map -> array of ids
    // if array.length == 1, then no duplicate, else fix all duplicate ids
    fileobjs.foreach(f => {
      inputFiles.append(f)
      if (fileobjs.exists(x => x.id != f.id && x.filename == f.filename)) {
        // create new filename filename_id.ext
        val (filename, ext) = f.filename.lastIndexOf('.') match {
          case(-1) => (f.filename, "")
          case(x) => (f.filename.substring(0, x), f.filename.substring(x))
        }
        filenameMap(f.id) = s"${parent}${filename}_${f.id}${ext}"
      } else {
        filenameMap(f.id) = s"${parent}${f.filename}"
      }
    })

    // get all folder objects
    val folderobjs = folderids.flatMap(x => folders.get(x) match {
      case Some(f) => Some(f)
      case None => {
        Logger.error(s"Could not find folder with id=${x.uuid}")
        None
      }
    })
    folderobjs.foreach(f => {
      val folder = if (folderobjs.exists(x => x.id != f.id && x.displayName == f.displayName)) {
        // this case should not happen since folders are made unique at creation
        s"${parent}${f.displayName}_${f.id.stringify}/"
      } else {
        s"${parent}${f.displayName}/"
      }
      listFilesInFolder(f.files, f.folders, folder, filenameMap, inputFiles)
    })
  }

  /**
    * Enumerator to loop over all files in a dataset and return chunks for the result zip file that will be
    * streamed to the client. The zip files are streamed and not stored on disk.
    *
    * @param dataset dataset from which to get teh files
    * @param chunkSize chunk size in memory in which to buffer the stream
    * @param compression java built in compression value. Use 0 for no compression.
    * @param bagit whether or not to include bagit structures in zip
   *  @param baseURL the root Clowder URL for metadata files, from original request
    * @param user an optional user to include in metadata
    * @param fileIDs a list of UUIDs of files in the dataset to include (i.e. marked file downloads)
    * @param folderId a folder UUID in the dataset to include (i.e. folder download)
    * @return Enumerator to produce array of bytes from a zipped stream containing the bytes of each file
    *         in the dataset
    */
  def enumeratorFromDataset(dataset: Dataset, chunkSize: Int = 1024 * 8,
                            compression: Int = Deflater.DEFAULT_COMPRESSION, bagit: Boolean, baseURL: String,
                            user : Option[User], fileIDs: Option[List[UUID]], folderId: Option[UUID])
                           (implicit ec: ExecutionContext): Enumerator[Array[Byte]] = {
    implicit val pec = ec.prepare()
    val dataFolder = if (bagit) "data/" else ""
    val filenameMap = scala.collection.mutable.Map.empty[UUID, String]
    val inputFiles = scala.collection.mutable.ListBuffer.empty[models.File]

    // Get list of all files and folder in dataset and enforce unique names
    fileIDs match {
      case Some(fids) => {
        Logger.info("Downloading only some files")
        Logger.info(fids.toString)
        listFilesInFolder(fids, List.empty, dataFolder, filenameMap, inputFiles)
      }
      case None => {
        folderId match {
          case Some(fid) => listFilesInFolder(List.empty, List(fid), dataFolder, filenameMap, inputFiles)
          case None => listFilesInFolder(dataset.files, dataset.folders, dataFolder, filenameMap, inputFiles)
        }
      }
    }

    // Keep two MD5 checksum lists, one for dataset files and one for BagIt files
    val md5Files = scala.collection.mutable.HashMap.empty[String, MessageDigest]
    val md5Bag = scala.collection.mutable.HashMap.empty[String, MessageDigest]

    val byteArrayOutputStream = new ByteArrayOutputStream(chunkSize)
    val zip = new ZipOutputStream(byteArrayOutputStream)
    zip.setLevel(compression)

    // Prep enumeration handlers
    var totalBytes = 0L
    var level = "dataset"
    var file_type = "metadata"
    var file_index = 0

    // Begin input stream with dataset info file
    var is = addDatasetInfoToZip(dataFolder, dataset, zip)
    val md5 = MessageDigest.getInstance("MD5")
    md5Files.put(dataFolder+"_info.json", md5)
    is = Some(new DigestInputStream(is.get, md5))

    // Handle rest of dataset structure by individual file
    Enumerator.generateM({
      is match {
        case Some(inputStream) => {
          val buffer = new Array[Byte](chunkSize)
          val bytesRead = scala.concurrent.blocking {
            inputStream.read(buffer)
          }
          val chunk = bytesRead match {
            case -1 => {
              // finished individual file
              zip.closeEntry()
              inputStream.close()

              (level, file_type) match {
                case ("dataset", "metadata") => {
                  is = addDatasetMetadataToZip(dataFolder, dataset, zip)
                  is = addMD5Entry(dataFolder+"_dataset_metadata.json", is, md5Files)
                  level = "file"
                  file_type = "info"
                }
                case ("file", "info") => {
                  val filename = filenameMap(inputFiles(file_index).id)
                  is = addFileInfoToZip(filename, inputFiles(file_index), zip)
                  is = addMD5Entry(filename+"_info.json", is, md5Files)
                  file_index += 1
                  if (file_index >= inputFiles.size) {
                    file_index = 0
                    file_type = "metadata"
                  }
                }
                case ("file", "metadata") => {
                  val filename = filenameMap(inputFiles(file_index).id)
                  is = addFileMetadataToZip(filename, inputFiles(file_index), zip)
                  is = addMD5Entry(filename+"_metadata.json", is, md5Files)
                  file_index += 1
                  if (file_index >= inputFiles.size){
                    file_index = 0
                    file_type = "bytes"
                  }
                }
                case ("file", "bytes") => {
                  val filename = filenameMap(inputFiles(file_index).id)
                  is = addFileToZip(filename, inputFiles(file_index), zip)
                  is = addMD5Entry(filename, is, md5Files)
                  file_index +=1
                  if (file_index >= inputFiles.size) {
                    if (bagit) {
                      file_index = 0
                      level = "bag"
                      file_type = "bagit.txt"
                    } else {
                      level = "done"
                      file_type = "none"
                    }
                  }
                }
                case ("bag", "bagit.txt") => {
                  // BagIt "header" data e.g. date, author
                  is = addBagItTextToZip(totalBytes, filenameMap.size, zip, dataset, user)
                  is = addMD5Entry("bagit.txt", is, md5Bag)
                  file_type = "bag-info.txt"
                }
                case ("bag", "bag-info.txt") => {
                  // BagIt version & encoding
                  is = addBagInfoToZip(zip)
                  is = addMD5Entry("bag-info.txt", is, md5Bag)
                  file_type = "manifest-md5.txt"
                }
                case ("bag", "manifest-md5.txt") => {
                  // List of all dataset (i.e. not BagIt) files and their checksums
                  is = addManifestMD5ToZip(md5Files.toMap[String,MessageDigest], zip)
                  is = addMD5Entry("manifest-md5.txt", is, md5Bag)
                  file_type = "datacite.xml"
                }
                case ("bag", "datacite.xml") => {
                  // RDA-recommended DataCite xml file
                  is = addDataCiteMetadataToZip(zip, dataset, baseURL)
                  file_type = "clowder.xml"
                }
                case ("bag", "clowder.xml") => {
                  // Clowder bespoke xml file (other instances can use to replicate the dataset)
                  is = addClowderXMLMetadataToZip(zip, dataset, baseURL)
                  file_type = "tagmanifest-md5.txt"
                }
                case ("bag", "tagmanifest-md5.txt") => {
                  // List of all BagIt, xml or non-dataset files and their checksums
                  is = addTagManifestMD5ToZip(md5Bag.toMap[String,MessageDigest], zip)
                  level = "done"
                  file_type = "none"
                }
                case ("done", "none") => {
                  zip.close()
                  is = None
                }
                case (_,_) => {
                  Logger.error("Unexpected values in dataset zip enum. Closing out anyway.")
                  zip.close()
                  is = None
                }
              }
              Some(byteArrayOutputStream.toByteArray)
            }
            case read => {
              zip.write(buffer, 0, read)
              Some(byteArrayOutputStream.toByteArray)
            }
          }

          if (level == "file" || level == "dataset")
            totalBytes += bytesRead

          byteArrayOutputStream.reset()
          Future.successful(chunk)
        }
        case None => Future.successful(None)
      }
    })(pec)
  }

  private def addMD5Entry(name: String, is: Option[InputStream], md5HashMap: scala.collection.mutable.HashMap[String, MessageDigest]) = {
    val md5 = MessageDigest.getInstance("MD5")
    md5HashMap.put(name, md5)
    Some(new DigestInputStream(is.get, md5))
  }

  private def addFileToZip(filename: String, file: models.File, zip: ZipOutputStream): Option[InputStream] = {
    files.getBytes(file.id) match {
      case Some((inputStream, _, _, _)) => {
        zip.putNextEntry(new ZipEntry(filename))
        Some(inputStream)
      }
      case None => None
    }
  }

  private def addFileMetadataToZip(filename: String, file: models.File, zip: ZipOutputStream): Option[InputStream] = {
    zip.putNextEntry(new ZipEntry(filename + "_metadata.json"))
    val fileMetadata = metadataService.getMetadataByAttachTo(ResourceRef(ResourceRef.file, file.id)).map(JSONLD.jsonMetadataWithContext(_))
    val s : String = Json.prettyPrint(Json.toJson(fileMetadata))
    Some(new ByteArrayInputStream(s.getBytes("UTF-8")))
  }

  private def getDatasetInfoAsJson(dataset : Dataset) : JsValue = {
    val rightsHolder = {
      val licenseType = dataset.licenseData.m_licenseType
      if (licenseType == "license1") {
        dataset.author.fullName
      } else if (licenseType == "license2") {
        "Creative Commons"
      } else if (licenseType == "license3") {
        "Public Domain Dedication"
      } else {
        "None"
      }
    }

    val spaceNames = for (
      spaceId <- dataset.spaces;
      space <- spaces.get(spaceId)
    ) yield {
      space.name
    }

    val dataset_description = Utils.decodeString(dataset.description)

    val licenseInfo = Json.obj("licenseText"->dataset.licenseData.m_licenseText,"rightsHolder"->rightsHolder)
    Json.obj("id"->dataset.id,"name"->dataset.name,"author"->dataset.author.email,"description"->dataset_description, "spaces"->spaceNames.mkString(","),"lastModified"->dataset.lastModifiedDate.toString,"license"->licenseInfo)
  }

  private def addDatasetInfoToZip(folderName: String, dataset: models.Dataset, zip: ZipOutputStream): Option[InputStream] = {
    zip.putNextEntry(new ZipEntry(folderName + "/_info.json"))
    val infoListMap = Json.prettyPrint(getDatasetInfoAsJson(dataset))
    Some(new ByteArrayInputStream(infoListMap.getBytes("UTF-8")))
  }

  private def getFileInfoAsJson(file : models.File) : JsValue = {
    val rightsHolder = {
      val licenseType = file.licenseData.m_licenseType
      if (licenseType == "license1") {
        file.author.fullName
      } else if (licenseType == "license2") {
        "Creative Commons"
      } else if (licenseType == "license3") {
        "Public Domain Dedication"
      } else {
        "None"
      }

    }
    val licenseInfo = Json.obj("licenseText"->file.licenseData.m_licenseText,"rightsHolder"->rightsHolder)
    Json.obj("id" -> file.id, "filename" -> file.filename, "author" -> file.author.email, "uploadDate" -> file.uploadDate.toString,"contentType"->file.contentType,"description"->file.description,"license"->licenseInfo)
  }

  private def addFileInfoToZip(filename: String, file: models.File, zip: ZipOutputStream): Option[InputStream] = {
    zip.putNextEntry(new ZipEntry(filename + "_info.json"))
    val fileInfo = getFileInfoAsJson(file)
    val s : String = Json.prettyPrint(fileInfo)
    Some(new ByteArrayInputStream(s.getBytes("UTF-8")))
  }

  private def addDatasetMetadataToZip(folderName: String, dataset : models.Dataset, zip: ZipOutputStream): Option[InputStream] = {
    zip.putNextEntry(new ZipEntry(folderName + "_dataset_metadata.json"))
    val datasetMetadata = metadataService.getMetadataByAttachTo(ResourceRef(ResourceRef.dataset, dataset.id))
      .map(JSONLD.jsonMetadataWithContext(_))
    val s : String = Json.prettyPrint(Json.toJson(datasetMetadata))
    Some(new ByteArrayInputStream(s.getBytes("UTF-8")))
  }

  // BagIt "header" data e.g. date, author
  private def addBagItTextToZip(totalbytes: Long, totalFiles: Long, zip: ZipOutputStream, dataset: models.Dataset, user: Option[User]) = {
    zip.putNextEntry(new ZipEntry("bagit.txt"))
    var s = ""
    s += "Bag-Software-Agent: clowder.ncsa.illinois.edu\n"
    s += "Bagging-Date: " + (new SimpleDateFormat("yyyy-MM-dd hh:mm:ss")).format(Calendar.getInstance.getTime) + "\n"
    s += "Bag-Size: " + _root_.util.Formatters.humanReadableByteCount(totalbytes) + "\n"
    s += "Payload-Oxum: " + totalbytes + "." + totalFiles + "\n"
    s += "Internal-Sender-Identifier: " + dataset.id + "\n"
    s += "Internal-Sender-Description: " + dataset.description + "\n"
    if (user.isDefined) {
      s += "Contact-Name: " + user.get.fullName + "\n"
      s += "Contact-Email: " + user.get.email.getOrElse("") + "\n"
    }
    Some(new ByteArrayInputStream(s.getBytes("UTF-8")))
  }

  // BagIt version & encoding
  private def addBagInfoToZip(zip : ZipOutputStream) : Option[InputStream] = {
    zip.putNextEntry(new ZipEntry("bag-info.txt"))
    var s = ""
    s += "BagIt-Version: 0.97\n"
    s += "Tag-File-Character-Encoding: UTF-8\n"
    Some(new ByteArrayInputStream(s.getBytes("UTF-8")))
  }

  // List of all dataset (i.e. not BagIt) files and their checksums
  private def addManifestMD5ToZip(md5map: Map[String,MessageDigest], zip: ZipOutputStream) : Option[InputStream] = {
    zip.putNextEntry(new ZipEntry("manifest-md5.txt"))
    var s : String = ""
    md5map.foreach{
      case (filePath,md) => {
        val current = Hex.encodeHexString(md.digest())+" "+filePath+"\n"
        s = s + current
      }
    }
    Some(new ByteArrayInputStream(s.getBytes("UTF-8")))
  }

  private def addDataCiteMetadataToZip(zip: ZipOutputStream, dataset: Dataset, baseURL: String): Option[InputStream] = {
    zip.putNextEntry(new ZipEntry("metadata/datacite.xml"))
    var s = "<resource xsi:schemaLocation=\"http://datacite.org/schema/kernel-4 http://schema.datacite.org/meta/kernel-4/metadata.xsd\">\n"
    // https://support.datacite.org/docs/schema-40

    // Prep user data (DataCite v4 specifies Family, Given as name format)
    // TODO: Need to resolve how to present names - take last word as Family and rest Given unless specified elsewhere?
    var creatorName = dataset.author.fullName
    var creatorOrcid = ""
    userService.get(dataset.author.id) match {
      case Some(u: User) => {
        creatorName = u.fullName
        creatorOrcid = u.profile match {
          case Some(p: Profile) => p.orcidID.getOrElse("")
          case None => ""
        }
      }
      case None => {}
    }

    // ---------- REQUIRED FIELDS ----------
    // Identifier (DOI)
    // TODO: We don't have a DOI yet in most cases. It seems that DataCite v4+ requires one, however RDA does not.
    s += "<identifier identifierType=\"DOI\">:none</identifier>\n"

    // Creators
    s += "<creators>\n"
    s += "\t<creator>\n"
    s += "\t\t<creatorName>"+creatorName+"</creatorName>\n"
    if (creatorOrcid.length > 0) {
      s += "\t\t<nameIdentifier>"+creatorOrcid+"</nameIdentifier>\n"
      s += "\t\t<nameIdentifierScheme>ORCID</nameIdentifierScheme>\n"
    }
    s += "\t</creator>\n"
    s += "</creators>\n"

    // Title
    s += "<titles>\n\t<title>"+dataset.name+"</title>\n</titles>\n"

    // Publisher
    // TODO: Not sure Clowder is right here.
    s += "<publisher>Clowder</publisher>\n"

    // PublicationYear
    val yyyy = new SimpleDateFormat("yyyy").format(dataset.created)
    s += "<publicationYear>"+yyyy+"</publicationYear>\n"

    // ResourceType
    s += "<resourceType resourceTypeGeneral=\"Dataset\">Clowder Dataset</resourceType>\n"

    // ---------- RECOMMENDED/OPTIONAL FIELDS ----------

    // Description
    s += "<descriptions>\n\t<description descriptionType=\"Abstract\">"+dataset.description+"</description>\n</descriptions>\n"

    // Contributors (anyone else who provided files or metadata)
    val contribList = ListBuffer[String]()
    datasets.getUserMetadataJSON(dataset.id).foreach(md => {
      val mdAuth = md.toString
      if (mdAuth.length > 1 && mdAuth != creatorName) contribList += mdAuth
    })
    files.get(dataset.files).found.foreach(fi => {
      if (fi.author.fullName != creatorName) contribList += fi.author.fullName
      files.getUserMetadataJSON(fi.id).foreach(md => {
        val mdAuth = md.toString
        if (mdAuth.length > 1 && mdAuth != creatorName) contribList += mdAuth
      })
    })

    if (contribList.length > 0) {
      s += "<contributors>\n"
      contribList.distinct.foreach(name => s += "\t<contributor contributorType=\"\">"+name+"</contributor>\n")
      s += "</contributors>\n"
    }

    // Date (Created)
    val isoDate = new SimpleDateFormat("YYYY-MM-dd").format(dataset.created)
    s += "<dates>\n\t<date dateType=\"Created\">"+isoDate+"</date>\n</dates>\n"

    // AlternateIdentifier
    s += "<alternateIdentifier>"+dataset.id.stringify+"</alternateIdentifier>\n"

    // Format
    s += "<format>application/zip</format>\n"

    // Subjects
    if (dataset.tags.length > 0) {
      s += "<subjects>\n"
      dataset.tags.foreach(t => s += "\t<tag>"+t.name+"</tag>\n")
      s += "</subjects>\n"
    }

    // RelatedIdentifier (Clowder root URL)
    s += "<relatedIdentifier relatedIdentifierType=\"URL\" relationType=\"isSourceOf\">"+baseURL+"</relatedIdentifier>\n"

    // Size (can have many)
    s += "<sizes>\n\t<size>"+dataset.files.length.toString+" files</size>\n</sizes>\n"

    // Rights
    // TODO: Clean these up?
    val rURL = dataset.licenseData.m_licenseUrl
    val rightsURI = if (rURL.length > 0) " rightsURI=\""+rURL+"\"" else ""
    val rightsTxt = dataset.licenseData.m_licenseText
    s += "<rights"+rightsURI+">"+rightsTxt+"</rights>\n"

    // Version
    // Language - e.g. "en"
    // GeoLocation (R)
    // FundingReference

    s += "</resource>"
    Some(new ByteArrayInputStream(s.getBytes("UTF-8")))
  }

  private def addClowderXMLMetadataToZip(zip: ZipOutputStream, dataset: Dataset, baseURL: String): Option[InputStream] = {
    """
      | Generate Clowder XML format that can be used to recreate the dataset & files as accurately as possible.
      |
      | WILL BE RECREATED:
      | dataset name, description, metadata & tags
      | folders, files, file metadata & tags
      |
      | The files/folders/metadata are exported as files and do not need to be replicated here.
      |
      | WILL NOT BE RECREATED:
      | original author, creation date (included as metadata)
      | parent collection & space relationships
      | extraction event history (source URL included as metadata for reference)
    """
    zip.putNextEntry(new ZipEntry("metadata/clowder.xml"))
    var content = "<clowderDataset>\n"

    // Top-level dataset information
    content += s"\t<id>${dataset.id.stringify}</id>\n"
    content += s"\t<name>${dataset.name}</name>\n"
    content += s"\t<description>${dataset.description}</description>\n"
    content += s"\t<tags>${dataset.tags.mkString(",")}</tags>\n"

    // Original source information
    content += s"\t<source>\n"
    content += s"\t\t<authorId>${dataset.author.id.stringify}</authorId>\n"
    content += s"\t\t<authorName>${dataset.author.fullName}</authorName>\n"
    content += s"\t\t<authorEmail>${dataset.author.email.getOrElse("")}</authorEmail>\n"
    content += s"\t\t<creators>${dataset.creators.toString}</creators>\n"
    content += s"\t\t<created>${dataset.created.toString}</created>\n"
    content += s"\t\t<url>$baseURL</url>\n"
    content += s"\t</source>\n"

    content += "</clowderDataset>"
    Some(new ByteArrayInputStream(content.getBytes("UTF-8")))
  }

  // List of all BagIt, xml or non-dataset files and their checksums
  private def addTagManifestMD5ToZip(md5map : Map[String,MessageDigest],zip : ZipOutputStream) : Option[InputStream] = {
    zip.putNextEntry(new ZipEntry("tagmanifest-md5.txt"))
    var s : String = ""
    md5map.foreach{
      case (filePath,md) => {
        val current = Hex.encodeHexString(md.digest())+" "+filePath+"\n"
        s = s + current
      }
    }
    Some(new ByteArrayInputStream(s.getBytes("UTF-8")))
  }

  def download(id: UUID, compression: Int, tracking: Boolean) = PermissionAction(Permission.DownloadFiles, Some(ResourceRef(ResourceRef.dataset, id))) { implicit request =>
    implicit val user = request.user
    datasets.get(id) match {
      case Some(dataset) => {
        val bagit = play.api.Play.configuration.getBoolean("downloadDatasetBagit").getOrElse(true)
        val baseURL = controllers.routes.Datasets.dataset(id).absoluteURL(https(request))

        // Increment download count if tracking is enabled
        if (tracking) {
          datasets.incrementDownloads(id, user)
          sinkService.logDatasetDownloadEvent(dataset, user)
        }

        // Use custom enumerator to create the zip file on the fly
        // Use a 1MB in memory byte array
        Ok.chunked(enumeratorFromDataset(dataset,1024*1024, compression, bagit, baseURL, user, None, None)).withHeaders(
          CONTENT_TYPE -> "application/zip",
          CONTENT_DISPOSITION -> (FileUtils.encodeAttachment(dataset.name+ ".zip", request.headers.get("user-agent").getOrElse("")))
        )
      }
      // If the dataset wasn't found by ID
      case None => {
        NotFound
      }
    }
  }

  // Takes dataset ID and a comma-separated string of file UUIDs in the dataset and streams just those files as a zip
  def downloadPartial(id: UUID, fileList: String) = PermissionAction(Permission.DownloadFiles, Some(ResourceRef(ResourceRef.dataset, id))) { implicit request =>
    implicit val user = request.user
    datasets.get(id) match {
      case Some(dataset) => {
        val fileIDs = fileList.split(',').map(fid => new UUID(fid)).toList
        val bagit = play.api.Play.configuration.getBoolean("downloadDatasetBagit").getOrElse(true)
        val baseURL = controllers.routes.Datasets.dataset(id).absoluteURL(https(request))

        // Increment download count for each file
        fileIDs.foreach(fid => files.incrementDownloads(fid, user))

        // Use custom enumerator to create the zip file on the fly
        // Use a 1MB in memory byte array
        Ok.chunked(enumeratorFromDataset(dataset,1024*1024, -1, bagit, baseURL, user, Some(fileIDs), None)).withHeaders(
          CONTENT_TYPE -> "application/zip",
          CONTENT_DISPOSITION -> (FileUtils.encodeAttachment(dataset.name+ " (Partial).zip", request.headers.get("user-agent").getOrElse("")))
        )
      }
      // If the dataset wasn't found by ID
      case None => {
        NotFound
      }
    }
  }

  // Takes dataset ID and a folder ID in that dataset and streams just that folder and sub-folders as a zip
  def downloadFolder(id: UUID, folderId: UUID) = PermissionAction(Permission.DownloadFiles, Some(ResourceRef(ResourceRef.dataset, id))) { implicit request =>
    implicit val user = request.user
    datasets.get(id) match {
      case Some(dataset) => {
        val bagit = play.api.Play.configuration.getBoolean("downloadDatasetBagit").getOrElse(true)
        val baseURL = controllers.routes.Datasets.dataset(id).absoluteURL(https(request))


        // Increment download count for each file in folder
        folders.get(folderId) match {
          case Some(fo) => {
            fo.files.foreach(fid => files.incrementDownloads(fid, user))

            // Use custom enumerator to create the zip file on the fly
            // Use a 1MB in memory byte array
            Ok.chunked(enumeratorFromDataset(dataset,1024*1024, -1, bagit, baseURL, user, None, Some(folderId))).withHeaders(
              CONTENT_TYPE -> "application/zip",
              CONTENT_DISPOSITION -> (FileUtils.encodeAttachment(dataset.name+ " ("+fo.name+" Folder).zip", request.headers.get("user-agent").getOrElse("")))
            )
          }
          case None => NotFound
        }


      }
      // If the dataset wasn't found by ID
      case None => {
        NotFound
      }
    }
  }

  def updateAccess(id:UUID, access:String) = PermissionAction(Permission.PublicDataset, Some(ResourceRef(ResourceRef.dataset, id))) { implicit request =>
    implicit val user = request.user
    user match {
      case Some(loggedInUser) => {
        datasets.get(id) match {
          case Some(dataset) if !dataset.isTRIAL => {
            datasets.update(dataset.copy(status = access))
            events.addObjectEvent(user, id, dataset.name, EventType.UPDATE_DATASET_INFORMATION.toString)
            Ok(toJson(Map("status" -> "success")))
          }
          // If the dataset wasn't found by ID
          case _ => {
            InternalServerError("Update Access failed")
          }
        }
      }
      case None => {
        Unauthorized
      }
    }
  }

  def addFileEvent(id:UUID,  inFolder:Boolean, fileCount: Int ) = AuthenticatedAction {implicit request =>
    datasets.get(id) match{
      case Some(d) =>  {
        var eventType = if (inFolder) "add_file_folder" else "add_file"
        eventType = eventType + "_" + fileCount.toString
        events.addObjectEvent(request.user, id, d.name, eventType)
      }

      // we do not return an internal server error here since this function just add an event and won't influence the
      // following operations.
      case None =>  Logger.error("Dataset not found")
    }
    Ok(toJson("added new event"))
  }

  def copyDatasetToSpace(datasetId: UUID, spaceId: UUID) = PermissionAction(Permission.AddResourceToSpace, Some(ResourceRef(ResourceRef.space, spaceId))) { implicit request =>
    implicit val user = request.user
    user match {
      case Some(u) => {
        datasets.get(datasetId) match {
          case Some(dataset) => {
            if (u.id == dataset.author.id) {
              spaces.get(spaceId) match {
                case Some(space) => {
                  val d = Dataset(name = dataset.name, description = dataset.description, created = new Date(), author = dataset.author, licenseData = dataset.licenseData, spaces = List(spaceId), stats = dataset.stats)
                  datasets.insert(d) match {
                    case Some(id) => {
                      copyDatasetMetadata(dataset.id,UUID(id))
                      spaces.addDataset(d.id, spaceId)
                      relations.add(Relation(source = Node(datasetId.stringify, ResourceType.dataset), target = Node(d.id.stringify, ResourceType.dataset)))
                      events.addSourceEvent(request.user, d.id, d.name, space.id, space.name, EventType.ADD_DATASET_SPACE.toString)

                      dataset.folders.map { folder =>
                        copyFolders(folder, datasetId, "dataset", d.id)
                      }

                      files.get(dataset.files).found.foreach(file => {
                        val original_thumbnail = file.thumbnail_id
                        val newFile = models.File(loader_id = file.loader_id, filename = file.filename, author = file.author,
                          uploadDate = file.uploadDate, contentType = file.contentType, length = file.length,
                          loader = file.loader, showPreviews = file.showPreviews,
                          description = file.description, licenseData = file.licenseData, stats = file.stats, status = file.status)
                        files.save(newFile)
                        FileUtils.copyFileThumbnail(file,newFile)
                        FileUtils.copyFileMetadata(file,newFile)
                        FileUtils.copyFilePreviews(file,newFile)
                        datasets.addFile(UUID(id), newFile)
                        relations.add(Relation(source = Node(file.id.stringify, ResourceType.file), target = Node(newFile.id.stringify, ResourceType.file)))
                      })

                      datasets.createThumbnail(d.id)
                      Ok(toJson(Map("newDatasetId" -> d.id.stringify)))
                    }
                    case None => BadRequest(s"Unable to copy the dataset with id $datasetId to space with id: $spaceId")
                  }
                }
                case None => BadRequest(s"No space found with id: + $spaceId.")
              }
            } else {
              BadRequest("You don't have permission to copy the dataset.")
            }
          }
          case None => BadRequest(s"No dataset  found with id: $datasetId")
        }
      }
      case None => BadRequest("You need to be logged in to copy a dataset to a space.")
    }
  }

  private def copyDatasetMetadata(originalDatasetID : UUID, newDatasetID : UUID) {
    val originalMetadata = metadataService.getMetadataByAttachTo(ResourceRef(ResourceRef.dataset, originalDatasetID))
    for ( md <- originalMetadata){
      if (md.creator.typeOfAgent == "extractor"){
        val content = md.content
        val creator = ExtractorAgent(id = UUID.generate(),
          extractorId = Some(new URL("http://clowder.ncsa.illinois.edu/extractors/deprecatedapi")))

        // check if the context is a URL to external endpoint
        val contextURL: Option[URL] = None

        // check if context is a JSON-LD document
        val contextID: Option[UUID] = None

        // when the new metadata is added
        val createdAt = new Date()

        //parse the rest of the request to create a new models.Metadata object
        val attachedTo = ResourceRef(ResourceRef.dataset, newDatasetID)
        val version = None
        val metadata = models.Metadata(UUID.generate, attachedTo, contextID, contextURL, createdAt, creator,
          content, version)
        //add metadata to mongo
        metadataService.addMetadata(metadata)
      }
    }
  }

  private def copyFolders(id: UUID, parentId: UUID, parentType: String, datasetId: UUID) {
    folders.get(id) match {
      case Some(folder) => {
        val newFiles = ListBuffer[UUID]()
        files.get(folder.files).found.foreach(file => {
          val original_thumbnail = file.thumbnail_id
          val newFile = models.File(loader_id = file.loader_id, filename = file.filename, author = file.author,
            uploadDate = file.uploadDate, contentType = file.contentType, length = file.length,
            loader = file.loader, showPreviews = file.showPreviews, previews = file.previews, thumbnail_id = file.thumbnail_id,
            description = file.description, licenseData = file.licenseData, stats = file.stats, status = file.status)
          files.save(newFile)
          FileUtils.copyFileMetadata(file,newFile)
          FileUtils.copyFilePreviews(file,newFile)
          relations.add(Relation(source = Node(file.id.stringify, ResourceType.file), target = Node(newFile.id.stringify, ResourceType.file)))
          original_thumbnail match {
            case Some(tnail) => {
              files.updateThumbnail(file.id,UUID(tnail))
              files.updateThumbnail(newFile.id,UUID(tnail))
            }
            case None =>
          }
          relations.add(Relation(source = Node(file.id.stringify, ResourceType.file), target = Node(newFile.id.stringify, ResourceType.file)))
          newFiles += newFile.id
        })

        val newFolder = Folder(author = folder.author, created = new Date(), name = folder.name, displayName = folder.displayName,
          files = newFiles.toList, folders = List.empty, parentId = parentId, parentType = parentType.toLowerCase(), parentDatasetId = datasetId)
        folders.insert(newFolder)
        if(parentType == "dataset"){
          datasets.addFolder(datasetId, newFolder.id)
        } else {
          folders.addSubFolder(parentId, newFolder.id)
        }

        relations.add(Relation(source= Node(folder.id.stringify, ResourceType.folder), target = Node(newFolder.id.stringify, ResourceType.folder)))
        folder.folders.map( f => copyFolders(f, newFolder.id, "folder", datasetId))
      }

    }
  }

  def users(id: UUID) = PermissionAction(Permission.ViewDataset, Some(ResourceRef(ResourceRef.dataset, id))) { implicit request =>
    implicit val user = request.user
    val spaceTitle: String = Messages("space.title")

    datasets.get(id) match {
      case Some(dataset) => {
        var userList: List[User] = List.empty
        var userListSpaceRoleTupleMap = Map[UUID, List[Tuple2[String, String]]]() // Map( User-id -> List((Space-name,Role-name)) )

        // Setup userList, add all users of all spaces associated with the dataset
        dataset.spaces.foreach { spaceId =>
          spaces.get(spaceId) match {
            case Some(spc) => userList = spaces.getUsersInSpace(spaceId, None) ::: userList
            case None => NotFound(s"Error: No $spaceTitle found for $id.")
          }
        }
        userList = userList.distinct.sortBy(_.fullName.toLowerCase)

        if (userList.nonEmpty) {
          Ok(Json.toJson(userList.map(user => Json.obj(
            "@context" -> Json.toJson(
              Map(
                "firstName" -> Json.toJson("http://schema.org/Person/givenName"),
                "lastName" -> Json.toJson("http://schema.org/Person/familyName"),
                "email" -> Json.toJson("http://schema.org/Person/email"),
                "affiliation" -> Json.toJson("http://schema.org/Person/affiliation")
              )
            ),
            "id" -> user.id.stringify,
            "firstName" -> user.firstName,
            "lastName" -> user.lastName,
            "fullName" -> user.fullName,
            "email" -> user.email,
            "avatar" -> user.getAvatarUrl(),
            "identityProvider" -> user.format(true)
          ))))
        }
        else NotFound(s"Error: No users found for $id.")
      }
      case None => NotFound(s"Error: No dataset with $id found.")
    }

  }
}

object ActivityFound extends Exception {}
