package util

import java.io.{File => JFile}
import java.net.URL
import java.util.Date

import collection.JavaConversions._
import api.UserRequest
import controllers.Utils
import fileutils.FilesUtils
import models._
import org.apache.commons.codec.digest.DigestUtils
import play.api.Logger
import play.api.Play._
import play.api.libs.Files
import play.api.libs.json._
import play.api.mvc.MultipartFormData
import play.libs.Akka
import services._

import scala.collection.mutable
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

import javax.mail.internet.MimeUtility
import java.net.URLEncoder

object FileUtils {
  val appConfig: AppConfigurationService = DI.injector.getInstance(classOf[AppConfigurationService])

  lazy val files: FileService = DI.injector.getInstance(classOf[FileService])
  lazy val datasets: DatasetService = DI.injector.getInstance(classOf[DatasetService])
  lazy val dtsrequests:ExtractionRequestsService = DI.injector.getInstance(classOf[ExtractionRequestsService])
  lazy val sqarql: RdfSPARQLService = DI.injector.getInstance(classOf[RdfSPARQLService])
  lazy val metadataService: MetadataService = DI.injector.getInstance(classOf[MetadataService])
  lazy val contextService: ContextLDService = DI.injector.getInstance(classOf[ContextLDService])
  lazy val events: EventService = DI.injector.getInstance(classOf[EventService])
  lazy val userService: UserService = DI.injector.getInstance(classOf[UserService])
  lazy val folders: FolderService = DI.injector.getInstance(classOf[FolderService])
  lazy val previews : PreviewService = DI.injector.getInstance(classOf[PreviewService])
  lazy val thumbnails : ThumbnailService = DI.injector.getInstance(classOf[ThumbnailService])
  lazy val routing : ExtractorRoutingService = DI.injector.getInstance(classOf[ExtractorRoutingService])
  lazy val sinkService : EventSinkService = DI.injector.getInstance(classOf[EventSinkService])


  def getContentType(filename: Option[String], contentType: Option[String]): String = {
    getContentType(filename.getOrElse(""), contentType)
  }

  def getContentType(filename: String, contentType: Option[String]): String = {
    var ct = contentType.getOrElse(play.api.http.ContentTypes.BINARY)
    if (ct == play.api.http.ContentTypes.BINARY) {
      ct = play.api.libs.MimeTypes.forFileName(filename).getOrElse(play.api.http.ContentTypes.BINARY)
    }
    ct
  }

  // ----------------------------------------------------------------------
  // START File upload
  // ----------------------------------------------------------------------

  // LOGIC FLOW
  // for all files uploaded {
  //   create file object
  //   associate relevant metadata to file
  //   new thread {
  //     upload file
  //     process bytes if needed (xml/rdf)
  //     process file (rabbitmq, index)
  //     process dataset (associate, rabbitmq, index)
  //     delete temp file
  //   }
  // }
  //
  // for all json objects with path {
  //   check path ok
  //   create file object
  //   associate relevant metadata to file
  //   new thread {
  //     process bytes if needed (xml/rdf)
  //     process file (rabbitmq, index)
  //     process dataset (associate, rabbitmq, index)
  //   }
  // }
  //

  /**
   * Upload files from a request and attach metadata if necessary. Returns list of uploaded files.
   */
  def uploadFilesMultipart(request: UserRequest[MultipartFormData[Files.TemporaryFile]],
                           dataset: Option[Dataset] = None, folder:Option[Folder] = None,
                           key: String = "", index: Boolean = true,
                           showPreviews: String = "DatasetLevel", originalZipFile: String = "",
                           flagsFromPrevious: String = "", intermediateUpload: Boolean = false,
                           runExtractors: Boolean = true, insertDTSRequests: Boolean = false, apiKey: Option[String]) : List[File] = {
    if (request.user.isEmpty) {
      Logger.error ("No user object given, should not happen.")
      return List.empty[File]
    }

    val user = request.user.get
    val creator_url = api.routes.Users.findById(user.id).absoluteURL(Utils.https(request))(request)
    val creator = UserAgent(id = UUID.generate(), user = user, userId = Some(new URL(creator_url)))

    // Extract path information from requests for later
    val clowderurl = Utils.baseUrl(request)
    val clientIP = request.remoteAddress
    val serverIP = request.host

    // list of files to process
    val file_list = if (key == "") {
      // No specific key provided, so attempt to upload every file
      request.body.files
    } else {
      // Only upload the specific key we were given (e.g. "File")
      request.body.file(key) match {
        case Some(f) => List[MultipartFormData.FilePart[Files.TemporaryFile]](f)
        case None => {
          Logger.debug(s"Key ${key} not found in request. No file uploaded.")
          return List.empty[File]
        }
      }
    }
    val multipleFile = request.body.asFormUrlEncoded.getOrElse("multiple", null) match {
      case null => true
      case m =>  m(0)  == "true"
    }

    // container for list of uploaded files
    val uploadedFiles = new mutable.MutableList[File]()

    // ------------------------------------------------------------
    // PROCESS UPLOADED FILES
    // ------------------------------------------------------------

    // Get file(s) attached and iterate, uploading each
    file_list.foreach { f =>
      // Check for metadata using filename as key; if not found, check using file's key (e.g. "File")
      val metadata: Option[Seq[String]] = request.body.dataParts.get(f.filename) match {
        case Some(md) => Some(md)
        case None => {
          request.body.dataParts.get(f.key) match {
            case Some(md) => {
              // If we find "@context" field we assume this is json metadata and not a file path
              // TODO: Revisit this, can we also check for a "path" field and improve?
              var foundMd: Option[Seq[String]] = None
              for (fobj <- md) {
                Json.parse(fobj) \ "@context" match {
                  case v: JsUndefined => None
                  case v: JsObject => foundMd = Some(md)
                  case v: JsString => foundMd = Some(md)
                }
              }
              foundMd
            }
            case None => None
          }
        }
      }
      processFileMultipart(f, metadata, user, creator, clowderurl, dataset, folder, key, index, showPreviews,
        originalZipFile, flagsFromPrevious, intermediateUpload, multipleFile, runExtractors, apiKey).foreach(uploadedFiles += _ )
    }

    // ------------------------------------------------------------
    // PROCESS PATH SPECIFIED FILES
    // ------------------------------------------------------------
    // Now check whether we have any local file paths to handle
    // file={ "path": "filepath", "md": { "field1":"val", "field2": 100 }, "dataset": UUID }
    for ((k,v) <- request.body.dataParts) {
      if (k == "file") {
        for (fobj <- v) {
          val jsv = Json.parse(fobj)
          jsv \ "path" match {
            case x:JsString => {
              val path = Parsers.parseString(x)
              processPath(path, jsv, user, creator, clowderurl, dataset, folder, key, index, showPreviews,
                originalZipFile, flagsFromPrevious, intermediateUpload, runExtractors, apiKey).foreach(uploadedFiles += _)
            }
            case _ => {}
          }
        }
      }
    }

    /*---- Insert DTS Request to database---*/
    if (insertDTSRequests) {
      uploadedFiles.foreach { file =>
        dtsrequests.insertRequest(serverIP, clientIP, file.filename, file.id, file.contentType, file.length, file.uploadDate)
      }
    }

    uploadedFiles.toList
  }

  // LOGIC FLOW
  // for all json objects {
  //   if key == fileurl || url || weburl {
  //     create file object
  //     add source to metadata
  //     associate relevant metadata to file
  //     new thread {
  //       upload url stream
  //       process bytes if needed (xml/rdf)
  //       process file (rabbitmq, index)
  //       process dataset (associate, rabbitmq, index)
  //       delete temp file
  //     }
  //   }
  //   if key == path {
  //     check path ok
  //     create file object
  //     associate relevant metadata to file
  //     new thread {
  //       process bytes if needed (xml/rdf)
  //       process file (rabbitmq, index)
  //       process dataset (associate, rabbitmq, index)
  //     }
  //   }
  // }
  //
  def processFileDuringCopy(copiedFile : models.File, metadata: Option[Seq[String]],
                            user: User, creator: Agent, clowderurl: String,
                            dataset: Option[Dataset] = None, folder: Option[Folder] = None,
                            key: String = "", index: Boolean = true,
                            showPreviews: String = "DatasetLevel", originalZipFile: String = "",
                            flagsFromPrevious: String = "", intermediateUpload: Boolean = false, multipleFile: Boolean,
                            runExtractors: Boolean = true, apiKey: Option[String]) : Option[File] = {

    files.setStatus(copiedFile.id, FileStatus.UPLOADED)
    processFile(copiedFile, clowderurl, index, flagsFromPrevious, showPreviews, dataset, runExtractors, apiKey)
    processDataset(copiedFile, dataset, folder, clowderurl, user, index, runExtractors, apiKey)
    files.setStatus(copiedFile.id, FileStatus.PROCESSED)
    Some(copiedFile)
  }

  def copyFileMetadata(originalFile : models.File, copiedFile : models.File) {
    val mds = metadataService.getMetadataByAttachTo(ResourceRef(ResourceRef.file, originalFile.id))
    for (md <- mds){
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
        val attachedTo = ResourceRef(ResourceRef.file, copiedFile.id)
        val version = None
        val metadata = models.Metadata(UUID.generate, attachedTo, contextID, contextURL, createdAt, creator,
          content, version)

        //add metadata to mongo
        metadataService.addMetadata(metadata)
      }
    }
  }

  def copyFileThumbnail(originalFile : models.File, copiedFile : models.File): Unit = {
    val originalFileThumbnail = originalFile.thumbnail_id
    originalFileThumbnail match {
      case Some(thumbnail) => {
        files.updateThumbnail(copiedFile.id, UUID(thumbnail))
      }
      case None => Logger.info("File does not have thumbnail")
    }
  }

  def copyFilePreviews(originalFile : models.File, copiedFile : models.File) {
    val previewsFromDB = previews.findByFileId(originalFile.id)
    for (pv <- previewsFromDB) {
      previews.get(pv.id) match {
        case Some(currentPreview) => {
          val extractorId = currentPreview.extractor_id
          val currentPreviewTitle = currentPreview.title
          val currentMetadata = currentPreview.jsonldMetadata
          previews.attachToFile(pv.id,originalFile.id,extractorId,Json.toJson(currentMetadata))
        }
        case None => Logger.error("No preview found during copy for original preview with id " + pv.id)
      }
    }
  }

  def uploadFilesJSON(request: UserRequest[JsValue],
                        dataset: Option[Dataset] = None, folder:Option[Folder] = None,
                        key: String = "", index: Boolean = true,
                        showPreviews: String = "DatasetLevel", originalZipFile: String = "",
                        flagsFromPrevious: String = "", intermediateUpload: Boolean = false,
                        runExtractors: Boolean = true, insertDTSRequests: Boolean = false, apiKey: Option[String]) : List[File] = {

    if (request.user.isEmpty) {
      Logger.error ("No user object given, should not happen.")
      return List.empty[File]
    }

    val user = request.user.get
    val creator_url = api.routes.Users.findById(user.id).absoluteURL(Utils.https(request))(request)
    val creator = UserAgent(id = UUID.generate(), user=user.getMiniUser, userId = Some(new URL(creator_url)))

    // Extract path information from requests for later
    val clowderurl = Utils.baseUrl(request)
    val clientIP = request.remoteAddress
    val serverIP = request.host

     // container for list of uploaded files
    val uploadedFiles = new mutable.MutableList[File]()

    val fileList = request.body match {
      case v: JsArray => request.body.asOpt[List[JsObject]]
      case v: JsObject => Some(List[JsObject](v))
      case _ => None
    }

    // Now check whether we have any local file paths to handle
    // [ { "path": "filepath", "md": { "field1":"val", "field2": 100 }, "dataset": UUID } ]
    // [ { "fileurl": "url", md: ..., "dataset": UUID } ]
    // [ { "url": "url", md: ..., "dataset": UUID } ]
    fileList.foreach{ _.filter(key == "" || _.keys.contains(key)).foreach { jsv =>
      val url = Try {
        jsv \ "fileurl" match {
          case u: JsString => Some(new URL(Parsers.parseString(u)))
          case _ => jsv \ "weburl" match {
            case u: JsString => Some(new URL(Parsers.parseString(u)))
            case _ => jsv \ "url" match {
              case u: JsString => Some(new URL(Parsers.parseString(u)))
              case _ => None
            }
          }
        }
      }
      if (url.isSuccess && url.get.isDefined) {
        url.get.foreach(processURL(_, jsv, user, creator, clowderurl, dataset, folder, key, index, showPreviews, originalZipFile, flagsFromPrevious, intermediateUpload, runExtractors, apiKey).foreach(uploadedFiles += _))
      } else if (jsv.keys.contains("path")) {
        val path = Parsers.parseString(jsv \ "path")
        processPath(path, jsv, user, creator, clowderurl, dataset, folder, key, index, showPreviews, originalZipFile, flagsFromPrevious, intermediateUpload, runExtractors, apiKey).foreach(uploadedFiles += _)
      }
    }}

    /*---- Insert DTS Request to database---*/
    if (insertDTSRequests) {
      uploadedFiles.foreach { file =>
        dtsrequests.insertRequest(serverIP, clientIP, file.filename, file.id, file.contentType, file.length, file.uploadDate)
      }
    }

    uploadedFiles.toList
  }

  // process a file from a bagit zipfile upload
  def processBagFile(file: java.io.File, filename: String, user: User, dataset: Dataset, folderId: Option[String]): Option[File] = {
    // Save file bytes
    ByteStorageService.save(file, "uploads") match {
      case Some((loader_id, loader, length)) => {
        val fileobj = File(UUID.generate, loader_id, filename, filename, user, new Date(), getContentType(filename, None),
          file.length, loader, licenseData=License.fromAppConfig, stats=new Statistics(), status=FileStatus.PROCESSED.toString)
        files.save(fileobj)
        appConfig.incrementCount('files, 1)
        appConfig.incrementCount('bytes, file.length)

        // Add to dataset/folder if given
        folderId match {
          case Some(fid) => folders.addFile(UUID(fid), fileobj.id)
          case None => datasets.addFile(dataset.id, fileobj)
        }
        Some(fileobj)
      }
      case None => {
        Logger.error("Unable to save file bytes to Clowder.")
        None
      }
    }
  }

  // process a single uploaded file
  private def processFileMultipart(f: MultipartFormData.FilePart[Files.TemporaryFile], metadata: Option[Seq[String]],
                          user: User, creator: Agent, clowderurl: String,
                          dataset: Option[Dataset] = None, folder:Option[Folder] = None,
                          key: String = "", index: Boolean = true,
                          showPreviews: String = "DatasetLevel", originalZipFile: String = "",
                          flagsFromPrevious: String = "", intermediateUpload: Boolean = false, multipleFile:Boolean,
                          runExtractors: Boolean = true, apiKey: Option[String]): Option[File] = {
    val file = File(UUID.generate(), "", f.filename, f.filename, user, new Date(),
      FileUtils.getContentType(f.filename, f.contentType), f.ref.file.length(), "",
      isIntermediate = intermediateUpload, showPreviews = showPreviews,
      licenseData = License.fromAppConfig(), stats = new Statistics(), status = FileStatus.CREATED.toString)
    files.save(file)
    Logger.debug(s"created file ${file.id}")

    associateMetaData(creator, file, metadata, clowderurl, apiKey, Some(user))
    associateDataset(file, dataset, folder, user, multipleFile)

    // process rest of file in background
    val fileExecutionContext: ExecutionContext = Akka.system().dispatchers.lookup("akka.actor.contexts.file-processing")
    Future {
      try {
        saveFile(file, f.ref.file, originalZipFile, clowderurl, apiKey, Some(user)).foreach { fixedfile =>
          processFileBytes(fixedfile, f.ref.file, dataset)
          files.setStatus(fixedfile.id, FileStatus.UPLOADED)
          sinkService.logFileUploadEvent(fixedfile, dataset, Option(user))
          processFile(fixedfile, clowderurl, index, flagsFromPrevious, showPreviews, dataset, runExtractors, apiKey)
          processDataset(file, dataset, folder, clowderurl, user, index, runExtractors, apiKey)
          files.setStatus(fixedfile.id, FileStatus.PROCESSED)
        }
      } finally {
        f.ref.clean()
      }
    }(fileExecutionContext)

    Some(file)
  }

  // process a single URL
  private def processURL(url: URL, jsv: JsValue, user: User, creator: Agent, clowderurl: String,
                         dataset: Option[Dataset] = None, folder:Option[Folder] = None,
                         key: String = "", index: Boolean = true,
                         showPreviews: String = "DatasetLevel", originalZipFile: String = "",
                         flagsFromPrevious: String = "", intermediateUpload: Boolean = false,
                         runExtractors: Boolean = true, apiKey: Option[String]): Option[File] = {
    val fileds = jsv \ "dataset" match {
      case d: JsString if dataset.isEmpty => datasets.get(UUID(Parsers.parseString(d)))
      case _ => dataset
    }

    // craete the file object
    val path = url.getPath
    val filename = path.slice(path.lastIndexOfSlice("/")+1, path.length)
    val file = File(UUID.generate(), path, filename, filename, user, new Date(),
      FileUtils.getContentType(filename, None), -1, "",
      isIntermediate=intermediateUpload, showPreviews=showPreviews,
      licenseData=License.fromAppConfig(), stats = new Statistics(), status = FileStatus.CREATED.toString)
    files.save(file)

    // extract metadata
    // TODO should really be using content not just the objects
//    val source = s"""{ "@context": { "source": "http://purl.org/dc/terms/source" }, "content": { "source": "${url.toString}" } }"""
    val source = s"""{ "@context": { "source": "http://purl.org/dc/terms/source" }, "source": "${url.toString}" }"""
    val metadata = jsv \ "md" match {
      case x: JsUndefined => {
        jsv \ "metadata" match {
          case y: JsUndefined => Some(Seq(source))
          case y => Some(Seq(Json.stringify(y), source))
        }
      }
      case x => Some(Seq(Json.stringify(x), source))
    }
    associateMetaData(creator, file, metadata, clowderurl, apiKey, Some(user))
    associateDataset(file, fileds, folder, user)

    // process rest of file in background
    val fileExecutionContext: ExecutionContext = Akka.system().dispatchers.lookup("akka.actor.contexts.file-processing")
    Future {
      saveURL(file, url, clowderurl, apiKey, Some(user)).foreach { fixedfile =>
        processFileBytes(fixedfile, new java.io.File(path), fileds)
        files.setStatus(fixedfile.id, FileStatus.UPLOADED)
        processFile(fixedfile, clowderurl, index, flagsFromPrevious, showPreviews, fileds, runExtractors, apiKey)
        processDataset(file, fileds, folder, clowderurl, user, index, runExtractors, apiKey)
        files.setStatus(fixedfile.id, FileStatus.PROCESSED)
      }
    }(fileExecutionContext)

    Some(file)
  }

  // process a single local path
  private def processPath(path: String, jsv: JsValue, user: User, creator: Agent, clowderurl: String,
                          dataset: Option[Dataset] = None, folder:Option[Folder] = None,
                          key: String = "", index: Boolean = true,
                          showPreviews: String = "DatasetLevel", originalZipFile: String = "",
                          flagsFromPrevious: String = "", intermediateUpload: Boolean = false,
                          runExtractors: Boolean = true, apiKey: Option[String]): Option[File] = {
    val fileds = jsv \ "dataset" match {
      case d: JsString if dataset.isEmpty => datasets.get(UUID(Parsers.parseString(d)))
      case _ => dataset
    }

    // getStringList returns a java.util.List as opposed to the kind of List we want, thus the conversion
    val sourcelist = play.api.Play.configuration.getStringList("filesystem.sourcepaths").map(_.toList).getOrElse(List.empty[String])

    // Is the current path included in the source whitelist?
    if (sourcelist.exists(s => path.startsWith(s))) {
      Logger.debug(path + " is whitelisted for upload")

      // craete the file object
      val filename = path.slice(path.lastIndexOfSlice("/")+1, path.length)
      val length = new java.io.File(path).length()
      val loader = classOf[services.filesystem.DiskByteStorageService].getName
      val file = File(UUID.generate(), path, filename, filename, user, new Date(),
        FileUtils.getContentType(filename, None), length, loader,
        isIntermediate=intermediateUpload, showPreviews=showPreviews,
        licenseData=License.fromAppConfig(), stats = new Statistics(), status = FileStatus.CREATED.toString)
      files.save(file)

      // extract metadata
      val metadata = jsv \ "md" match {
        case x: JsUndefined => {
          jsv \ "metadata" match {
            case y: JsUndefined => None
            case y => Some(Seq(Json.stringify(y)))
          }
        }
        case x => Some(Seq(Json.stringify(x)))
      }


      associateMetaData(creator, file, metadata, clowderurl, apiKey, Some(user))
      associateDataset(file, fileds, folder, user)

      // process rest of file in background
      val fileExecutionContext: ExecutionContext = Akka.system().dispatchers.lookup("akka.actor.contexts.file-processing")
      Future {
        savePath(file, path).foreach { fixedfile =>
          processFileBytes(fixedfile, new java.io.File(path), fileds)
          files.setStatus(fixedfile.id, FileStatus.UPLOADED)
          processFile(fixedfile, clowderurl, index, flagsFromPrevious, showPreviews, fileds, runExtractors, apiKey)
          processDataset(file, fileds, folder, clowderurl, user, index, runExtractors, apiKey)
          files.setStatus(fixedfile.id, FileStatus.PROCESSED)
        }
      }(fileExecutionContext)

      Some(file)
    } else {
      Logger.debug(path +" is not a whitelisted upload location")
      None
    }
  }

  /** Associate all metadata with file, added by agent */
  private def associateMetaData(agent: Agent, file: File, mds: Option[Seq[String]], requestHost: String,
    apiKey: Option[String], user: Option[User]): Unit = {
    mds.getOrElse(List.empty[String]).foreach { md =>
      // TODO: should do a metadata validity check first
      // Extract context from metadata object and remove it so it isn't repeated twice
      val jsonmd = Json.parse(md)
      val context: JsValue = jsonmd \ "@context"
      val content = jsonmd.as[JsObject] - "@context"

      // check if the context is a URL to external endpoint
      val contextURL: Option[URL] = context.asOpt[String].map(new URL(_))

      // check if context is a JSON-LD document
      val contextID: Option[UUID] = context match {
        case js: JsObject => Some(contextService.addContext(new JsString("context name"), js))
        case js: JsArray => Some(contextService.addContext(new JsString("context name"), js))
        case _ => None
      }

      // when the new metadata is added
      val createdAt = new Date()

      //parse the rest of the request to create a new models.Metadata object
      val attachedTo = ResourceRef(ResourceRef.file, file.id)
      val version = None
      val metadata = models.Metadata(UUID.generate(), attachedTo, contextID, contextURL, createdAt, agent, content, version)

      // add metadata to mongo
      val metadataId = metadataService.addMetadata(metadata)
      val mdMap = metadata.getExtractionSummary

      //send RabbitMQ message
      routing.metadataAddedToResource(metadataId, metadata.attachedTo, mdMap, requestHost, apiKey, user)
    }
  }

  /** dataset processning */
  private def associateDataset(file: File, dataset: Option[Dataset], folder: Option[Folder], user: User, multipleFile:Boolean=true): Unit = {
    // add metadata to dataset
    dataset.foreach{ds =>
      // add file to folder or dataset
      folder match {
        case Some(folder) => {
          if(!multipleFile) {
            events.addObjectEvent(Some(user), ds.id, ds.name, EventType.ADD_FILE_FOLDER.toString)
          }
          folders.addFile(folder.id, file.id)
        }
        case None => {
          if(!multipleFile) {
            events.addObjectEvent(Some(user), ds.id, ds.name, EventType.ADD_FILE.toString)
          }
          datasets.addFile(ds.id, file)
        }
      }
    }
  }

  /** stream the data from the uploaded path to final resting spot, this can be slow */
  private def saveFile(file: File, path: java.io.File, originalZipFile: String, host: String, apiKey: Option[String],
    user: Option[User]): Option[File] = {
    // If intermediate upload, get flags from originalIdAndFlags
    var nameOfFile = file.filename
    if (!file.isIntermediate) {
      // Parse .ptm filenames into component flags
      if (nameOfFile.toLowerCase().endsWith(".ptm")) {
        val thirdSeparatorIndex = nameOfFile.indexOf("__")
        if (thirdSeparatorIndex >= 0) {
          nameOfFile = nameOfFile.substring(thirdSeparatorIndex + 2)
        }
      }
      Logger.debug("Uploading file " + nameOfFile)
    }

    // fix up special case upload of zipfile
    var fileType = file.contentType
    if (fileType.contains("/zip") || fileType.contains("/x-zip") || file.filename.toLowerCase().endsWith(".zip")) {
      fileType = FilesUtils.getMainFileTypeOfZipFile(path, file.filename, "file")
      if (fileType.startsWith("ERROR: ")) {
        Logger.error(fileType.substring(7))
        fileType = file.contentType
      } else if (fileType.equals("imageset/ptmimages-zipped") || fileType.equals("imageset/ptmimages+zipped") || fileType.equals("multi/files-ptm-zipped")) {
        if (fileType.equals("multi/files-ptm-zipped")) {
          fileType = "multi/files-zipped"
        }

        val thirdSeparatorIndex = file.filename.indexOf("__")
        if (thirdSeparatorIndex >= 0) {
          nameOfFile = file.filename.substring(thirdSeparatorIndex + 2)
        }
        files.setContentType(file.id, fileType)
      }
    }

    // get the real user
    val realUser = if (!originalZipFile.equals("")) {
      files.get(new UUID(originalZipFile)) match {
        case Some(originalFile) => userService.findById(originalFile.id).fold(file.author)(_.getMiniUser)
        case None => file.author
      }
    } else {
      file.author
    }

    // actually save the file
    ByteStorageService.save(path, "uploads") match {
      case Some((loader_id, loader, length)) => {
        files.get(file.id) match {
          case Some(f) => {
            val fixedfile = f.copy(filename=nameOfFile, contentType=fileType, loader=loader, loader_id=loader_id, length=length, author=realUser)
            files.save(fixedfile)
            //Update counts
            appConfig.incrementCount('files, 1)
            appConfig.incrementCount('bytes, f.length)
            Logger.debug("Uploading Completed")
            Some(fixedfile)
          }
          case None => {
            Logger.error(s"File $loader_id was not found anymore")
            None
          }
        }
      }
      case None => {
        Logger.error(s"Could not save bytes, deleting file ${file.id}")
        files.removeFile(file.id, host, apiKey, user)
        None
      }
    }
  }

  /** Fix file object based on path file, no uploading just compute sha512 */
  private def savePath(file: File, path: String): Option[File] = {
    files.get(file.id) match {
      case Some(f) => {
        //Update counts
        appConfig.incrementCount('files, 1)
        appConfig.incrementCount('bytes, f.length)
        return Some(f)
      }
      case None => {
        Logger.error(s"File ${file.id} was not found anymore")
        None
      }
    }
  }

  /** Fix file object based on path file, no uploading just compute sha512 */
  private def saveURL(file: File, url: URL, host: String, apiKey: Option[String], user: Option[User]): Option[File] = {
    // actually save the file
    val conn = url.openConnection()

    ByteStorageService.save(conn.getInputStream, "uploads", conn.getContentLengthLong) match {
      case Some((loader_id, loader, length)) => {
        files.get(file.id) match {
          case Some(f) => {
            // if header's content type is application/octet-stream leave content type as the one based on file name,
            // otherwise replace with value from header.
            val fixedFile = if (conn.getContentType == play.api.http.ContentTypes.BINARY) {
              f.copy(loader = loader, loader_id = loader_id, length = length)
            } else {
              f.copy(contentType = conn.getContentType, loader = loader, loader_id = loader_id, length = length)
            }
            files.save(fixedFile)
            appConfig.incrementCount('files, 1)
            appConfig.incrementCount('bytes, f.length)
            Logger.debug("Uploading Completed")
            Some(fixedFile)
          }
          case None => {
            Logger.error(s"File $loader_id was not found anymore")
            None
          }
        }
      }
      case None => {
        Logger.error(s"Could not save bytes, deleting file ${file.id}")
        files.removeFile(file.id, host, apiKey, user)
        None
      }
    }
  }

  /**
   * Process the bytes on disk, send them as xml/rdf and store a copy. This will work with the original
   * data after it has been send to Clowdders storage area.
   */
  private def processFileBytes(file: File, fp: java.io.File, dataset: Option[Dataset]): Unit = {
    if (!file.isIntermediate) {
      // store the file
      current.plugin[FileDumpService].foreach {
        _.dump(DumpOfFile(fp, file.id.toString(), file.filename))
      }

      // for metadata files
      if (file.contentType.equals("application/xml") || file.contentType.equals("text/xml")) {
        val xmlToJSON = FilesUtils.readXMLgetJSON(fp)
        Logger.debug("xmlmd=" + xmlToJSON)

        // add xml as xml metadata
        // TODO is this still valid?
        files.addXMLMetadata(file.id, xmlToJSON)

        //add file to RDF triple store if triple store is used
        configuration.getString("userdfSPARQLStore").getOrElse("no") match {
          case "yes" => sqarql.addFileToGraph(file.id)
          case _ => {}
        }
      }
    }
  }

  /** process the file, index, rabbitmq, etc */
  private def processFile(file: File, clowderurl: String, index: Boolean, idAndFlags: String, showPreviews: String,
    dataset: Option[Dataset], runExtractors: Boolean = true, apiKey: Option[String]): Unit = {
    // TODO this not work correctly, filename is already chopped, if not done earlier this will fail on second time processing, this needs to be metadata of the file
//    var flags = ""
//    if (nameOfFile.toLowerCase().endsWith(".ptm")) {
//      var thirdSeparatorIndex = nameOfFile.indexOf("__")
//      if (thirdSeparatorIndex >= 0) {
//        var firstSeparatorIndex = nameOfFile.indexOf("_")
//        var secondSeparatorIndex = nameOfFile.indexOf("_", firstSeparatorIndex + 1)
//        flags = flags + "+numberofIterations_" + nameOfFile.substring(0, firstSeparatorIndex) + "+heightFactor_" + nameOfFile.substring(firstSeparatorIndex + 1, secondSeparatorIndex) + "+ptm3dDetail_" + nameOfFile.substring(secondSeparatorIndex + 1, thirdSeparatorIndex)
//        nameOfFile = nameOfFile.substring(thirdSeparatorIndex + 2)
//      }
//    }
//
//    val id = f.id
//    var fileType = f.contentType
//    if (fileType.contains("/zip") || fileType.contains("/x-zip") || nameOfFile.toLowerCase().endsWith(".zip")) {
//      fileType = FilesUtils.getMainFileTypeOfZipFile(uploadedFile.ref.file, nameOfFile, "file")
//      if (fileType.startsWith("ERROR: ")) {
//        Logger.error(fileType.substring(7))
//        InternalServerError(fileType.substring(7))
//      }
//      if (fileType.equals("imageset/ptmimages-zipped") || fileType.equals("imageset/ptmimages+zipped")) {
//        var thirdSeparatorIndex = nameOfFile.indexOf("__")
//        if (thirdSeparatorIndex >= 0) {
//          var firstSeparatorIndex = nameOfFile.indexOf("_")
//          var secondSeparatorIndex = nameOfFile.indexOf("_", firstSeparatorIndex + 1)
//          flags = flags + "+numberofIterations_" + nameOfFile.substring(0, firstSeparatorIndex) + "+heightFactor_" + nameOfFile.substring(firstSeparatorIndex + 1, secondSeparatorIndex) + "+ptm3dDetail_" + nameOfFile.substring(secondSeparatorIndex + 1, thirdSeparatorIndex)
//          nameOfFile = nameOfFile.substring(thirdSeparatorIndex + 2)
//          files.renameFile(f.id, nameOfFile)
//        }
//        files.setContentType(f.id, fileType)
//      }
//    }

    val newFlags = if (showPreviews.equals("FileLevel"))
      idAndFlags + "+filelevelshowpreviews"
    else if (showPreviews.equals("None"))
      idAndFlags + "+nopreviews"
    else
      idAndFlags

    val originalId = if (!file.isIntermediate) {
      file.id.toString()
    } else {
      idAndFlags
    }

    // send file to rabbitmq for processing
    if (runExtractors) {
      routing.fileCreated(file, dataset, clowderurl, apiKey)
    }

    // index the file
    if (index) {
      current.plugin[ElasticsearchPlugin].foreach {
        _.index(file, None)
      }
    }

    // notify admins a new file was added
    current.plugin[AdminsNotifierPlugin].foreach {
      // TODO replace with Mail.sendAdmins and use template
      _.sendAdminsNotification(clowderurl, "File", "added", file.id.stringify, file.filename)
    }
  }

  /** dataset processing */
  private def processDataset(file: File, dataset: Option[Dataset], folder: Option[Folder], clowderurl: String,
    user: User, index: Boolean, runExtractors: Boolean = true, apiKey: Option[String]): Unit = {
    // add metadata to dataset
    dataset.foreach{ds =>

      if (runExtractors) {
        routing.fileAddedToDataset(file, ds, clowderurl, apiKey)
      }

      // index dataset
      if (index) {
        datasets.index(ds.id)
      }

      //add file to RDF triple store if triple store is used
      if (file.contentType.equals("application/xml") || file.contentType.equals("text/xml")) {
        if (configuration.getString("userdfSPARQLStore").getOrElse("no") == "yes") {
          sqarql.addFileToGraph(file.id)
          sqarql.linkFileToDataset(file.id, ds.id)
        }
      }
    }
  }

  // ----------------------------------------------------------------------
  // END File upload
  // ----------------------------------------------------------------------

  //Download CONTENT-DISPOSITION encoding
  //
  def encodeAttachment(filename: String, userAgent: String) : String = {
    val filenameStar = if (userAgent.indexOf("MSIE") > -1) {
                              URLEncoder.encode(filename, "UTF-8")
                            } else if (userAgent.indexOf("Edge") > -1){
                              MimeUtility.encodeText(filename
                                  .replaceAll(",","%2C")
                                  .replaceAll("\"","%22")
                                  .replaceAll("/","%2F")
                                  .replaceAll("=","%3D")
                                  .replaceAll("&","%26")
                                  .replaceAll(":","%3A")
                                  .replaceAll(";","%3B")
                                  .replaceAll("\\?","%3F")
                                  .replaceAll("\\*","%2A")
                                  ,"utf-8","Q")
                            } else {
                              MimeUtility.encodeText(filename
                                  .replaceAll("%","%25")
                                  .replaceAll(" ","%20")
                                  .replaceAll("\"","%22")
                                  .replaceAll(",","%2C")
                                  .replaceAll("/","%2F")
                                  .replaceAll("=","%3D")
                                  .replaceAll(":","%3A")
                                  .replaceAll(";","%3B")
                                  .replaceAll("\\*","%2A")
                                  ,"utf-8","Q")
                            }
    Logger.debug(userAgent + ": " + filenameStar)

    //Return the complete attachment header info
    "attachment; filename*=UTF-8''" + filenameStar
  }

}
