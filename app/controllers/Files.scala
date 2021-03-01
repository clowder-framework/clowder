package controllers

import java.io._
import java.text.SimpleDateFormat
import java.util.Date

import javax.inject.Inject
import api.Permission
import fileutils.FilesUtils
import models._
import org.apache.commons.lang.StringEscapeUtils._
import play.api.Logger
import play.api.Play.{configuration, current}
import play.api.data.Form
import play.api.data.Forms._
import play.api.libs.iteratee._
import play.api.libs.json.Json._
import play.api.libs.concurrent.Execution.Implicits._
import services._
import java.text.SimpleDateFormat

import views.html.defaultpages.badRequest
import util.SearchUtils

import scala.collection.immutable.List
import scala.collection.mutable.ListBuffer
import scala.concurrent.Future
import play.api.i18n.Messages
import util.FileUtils
import javax.mail.internet.MimeUtility
import java.net.URLEncoder

/**
 * Manage files.
 */
class Files @Inject() (
  files: FileService,
  datasets: DatasetService,
  queries: MultimediaQueryService,
  comments: CommentService,
  sections: SectionService,
  extractions: ExtractionService,
  dtsrequests: ExtractionRequestsService,
  previews: PreviewService,
  threeD: ThreeDService,
  sparql: RdfSPARQLService,
  users: UserService,
  events: EventService,
  thumbnails: ThumbnailService,
  metadata: MetadataService,
  contextLDService: ContextLDService,
  spaces: SpaceService,
  folders: FolderService,
  routing: ExtractorRoutingService,
  appConfig: AppConfigurationService,
  sinkService: EventSinkService) extends SecuredController {

  lazy val chunksize = play.Play.application().configuration().getInt("clowder.chunksize", 1024*1024)

  /**
   * Upload form.
   */
  val uploadForm = Form(
    mapping(
      "userid" -> nonEmptyText
    )(FileMD.apply)(FileMD.unapply)
  )
  val spaceTitle: String = Messages("space.title")
  /**
   * File info.
   */
  def file(id: UUID, dataset: Option[String], space: Option[String], folder: Option[String]) = PermissionAction(Permission.ViewFile, Some(ResourceRef(ResourceRef.file, id))).async { implicit request =>
    implicit val user = request.user
    files.get(id) match {
      case Some(file) => {
        // get previews attached to the file in the database
        val previewsFromDB = previews.findByFileId(file.id)
        // get compatible previewers from disk
        val previewers = Previewers.findFilePreviewers()

        // TODO Extract to standalone method since it is duplicated in Datasets and Files for both api and controllers
        val previewsWithPreviewer = {
          // for each preview in the database, check that is compatible and add it to the list
          val pvf = for (
              previewer <- previewers;
              previewData <- previewsFromDB
              if (previewer.preview)
              if (!file.showPreviews.equals("None")) && (previewer.contentType.contains(previewData.contentType))
          ) yield {
            val tabTitle = previewData.title.getOrElse("Preview")
            (previewData.id.toString, previewer.id, previewer.path, previewer.main,
              api.routes.Previews.download(previewData.id).toString, previewData.contentType, previewData.length,
              tabTitle)
          }
          // for each previewer on disk, check that it is compatible and add it to the list
          val ff = for (
            previewer <- previewers
            if (previewer.file)
            if (!file.showPreviews.equals("None")) && (previewer.contentType.contains(file.contentType))
          ) yield {
            val tabTitle = previewer.id
            if (file.licenseData.isDownloadAllowed(user) ||
              Permission.checkPermission(user, Permission.DownloadFiles, ResourceRef(ResourceRef.file, file.id))) {
              (file.id.toString, previewer.id, previewer.path, previewer.main, routes.Files.file(file.id) + "/blob",
                file.contentType, file.length, tabTitle)
            } else {
              (file.id.toString, previewer.id, previewer.path, previewer.main, "null", file.contentType, file.length, tabTitle)
            }
          }
          // take the union of both lists
          val prevs = pvf ++ ff
          Map(file -> prevs)
        }

        // add sections to file
        val sectionsByFile = sections.findByFileId(file.id)
        val sectionsWithPreviews = sectionsByFile.map { s =>
          val p = previews.findBySectionId(s.id)
          if (p.length > 0)
            s.copy(preview = Some(p(0)))
          else
            s.copy(preview = None)
        }

        // metadata
        val mds = metadata.getMetadataByAttachTo(ResourceRef(ResourceRef.file, file.id))
        // TODO use to provide contextual definitions directly in the GUI
        val contexts = (for (md <- mds;
          cId <- md.contextId;
                             c <- contextLDService.getContextById(cId))
          yield cId -> c).toMap

        // Check if file is currently being processed by extractor(s)
        val extractorsActive = extractions.findIfBeingProcessed(file.id)

        var commentsByFile = comments.findCommentsByFileId(id)
        sectionsByFile.map { section =>
          commentsByFile ++= comments.findCommentsBySectionId(section.id)
        }
        commentsByFile = commentsByFile.sortBy(_.posted)

        //Decode the comments so that their free text will display correctly in the view
        var decodedCommentsByFile = ListBuffer.empty[Comment]
        for (aComment <- commentsByFile) {
          val dComment = Utils.decodeCommentElements(aComment)
          decodedCommentsByFile += dComment
        }

        //Decode the datasets so that their free text will display correctly in the view
        val datasetsContainingFile = datasets.findByFileIdDirectlyContain(file.id).sortBy(_.name)
        val allDatasets = datasets.get(folders.findByFileId(id).map(_.parentDatasetId)).found ++ datasetsContainingFile

        val access = if (allDatasets == Nil) {
          "Private"
        } else if (!allDatasets.head.isDefault) {
          val status = allDatasets.head.status
          status(0).toUpper + status.substring(1).toLowerCase()
        } else {
          var isInPublicSpace = false
          allDatasets.map { ds =>
            ds.spaces.map { s =>
              spaces.get(s) match {
                case Some(space) => {
                  if (space.isPublic) {
                    isInPublicSpace = true
                  }
                }
                case None => {}
              }
            }

          }
          if (isInPublicSpace) {
            "Public (" + spaceTitle + " Default)"
          } else {
            "Private (" + spaceTitle + " Default)"
          }
        }

        //decodedDatasetsContaining are the datasets where the file is, where the file is not within a folder
        val decodedDatasetsContaining = ListBuffer.empty[models.Dataset]

        for (aDataset <- datasetsContainingFile) {
          val dDataset = Utils.decodeDatasetElements(aDataset)
          decodedDatasetsContaining += dDataset
        }

        //allDecodedDatasets includes datasets where the file is on the first level (not within a folder) and when the file is in a folder
        //it includes the parent dataset of the folder.
        val allDecodedDatasets = ListBuffer.empty[models.Dataset]
        val decodedSpacesContaining = ListBuffer.empty[models.ProjectSpace]
        for (aDataset <- allDatasets) {
          val dDataset = Utils.decodeDatasetElements(aDataset)
          allDecodedDatasets += dDataset
          aDataset.spaces.map {
            sp => spaces.get(sp) match {
                case Some(s) => {
                  decodedSpacesContaining += Utils.decodeSpaceElements(s)
                }
                case None =>
              }
          }
        }

        val foldersContainingFile = folders.findByFileId(file.id).sortBy(_.name)
        val isRDFExportEnabled = current.plugin[RDFExportService].isDefined

        val extractionsByFile = extractions.findById(new ResourceRef('file, id))
        val extractionGroups = extractions.groupByType(extractionsByFile)


        var folderHierarchy = new ListBuffer[Folder]()
        if (foldersContainingFile.length > 0) {
          folderHierarchy = folderHierarchy ++ foldersContainingFile
          var f1: Folder = folderHierarchy.head
          while (f1.parentType == "folder") {
            folders.get(f1.parentId) match {
              case Some(fparent) => {
                folderHierarchy += fparent
                f1 = fparent
              }
              case None =>
            }
          }
        }

        val pager: models.Pager = dataset match {
          case None => Pager(None, None)
          case Some(dsId) => {
            datasets.get(new UUID(dsId)) match {
              case None => Pager(None, None)
              case Some(ds) => {
                val lastIndex = ds.files.length - 1
                val index = ds.files.indexOf(id)

                // Set prevFile / nextFile, if applicable
                if (index > 0 && index < lastIndex) {
                  // Yields UUID of prevFile and nextFile respectively
                  Pager(Some(ds.files(index + 1)), Some(ds.files(index - 1)))
                }else if (index == 0 && index < lastIndex) {
                  // This is the first file in the list, but not the last
                  Pager(Some(ds.files(index + 1)), None)
                } else if (index > 0 && index == lastIndex) {
                  // This is the last file in the list, but not the first
                  Pager(None, Some(ds.files(index - 1)))
                } else {
                  // There is one item on the list, disable paging
                  Pager(None, None)
                }
              }
            }
          }
        }

        //call Polyglot to get all possible output formats for this file's content type
        current.plugin[PolyglotPlugin] match {
          case Some(plugin) => {
            Logger.debug("Polyglot plugin found")

            // Increment view count for file
            val (view_count, view_date) = files.incrementViews(id, user)

            val fname = file.filename
            //use name of the file to get the extension (pdf or txt or jpg) to use an input type for Polyglot
            val lastDividerIndex = (fname.replace("/", ".").lastIndexOf(".")) + 1
            //drop all elements left of last divider index
            val contentTypeEnding = fname.drop(lastDividerIndex)
            Logger.debug("file name ends in " + contentTypeEnding)
            //get output formats from Polyglot plugin and pass as the last parameter to view
            plugin.getOutputFormats(contentTypeEnding).map(outputFormats =>
              Ok(views.html.file(file, id.stringify, commentsByFile, previewsWithPreviewer, sectionsWithPreviews,
                extractorsActive, decodedDatasetsContaining.toList, foldersContainingFile,
                mds, isRDFExportEnabled, extractionGroups, outputFormats, space, access, folderHierarchy.reverse.toList, decodedSpacesContaining.toList, allDecodedDatasets.toList, view_count, view_date, pager)))
          }
          case None =>
            Logger.debug("Polyglot plugin not found")

            // Increment view count for file
            val (view_count, view_date) = files.incrementViews(id, user)

            sinkService.logFileViewEvent(file, user)
            //passing None as the last parameter (list of output formats)
            Future(Ok(views.html.file(file, id.stringify, commentsByFile, previewsWithPreviewer, sectionsWithPreviews,
              extractorsActive, decodedDatasetsContaining.toList, foldersContainingFile,
              mds, isRDFExportEnabled, extractionGroups, None, space, access, folderHierarchy.reverse.toList, decodedSpacesContaining.toList, allDecodedDatasets.toList, view_count, view_date, pager)))
        }
      }

      case None => {
        val error_str = s"The file with id ${id} is not found."
        Logger.error(error_str)
        Future(BadRequest(views.html.notFound("File does not exist.")))
      }
    }
  }

  def followingFiles(index: Int, limit: Int, mode: String) = PrivateServerAction { implicit request =>
    implicit val user = request.user
    user match {
      case Some(clowderUser) => {

        var fileList = new ListBuffer[models.File]()
        val fileIds = clowderUser.followedEntities.filter(_.objectType == "file")
        val fileIdsToUse = fileIds.slice(index * limit, (index + 1) * limit)
        val prev = index - 1
        val next = if (fileIds.length > (index + 1) * limit) {
          index + 1
        } else {
          -1
        }

        files.get(fileIdsToUse.map(_.id)).found.foreach(ffile => fileList += ffile)

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
        Ok(views.html.users.followingFiles(fileList.toList, prev, next, limit, viewMode))
      }
      case None => InternalServerError("User not found")
    }
  }
  /**
   * List a specific number of files before or after a certain date.
   */
  def list(when: String, date: String, limit: Int, mode: String) = DisabledAction { implicit request =>
    implicit val user = request.user
    var direction = "b"
    if (when != "") direction = when
    val formatter = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS")
    var prev = ""
    var next = ""
    var fileList = List.empty[models.File]
    if (direction == "b") {
      fileList = files.listFilesBefore(date, limit)
    } else if (direction == "a") {
      fileList = files.listFilesAfter(date, limit)
    } else {
      badRequest
    }

    // latest object
    val latest = files.latest()
    // first object
    val first = files.first()
    var firstPage = false
    var lastPage = false
    if (latest.size == 1) {
      firstPage = fileList.exists(_.id.equals(latest.get.id))
      lastPage = fileList.exists(_.id.equals(first.get.id))
      Logger.debug("latest " + latest.get.id + " first page " + firstPage)
      Logger.debug("first " + first.get.id + " last page " + lastPage)
    }

    if (fileList.size > 0) {
      if (date != "" && !firstPage) { // show prev button
        prev = formatter.format(fileList.head.uploadDate)
      }
      if (!lastPage) { // show next button
        next = formatter.format(fileList.last.uploadDate)
      }
    }

    val commentMap = fileList.map { file =>
      var allComments = comments.findCommentsByFileId(file.id)
      sections.findByFileId(file.id).map { section =>
        allComments ++= comments.findCommentsBySectionId(section.id)
      }
      file.id -> allComments.size
    }.toMap

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
    Ok(views.html.filesList(fileList, commentMap, prev, next, limit, viewMode, None))
  }

  /**
   * Upload file page.
   */
  def uploadFile = PermissionAction(Permission.AddFile) { implicit request =>
    implicit val user = request.user
    Ok(views.html.upload(uploadForm))
  }

  /**
   * Upload form for extraction.
   */
  val extractForm = Form(
    mapping(
      "userid" -> nonEmptyText
    )(FileMD.apply)(FileMD.unapply)
  )


  def extractFile = PermissionAction(Permission.AddFile) { implicit request =>
    implicit val user = request.user
    Ok(views.html.uploadExtract(extractForm))
  }

  def uploadExtract() = PermissionAction(Permission.AddFile)(parse.multipartFormData) { implicit request =>
    implicit val user = request.user
    user match {
      case Some(identity) => {
        request.body.file("File").map { f =>
          try {
            var nameOfFile = f.filename
            var flags = ""
            if (nameOfFile.toLowerCase().endsWith(".ptm")) {
              var thirdSeparatorIndex = nameOfFile.indexOf("__")
              if (thirdSeparatorIndex >= 0) {
                var firstSeparatorIndex = nameOfFile.indexOf("_")
                var secondSeparatorIndex = nameOfFile.indexOf("_", firstSeparatorIndex + 1)
                flags = flags + "+numberofIterations_" + nameOfFile.substring(0, firstSeparatorIndex) + "+heightFactor_" + nameOfFile.substring(firstSeparatorIndex + 1, secondSeparatorIndex) + "+ptm3dDetail_" + nameOfFile.substring(secondSeparatorIndex + 1, thirdSeparatorIndex)
                nameOfFile = nameOfFile.substring(thirdSeparatorIndex + 2)
              }
            }

            Logger.debug("Uploading file " + nameOfFile)

            var showPreviews = request.body.asFormUrlEncoded.get("datasetLevel").get(0)

              // store file
              val file = files.save(new FileInputStream(f.ref.file), nameOfFile, f.ref.file.length, f.contentType, identity, showPreviews)
              val uploadedFile = f
              file match {
                case Some(f) => {
                  // Add new file & byte count to appConfig
                  appConfig.incrementCount('files, 1)
                  appConfig.incrementCount('bytes, f.length)

                  current.plugin[FileDumpService].foreach {
                    _.dump(DumpOfFile(uploadedFile.ref.file, f.id.toString, nameOfFile))
                  }

                if (showPreviews.equals("FileLevel"))
                  flags = flags + "+filelevelshowpreviews"
                else if (showPreviews.equals("None"))
                  flags = flags + "+nopreviews"
                var fileType = f.contentType
                if (fileType.contains("/zip") || fileType.contains("/x-zip") || nameOfFile.toLowerCase().endsWith(".zip")) {
                  fileType = FilesUtils.getMainFileTypeOfZipFile(uploadedFile.ref.file, nameOfFile, "file")
                  if (fileType.startsWith("ERROR: ")) {
                    Logger.error(fileType.substring(7))
                    InternalServerError(fileType.substring(7))
                  }
                }
                // submit for extraction
                routing.fileCreated(f, None, Utils.baseUrl(request).toString, request.apiKey)

                /** *** Inserting DTS Requests   **/
                val clientIP = request.remoteAddress
                val domain = request.domain
                val keysHeader = request.headers.keys

                Logger.debug("clientIP:" + clientIP + "   domain:= " + domain + "  keysHeader=" + keysHeader.toString + "\n")
                Logger.debug("Origin: " + request.headers.get("Origin") + "  Referer=" + request.headers.get("Referer") + " Connections=" + request.headers.get("Connection") + "\n \n")
                val serverIP = request.host
                dtsrequests.insertRequest(serverIP, clientIP, f.filename, f.id, fileType, f.length, f.uploadDate)

                /** **************************/
                //for metadata files
                if (fileType.equals("application/xml") || fileType.equals("text/xml")) {
                  val xmlToJSON = FilesUtils.readXMLgetJSON(uploadedFile.ref.file)
                  files.addXMLMetadata(f.id, xmlToJSON)

                  current.plugin[ElasticsearchPlugin].foreach {
                    _.index(SearchUtils.getElasticsearchObject(f))
                  }
                }
                else {
                  current.plugin[ElasticsearchPlugin].foreach {
                    _.index(SearchUtils.getElasticsearchObject(f))
                  }
                }
                current.plugin[VersusPlugin].foreach {
                  _.index(f.id.toString, fileType)
                }

                // redirect to extract page
                Ok(views.html.extract(f.id))
              }
              case None => {
                Logger.error("Could not retrieve file that was just saved.")
                InternalServerError("Error uploading file")
              }
            }
          } finally {
            f.ref.clean()
          }
        }.getOrElse {
          BadRequest("File not attached.")

        }
      }
      case None => Redirect(routes.Datasets.list()).flashing("error" -> "You are not authorized to create new files.")
    }
  }
  /*def extraction(id: String) = SecuredAction(authorization = WithPermission(Permission.ShowFile)) { implicit request =>
 

}*/

  /**
   * Upload a file.
   *
   * Updated to return json data that is utilized by the user interface upload library. The json structure is an array of maps that
   * contain data for each of the file that the upload interface can use to accurately update the display based on the success
   * or failure of the upload process.
   */
  def upload() = PermissionAction(Permission.AddFile)(parse.multipartFormData) { implicit request =>
    implicit val user = request.user
    Logger.debug("--------- in upload ------------ ")
    user match {
      case Some(identity) => {
        request.body.file("files[]").map { f =>                     
	          var nameOfFile = f.filename
	          var flags = ""	          
	          if(nameOfFile.toLowerCase().endsWith(".ptm")){
		          val thirdSeparatorIndex = nameOfFile.indexOf("__")
	              if(thirdSeparatorIndex >= 0){
	                val firstSeparatorIndex = nameOfFile.indexOf("_")
	                val secondSeparatorIndex = nameOfFile.indexOf("_", firstSeparatorIndex+1)
	            	flags = flags + "+numberofIterations_" +  nameOfFile.substring(0,firstSeparatorIndex) + "+heightFactor_" + nameOfFile.substring(firstSeparatorIndex+1,secondSeparatorIndex)+ "+ptm3dDetail_" + nameOfFile.substring(secondSeparatorIndex+1,thirdSeparatorIndex)
	            	nameOfFile = nameOfFile.substring(thirdSeparatorIndex+2)
	              }
	          }	       
	        Logger.debug("Uploading file " + nameOfFile)

	        val showPreviews = request.body.asFormUrlEncoded.get("datasetLevel").get(0)

	        // store file       
	        val file = files.save(new FileInputStream(f.ref.file), nameOfFile, f.ref.file.length, f.contentType, identity, showPreviews)
	        val uploadedFile = f
	        file match {
	          case Some(f) => {
              // Add new file & byte count to appConfig
              appConfig.incrementCount('files, 1)
              appConfig.incrementCount('bytes, f.length)

              val option_user = users.findByIdentity(identity)
              events.addObjectEvent(option_user, f.id, f.filename, "upload_file")
              if (showPreviews.equals("FileLevel"))
                flags = flags + "+filelevelshowpreviews"
              else if (showPreviews.equals("None"))
                flags = flags + "+nopreviews"
              var fileType = f.contentType
              if (fileType.contains("/zip") || fileType.contains("/x-zip") || nameOfFile.toLowerCase().endsWith(".zip")) {
                fileType = FilesUtils.getMainFileTypeOfZipFile(uploadedFile.ref.file, nameOfFile, "file")
                if (fileType.startsWith("ERROR: ")) {
                  Logger.error(fileType.substring(7))
                  InternalServerError(fileType.substring(7))
                }
                if (fileType.equals("imageset/ptmimages-zipped") || fileType.equals("imageset/ptmimages+zipped") || fileType.equals("multi/files-ptm-zipped")) {
                  if (fileType.equals("multi/files-ptm-zipped")) {
                    fileType = "multi/files-zipped";
                  }

                  val thirdSeparatorIndex = nameOfFile.indexOf("__")
                  if (thirdSeparatorIndex >= 0) {
                    val firstSeparatorIndex = nameOfFile.indexOf("_")
                    val secondSeparatorIndex = nameOfFile.indexOf("_", firstSeparatorIndex + 1)
                    flags = flags + "+numberofIterations_" + nameOfFile.substring(0, firstSeparatorIndex) + "+heightFactor_" + nameOfFile.substring(firstSeparatorIndex + 1, secondSeparatorIndex) + "+ptm3dDetail_" + nameOfFile.substring(secondSeparatorIndex + 1, thirdSeparatorIndex)
                    nameOfFile = nameOfFile.substring(thirdSeparatorIndex + 2)
                    files.renameFile(f.id, nameOfFile)
                  }
                  files.setContentType(f.id, fileType)
                }
              }

              current.plugin[FileDumpService].foreach { _.dump(DumpOfFile(uploadedFile.ref.file, f.id.toString, nameOfFile)) }

              /***** Inserting DTS Requests   **/
              val clientIP = request.remoteAddress
              //val clientIP=request.headers.get("Origin").get
              val domain = request.domain
              val keysHeader = request.headers.keys
              //request.
              Logger.debug("---\n \n")

              Logger.debug("clientIP:" + clientIP + "   domain:= " + domain + "  keysHeader=" + keysHeader.toString + "\n")
              Logger.debug("Origin: " + request.headers.get("Origin") + "  Referer=" + request.headers.get("Referer") + " Connections=" + request.headers.get("Connection") + "\n \n")

              Logger.debug("----")
              val serverIP = request.host
              val extra = Map("filename" -> f.filename)
              dtsrequests.insertRequest(serverIP, clientIP, f.filename, f.id, fileType, f.length, f.uploadDate)
              /****************************/
              routing.fileCreated(f, None, Utils.baseUrl(request).toString, request.apiKey)
	            
	            //for metadata files
	            if(fileType.equals("application/xml") || fileType.equals("text/xml")){
	              val xmlToJSON = FilesUtils.readXMLgetJSON(uploadedFile.ref.file)
	              files.addXMLMetadata(f.id, xmlToJSON)
	              
	              current.plugin[ElasticsearchPlugin].foreach{
		              _.index(SearchUtils.getElasticsearchObject(f))
                }
	            }
	            else{
		            current.plugin[ElasticsearchPlugin].foreach{
		              _.index(SearchUtils.getElasticsearchObject(f))
                }
	            }

              current.plugin[VersusPlugin].foreach { _.indexFile(f.id, fileType) }

              //add file to RDF triple store if triple store is used
              if (fileType.equals("application/xml") || fileType.equals("text/xml")) {
                play.api.Play.configuration.getString("userdfSPARQLStore").getOrElse("no") match {
                  case "yes" => sparql.addFileToGraph(f.id)
                  case _ => {}
                }
              }

              current.plugin[AdminsNotifierPlugin].foreach {
                _.sendAdminsNotification(Utils.baseUrl(request), "File","added",f.id.stringify, nameOfFile)}

              //Correctly set the updated URLs and data that is needed for the interface to correctly 
              //update the display after a successful upload.
              val https = controllers.Utils.https(request)
              val retMap = Map("files" ->
                Seq(
                  toJson(
                    Map(
                      "name" -> toJson(nameOfFile),
                      "size" -> toJson(uploadedFile.ref.file.length()),
                      "url" -> toJson(routes.Files.file(f.id).absoluteURL(https)),
                      "deleteUrl" -> toJson(api.routes.Files.removeFile(f.id).absoluteURL(https)),
	                            "deleteType" -> toJson("POST")
	                        )
	                    )
	                )
	            )
              Ok(toJson(retMap))
            }
            case None => {
              Logger.error("Could not retrieve file that was just saved.")
              //Changed to return appropriate data and message to the upload interface
              val retMap = Map("files" ->
                Seq(
                  toJson(
                    Map(
                      "name" -> toJson(nameOfFile),
                      "size" -> toJson(uploadedFile.ref.file.length()),
                                "error" -> toJson("Problem in storing the uploaded file.")
                            )
                        )
                    )
                )
              Ok(toJson(retMap))
            }
          }
        }.getOrElse {
          Logger.error("The file appears to not have been attached correctly during upload.")
          //This should be a very rare case. Changed to return the simple error message for the interface to display.
          val retMap = Map("files" ->
            Seq(
              toJson(
                Map(
                                "error" -> toJson("The file was not correctly attached during upload.")
                            )
                        )
                    )
                )
          Ok(toJson(retMap))

        }
      }
      case None => {
        //Change to be the authentication login? Or will this automatically intercept? TEST IT
        Redirect(routes.Datasets.list()).flashing("error" -> "You are not authorized to create new files.")
      }
    }
  }

  /**
   * Download file using http://en.wikipedia.org/wiki/Chunked_transfer_encoding
   */
  def download(id: UUID) = PermissionAction(Permission.DownloadFiles, Some(ResourceRef(ResourceRef.file, id))) { implicit request =>
    implicit val user = request.user

    if (UUID.isValid(id.stringify)) {
      //Check the license type before doing anything. 
      files.get(id) match {
        case Some(file) => {
          if (file.licenseData.isDownloadAllowed(request.user)) {
            files.getBytes(id) match {
              case Some((inputStream, filename, contentType, contentLength)) => {
                files.incrementDownloads(id, user)
                sinkService.logFileDownloadEvent(file, user)

                request.headers.get(RANGE) match {
                  case Some(value) => {
                    val range: (Long, Long) = value.substring("bytes=".length).split("-") match {
                      case x if x.length == 1 => (x.head.toLong, contentLength - 1)
                      case x => (x(0).toLong, x(1).toLong)
                    }
                          range match { case (start,end) =>

                        inputStream.skip(start)
                        import play.api.mvc.{ ResponseHeader, SimpleResult }
                        SimpleResult(
                          header = ResponseHeader(PARTIAL_CONTENT,
                            Map(
                              CONNECTION -> "keep-alive",
                              ACCEPT_RANGES -> "bytes",
                              CONTENT_RANGE -> "bytes %d-%d/%d".format(start, end, contentLength),
                              CONTENT_LENGTH -> (end - start + 1).toString,
                                                  CONTENT_TYPE -> contentType
                                                  )
                                          ),
                                          body = Enumerator.fromStream(inputStream, chunksize)
                                  )
                    }
                  }
                  case None => {
                    val userAgent = request.headers.get("user-agent").getOrElse("")

                    Ok.chunked(Enumerator.fromStream(inputStream, chunksize))
                      .withHeaders(CONTENT_TYPE -> contentType)
                      .withHeaders(CONTENT_DISPOSITION -> (FileUtils.encodeAttachment(filename, userAgent)))
                  }
                }
              }
              case None => {
                Logger.error("Error getting file" + id)
                BadRequest("Invalid file ID")
              }
            }
                  }
                  else {
            //Case where the checkLicenseForDownload fails
            Logger.error("The file is not able to be downloaded")
            BadRequest("The license for this file does not allow it to be downloaded.")
          }
        }
        case None => {
          //Case where the file could not be found
          Logger.error(s"Error getting the file with id $id.")
          BadRequest("Invalid file ID")
        }
      }

      }
      else {
      Logger.error(s"The given id $id is not a valid ObjectId.")
      BadRequest(toJson(s"The given id $id is not a valid ObjectId."))
    }
  }

  //using code from https://www.playframework.com/documentation/2.2.x/ScalaWS
  //Processing large responses
  def fromStream(stream: OutputStream): Iteratee[Array[Byte], Unit] = Cont {
    case e @ Input.EOF =>
      Logger.debug("fromStream case EOF")
      stream.close()
      Done((), e)
    case Input.El(data) =>
      Logger.debug("fromStream case input.El, data = " + data)
      stream.write(data)
      fromStream(stream)
    case Input.Empty =>
      Logger.debug("fromStream case empty , so calling fromStream again")
      fromStream(stream)
  }

  /**
   *  Uses Polyglot service to convert file to a new format and download to user's computer.
   *
   */
  def downloadAsFormat(id: UUID, outputFormat: String) = PermissionAction(Permission.DownloadFiles, Some(ResourceRef(ResourceRef.file, id))).async { implicit request =>
    implicit val user = request.user

    current.plugin[PolyglotPlugin] match {
      case Some(plugin) => {
        if (UUID.isValid(id.stringify)) {
          files.get(id) match {
            case Some(file) => {
              //get bytes for file to be converted
              files.getBytes(id) match {
                case Some((inputStream, filename, contentType, contentLength)) => {

                  //prepare encoded file name for converted file
                  val lastSeparatorIndex = file.filename.replace("_", ".").lastIndexOf(".")
                  val outputFileName = file.filename.substring(0, lastSeparatorIndex) + "." + outputFormat
                  
                  //create local temp file to save polyglot output
                  val tempFileName = "temp_converted_file." + outputFormat
                  val tempFile: java.io.File = new java.io.File(tempFileName)
                  tempFile.deleteOnExit()

                  val outputStream: OutputStream = new BufferedOutputStream(new FileOutputStream(tempFile))

                  val polyglotUser: Option[String] = configuration.getString("polyglot.username")
                  val polyglotPassword: Option[String] = configuration.getString("polyglot.password")
                  val polyglotConvertURL: Option[String] = configuration.getString("polyglot.convertURL")

                  if (polyglotConvertURL.isDefined && polyglotUser.isDefined && polyglotPassword.isDefined) {
                    files.incrementDownloads(id, user)

                    //first call to Polyglot to get url of converted file
                    plugin.getConvertedFileURL(filename, inputStream, outputFormat)
                      .flatMap {
                        convertedFileURL =>
                          val triesLeft = 30
                          //Cponverted file is initially empty. Have to wait for Polyglot to finish conversion.
                          //keep checking until file exists or until too many tries
                          //returns future success only if file is found and downloaded
                          plugin.checkForFileAndDownload(triesLeft, convertedFileURL, outputStream)
                      }.map {
                        x =>
                          //successfuly completed future - get here only after polyglotPlugin.getConvertedFileURL is done executing
                          Ok.chunked(Enumerator.fromStream(new FileInputStream(tempFileName), chunksize))
                            .withHeaders(CONTENT_TYPE -> "some-content-Type")
                            .withHeaders(CONTENT_DISPOSITION -> (FileUtils.encodeAttachment(outputFileName, request.headers.get("user-agent").getOrElse(""))))
                      }.recover {
                        case t =>
                          Logger.debug("Failed:  " + t.getMessage())
                          BadRequest("Error: " + t.getMessage())
                      }
                  } //end of if defined config params
                  else {
                    Logger.error("Could not get configuration parameters.")
                    Future(BadRequest("Could not get configuration parameters."))
                  }
                } //end of case Some
                case None => {
                  Logger.error("Error getting file " + id)
                  Future(BadRequest("File with this id not found"))
                }
              }
            }
            case None => {
              //File could not be found
              Logger.error(s"Error getting the file with id $id.")
              Future(BadRequest("Invalid file ID"))
            }
          }
        } //end of if (UUID.isValid(id.stringify))
        else {
          Logger.error(s"The given id $id is not a valid ObjectId.")
          Future(BadRequest(toJson(s"The given id $id is not a valid ObjectId.")))
        }
      } //end of case Some(plugin)
      case None =>
        Logger.debug("Polyglot plugin not found")
        Future(Ok("Plugin not found"))
    }
  }

  def thumbnail(id: UUID) = PermissionAction(Permission.ViewFile, Some(ResourceRef(ResourceRef.thumbnail, id))) { implicit request =>
    thumbnails.getBlob(id) match {
      case Some((inputStream, filename, contentType, contentLength)) => {
        request.headers.get(RANGE) match {
          case Some(value) => {
            val range: (Long, Long) = value.substring("bytes=".length).split("-") match {
              case x if x.length == 1 => (x.head.toLong, contentLength - 1)
              case x => (x(0).toLong, x(1).toLong)
            }
	            range match { case (start,end) =>


                inputStream.skip(start)
                import play.api.mvc.{ ResponseHeader, SimpleResult }
                SimpleResult(
                  header = ResponseHeader(PARTIAL_CONTENT,
                    Map(
                      CONNECTION -> "keep-alive",
                      ACCEPT_RANGES -> "bytes",
                      CONTENT_RANGE -> "bytes %d-%d/%d".format(start, end, contentLength),
                      CONTENT_LENGTH -> (end - start + 1).toString,
	                    CONTENT_TYPE -> contentType
	                  )
	                ),
	                body = Enumerator.fromStream(inputStream, chunksize)
	              )
            }
          }
          case None => {
            Ok.chunked(Enumerator.fromStream(inputStream, chunksize))
              .withHeaders(CONTENT_TYPE -> contentType)
              .withHeaders(CONTENT_DISPOSITION -> (FileUtils.encodeAttachment(filename, request.headers.get("user-agent").getOrElse(""))))

          }
        }
      }
      case None => {
        Logger.error("Error getting thumbnail " + id)
        NotFound
      }
    }

  }

  /**
   * Uploads query to temporary folder.
   * Gets type of index and list of sections, and passes on to the Search controller
   */
  def uploadSelectQuery() = PermissionAction(Permission.ViewDataset)(parse.multipartFormData) { implicit request =>
    val nameOfIndex = play.api.Play.configuration.getString("elasticsearchSettings.indexNamePrefix").getOrElse("clowder")
    //=== processing searching within files or sections of files or both ===
    //dataParts are from the seach form in view/multimediasearch
    //get type of index and list of sections, and pass on to the Search controller
    //pass them on to Search.findSimilarToQueryFile for further processing
    val dataParts = request.body.dataParts
    //indexType in dataParts is a sequence of just one element
    val typeToSearch = dataParts("indexType").head
    //get a list of sections to be searched
    var sections: List[String] = List.empty[String]
    if (typeToSearch.equals("sectionsSome") && dataParts.contains("sections")) {
      sections = dataParts("sections").toList
    }
    //END OF: processing searching within files or sections of files or both    
    request.body.file("File").map { f =>
      try {
        var nameOfFile = f.filename
        var flags = ""
        if (nameOfFile.toLowerCase().endsWith(".ptm")) {
          var thirdSeparatorIndex = nameOfFile.indexOf("__")
          if (thirdSeparatorIndex >= 0) {
            var firstSeparatorIndex = nameOfFile.indexOf("_")
            var secondSeparatorIndex = nameOfFile.indexOf("_", firstSeparatorIndex + 1)
            flags = flags + "+numberofIterations_" + nameOfFile.substring(0, firstSeparatorIndex) + "+heightFactor_" +
              nameOfFile.substring(firstSeparatorIndex + 1, secondSeparatorIndex) + "+ptm3dDetail_" +
              nameOfFile.substring(secondSeparatorIndex + 1, thirdSeparatorIndex)
            nameOfFile = nameOfFile.substring(thirdSeparatorIndex + 2)
          }
        }
        Logger.debug("Controllers/Files Uploading file " + nameOfFile)
        
        // store file
        val file = queries.save(new FileInputStream(f.ref.file), nameOfFile, f.contentType)
        val uploadedFile = f

        file match {
          case Some(f) => {

            var fileType = f.contentType
            if (fileType.contains("/zip") || fileType.contains("/x-zip") || nameOfFile.toLowerCase().endsWith(".zip")) {
              fileType = FilesUtils.getMainFileTypeOfZipFile(uploadedFile.ref.file, nameOfFile, "file")
              if (fileType.startsWith("ERROR: ")) {
                Logger.error(fileType.substring(7))
                InternalServerError(fileType.substring(7))
              }
              if (fileType.equals("imageset/ptmimages-zipped") || fileType.equals("imageset/ptmimages+zipped") || fileType.equals("multi/files-ptm-zipped")) {
                if (fileType.equals("multi/files-ptm-zipped")) {
                  fileType = "multi/files-zipped";
                }

                var thirdSeparatorIndex = nameOfFile.indexOf("__")
                if (thirdSeparatorIndex >= 0) {
                  var firstSeparatorIndex = nameOfFile.indexOf("_")
                  var secondSeparatorIndex = nameOfFile.indexOf("_", firstSeparatorIndex + 1)
                  flags = flags + "+numberofIterations_" + nameOfFile.substring(0, firstSeparatorIndex) + "+heightFactor_" + nameOfFile.substring(firstSeparatorIndex + 1, secondSeparatorIndex) + "+ptm3dDetail_" + nameOfFile.substring(secondSeparatorIndex + 1, thirdSeparatorIndex)
                  nameOfFile = nameOfFile.substring(thirdSeparatorIndex + 2)
                  files.renameFile(f.id, nameOfFile)
                }
                files.setContentType(f.id, fileType)
              }
            }

            current.plugin[FileDumpService].foreach {
              _.dump(DumpOfFile(uploadedFile.ref.file, f.id.toString, nameOfFile))
            }

            routing.multimediaQuery(f.id, f.contentType, f.length.toString, Utils.baseUrl(request), request.apiKey)

            //for metadata files
            if (fileType.equals("application/xml") || fileType.equals("text/xml")) {
              val xmlToJSON = FilesUtils.readXMLgetJSON(uploadedFile.ref.file)
              files.addXMLMetadata(f.id, xmlToJSON)
            }

            //add file to RDF triple store if triple store is used
            if (fileType.equals("application/xml") || fileType.equals("text/xml")) {
              play.api.Play.configuration.getString("userdfSPARQLStore").getOrElse("no") match {
                case "yes" => sparql.addFileToGraph(f.id)
                case _ => {}
              }
            }
            // redirect to file page
            Redirect(routes.Search.findSimilarToQueryFile(f.id, typeToSearch, sections))
          }
          case None => {
            Logger.error("Could not retrieve file that was just saved.")
            InternalServerError("Error uploading file")
          }
        }
      } finally {
        f.ref.clean()
      }
    }.getOrElse {
      BadRequest("File not attached.")
    }
  }

  /**
    * When a user drag and drops in the GUI. This seems to only be used for multimedia queries to provide the input
    * image. The next method `def uploaddnd(dataset_id: UUID)` is the one currently used by the GUI when uploading
    * files to a dataset.
    * @return
    */
  def uploadDragDrop() = PermissionAction(Permission.ViewDataset)(parse.multipartFormData) { implicit request =>
    request.body.file("File").map { f =>
      var nameOfFile = f.filename
      var flags = ""
      if (nameOfFile.toLowerCase().endsWith(".ptm")) {
        val thirdSeparatorIndex = nameOfFile.indexOf("__")
        if (thirdSeparatorIndex >= 0) {
          val firstSeparatorIndex = nameOfFile.indexOf("_")
          val secondSeparatorIndex = nameOfFile.indexOf("_", firstSeparatorIndex + 1)
          flags = flags + "+numberofIterations_" + nameOfFile.substring(0, firstSeparatorIndex) + "+heightFactor_" + nameOfFile.substring(firstSeparatorIndex + 1, secondSeparatorIndex) + "+ptm3dDetail_" + nameOfFile.substring(secondSeparatorIndex + 1, thirdSeparatorIndex)
          nameOfFile = nameOfFile.substring(thirdSeparatorIndex + 2)
        }
      }

      Logger.debug("Uploading file " + nameOfFile)

      // store file
      Logger.debug("uploadDragDrop")
      val file = queries.save(new FileInputStream(f.ref.file), nameOfFile, f.contentType)
      val uploadedFile = f
      try {
        file match {
          case Some(f) => {
            var fileType = f.contentType
            if (fileType.contains("/zip") || fileType.contains("/x-zip") || nameOfFile.toLowerCase().endsWith(".zip")) {
              fileType = FilesUtils.getMainFileTypeOfZipFile(uploadedFile.ref.file, nameOfFile, "file")
              if (fileType.startsWith("ERROR: ")) {
                Logger.error(fileType.substring(7))
                InternalServerError(fileType.substring(7))
              }
              if (fileType.equals("imageset/ptmimages-zipped") || fileType.equals("imageset/ptmimages+zipped") || fileType.equals("multi/files-ptm-zipped")) {
                if (fileType.equals("multi/files-ptm-zipped")) {
                  fileType = "multi/files-zipped";
                }

                var thirdSeparatorIndex = nameOfFile.indexOf("__")
                if (thirdSeparatorIndex >= 0) {
                  var firstSeparatorIndex = nameOfFile.indexOf("_")
                  var secondSeparatorIndex = nameOfFile.indexOf("_", firstSeparatorIndex + 1)
                  flags = flags + "+numberofIterations_" + nameOfFile.substring(0, firstSeparatorIndex) + "+heightFactor_" + nameOfFile.substring(firstSeparatorIndex + 1, secondSeparatorIndex) + "+ptm3dDetail_" + nameOfFile.substring(secondSeparatorIndex + 1, thirdSeparatorIndex)
                  nameOfFile = nameOfFile.substring(thirdSeparatorIndex + 2)
                  files.renameFile(f.id, nameOfFile)
                }
                files.setContentType(f.id, fileType)
              }
            }

            current.plugin[FileDumpService].foreach {
              _.dump(DumpOfFile(uploadedFile.ref.file, f.id.toString, nameOfFile))
            }

            // TODO RK need to replace unknown with the server name
            val key = "unknown." + "file." + fileType.replace(".", "_").replace("/", ".")

            val host = Utils.baseUrl(request)
            val id = f.id
            val extra = Map("filename" -> f.filename, "action" -> "upload")

            routing.fileCreated(f, host, request.apiKey)

            //for metadata files
            if (fileType.equals("application/xml") || fileType.equals("text/xml")) {
              val xmlToJSON = FilesUtils.readXMLgetJSON(uploadedFile.ref.file)
              files.addXMLMetadata(id, xmlToJSON)

              current.plugin[ElasticsearchPlugin].foreach {
                _.index(SearchUtils.getElasticsearchObject(f))
              }
            }
            else {
              current.plugin[ElasticsearchPlugin].foreach {
                _.index(SearchUtils.getElasticsearchObject(f))
              }
            }
            //add file to RDF triple store if triple store is used
            if (fileType.equals("application/xml") || fileType.equals("text/xml")) {
              play.api.Play.configuration.getString("userdfSPARQLStore").getOrElse("no") match {
                case "yes" => sparql.addFileToGraph(f.id)
                case _ => {}
              }
            }
            Ok(f.id.toString)
          }
          case None => {
            Logger.error("Could not retrieve file that was just saved.")
            InternalServerError("Error uploading file")
          }
        }
      } finally {
        f.ref.clean()
      }
    }.getOrElse {
      BadRequest("File not attached.")
    }
  }

  def uploaddnd(dataset_id: UUID) = PermissionAction(Permission.AddResourceToDataset, Some(ResourceRef(ResourceRef.dataset, dataset_id)))(parse.multipartFormData) { implicit request =>
    request.user match {
      case Some(identity) => {
        datasets.get(dataset_id) match {
          case Some(dataset) => {
            request.body.file("File").map { f =>
              try {
                var nameOfFile = f.filename
                var flags = ""
                if (nameOfFile.toLowerCase().endsWith(".ptm")) {
                  var thirdSeparatorIndex = nameOfFile.indexOf("__")
                  if (thirdSeparatorIndex >= 0) {
                    var firstSeparatorIndex = nameOfFile.indexOf("_")
                    var secondSeparatorIndex = nameOfFile.indexOf("_", firstSeparatorIndex + 1)
                    flags = flags + "+numberofIterations_" + nameOfFile.substring(0, firstSeparatorIndex) + "+heightFactor_" + nameOfFile.substring(firstSeparatorIndex + 1, secondSeparatorIndex) + "+ptm3dDetail_" + nameOfFile.substring(secondSeparatorIndex + 1, thirdSeparatorIndex)
                    nameOfFile = nameOfFile.substring(thirdSeparatorIndex + 2)
                  }
                }

                Logger.debug("Uploading file " + nameOfFile)
                val showPreviews = request.body.asFormUrlEncoded.get("datasetLevel").get(0)
                // save file bytes
                val file = files.save(new FileInputStream(f.ref.file), nameOfFile, f.ref.file.length, f.contentType, identity, showPreviews)
                val uploadedFile = f

                // submit file for extraction
                file match {
                  case Some(f) => {
                    // Add new file & byte count to appConfig
                    appConfig.incrementCount('files, 1)
                    appConfig.incrementCount('bytes, f.length)

                    if (showPreviews.equals("FileLevel"))
                      flags = flags + "+filelevelshowpreviews"
                    else if (showPreviews.equals("None"))
                      flags = flags + "+nopreviews"
                    var fileType = f.contentType
                    if (fileType.contains("/zip") || fileType.contains("/x-zip") || nameOfFile.toLowerCase().endsWith(".zip")) {
                      fileType = FilesUtils.getMainFileTypeOfZipFile(uploadedFile.ref.file, nameOfFile, "dataset")
                      if (fileType.startsWith("ERROR: ")) {
                        Logger.error(fileType.substring(7))
                        InternalServerError(fileType.substring(7))
                      }
                      if (fileType.equals("imageset/ptmimages-zipped") || fileType.equals("imageset/ptmimages+zipped") || fileType.equals("multi/files-ptm-zipped")) {
                        if (fileType.equals("multi/files-ptm-zipped")) {
                          fileType = "multi/files-zipped";
                        }

                        var thirdSeparatorIndex = nameOfFile.indexOf("__")
                        if (thirdSeparatorIndex >= 0) {
                          var firstSeparatorIndex = nameOfFile.indexOf("_")
                          var secondSeparatorIndex = nameOfFile.indexOf("_", firstSeparatorIndex + 1)
                          flags = flags + "+numberofIterations_" + nameOfFile.substring(0, firstSeparatorIndex) + "+heightFactor_" + nameOfFile.substring(firstSeparatorIndex + 1, secondSeparatorIndex) + "+ptm3dDetail_" + nameOfFile.substring(secondSeparatorIndex + 1, thirdSeparatorIndex)
                          nameOfFile = nameOfFile.substring(thirdSeparatorIndex + 2)
                          files.renameFile(f.id, nameOfFile)
                        }
                        files.setContentType(f.id, fileType)
                      }
                    }

                    current.plugin[FileDumpService].foreach {
                      _.dump(DumpOfFile(uploadedFile.ref.file, f.id.toString, nameOfFile))
                    }

                    /** *** Inserting DTS Requests   **/
                    val clientIP = request.remoteAddress
                    val domain = request.domain
                    val keysHeader = request.headers.keys
                    //request.
                    Logger.debug("---\n \n")

                    Logger.debug("clientIP:" + clientIP + "   domain:= " + domain + "  keysHeader=" + keysHeader.toString + "\n")
                    Logger.debug("Origin: " + request.headers.get("Origin") + "  Referer=" + request.headers.get("Referer") + " Connections=" + request.headers.get("Connection") + "\n \n")

                    Logger.debug("----")
                    val serverIP = request.host
                    dtsrequests.insertRequest(serverIP, clientIP, f.filename, f.id, fileType, f.length, f.uploadDate)
                    /** **************************/

                    // if xml, add xml contents as JSON metadata
                    if (fileType.equals("application/xml") || fileType.equals("text/xml")) {
                      val xmlToJSON = FilesUtils.readXMLgetJSON(uploadedFile.ref.file)
                      files.addXMLMetadata(f.id, xmlToJSON)
                    }

                    // add file to dataset model
                    // FIXME create a service instead of calling salat directly
                    datasets.addFile(dataset.id, files.get(f.id).get)

                    // index in Elasticsearch
                    current.plugin[ElasticsearchPlugin].foreach { es =>
                      // index dataset
                      datasets.index(dataset_id)
                      // index file
                      es.index(SearchUtils.getElasticsearchObject(f))
                    }

                    // notify extractors that a file has been uploaded and added to a dataset
                    routing.fileCreated(f, Some(dataset), Utils.baseUrl(request).toString, request.apiKey)
                    routing.fileAddedToDataset(f, dataset, Utils.baseUrl(request), request.apiKey)

                    // add file to RDF triple store if triple store is used
                    if (fileType.equals("application/xml") || fileType.equals("text/xml")) {
                      play.api.Play.configuration.getString("userdfSPARQLStore").getOrElse("no") match {
                        case "yes" => {
                          sparql.addFileToGraph(f.id)
                          sparql.linkFileToDataset(f.id, dataset_id)
                        }
                        case _ => {}
                      }
                    }

                    Logger.debug("Uploading Completed")
                    // redirect to dataset page
                    Redirect(routes.Datasets.dataset(dataset_id))
                  }
                  case None => {
                    Logger.error("Could not retrieve file that was just saved.")
                    InternalServerError("Error uploading file")
                  }
                }
              } finally {
                f.ref.clean()
              }
            }.getOrElse {
              BadRequest("File not attached.")
            }
          }
          case None => {
            Logger.error("Error getting dataset" + dataset_id); InternalServerError
          }
        }
      }
      case None => {
        Logger.error("Error getting dataset" + dataset_id); InternalServerError
      }
    }
  }

  def metadataSearch() = PermissionAction(Permission.ViewMetadata) { implicit request =>
    implicit val user = request.user
    Ok(views.html.fileMetadataSearch())
  }

  def generalMetadataSearch() = PermissionAction(Permission.ViewMetadata) { implicit request =>
    implicit val user = request.user
    Ok(views.html.fileGeneralMetadataSearch())
  }

  /**
   * File by section.
   */
  def fileBySection(section_id: UUID) = PermissionAction(Permission.ViewSection, Some(ResourceRef(ResourceRef.section, section_id))) {
    implicit request =>
      sections.get(section_id) match {
        case Some(section) => {
          files.get(section.file_id) match {
            case Some(file) => Redirect(routes.Files.file(file.id))
            case None => InternalServerError("File not found")
          }
        }
        case None => InternalServerError("Section not found")
      }
  }

  ///////////////////////////////////
  //
  //  def myPartHandler: BodyParsers.parse.Multipart.PartHandler[MultipartFormData.FilePart[Result]] = {
  //        parse.Multipart.handleFilePart {
  //          case parse.Multipart.FileInfo(partName, filename, contentType) =>
  //            Logger.debug("Part: " + partName + " filename: " + filename + " contentType: " + contentType);
  //            // TODO RK handle exception for instance if we switch to other DB
  //        Logger.debug("myPartHandler")
  //			val files = current.plugin[MongoSalatPlugin] match {
  //			  case None    => throw new RuntimeException("No MongoSalatPlugin");
  //			  case Some(x) =>  x.gridFS("uploads")
  //			}
  //            
  //            //Set up the PipedOutputStream here, give the input stream to a worker thread
  //            val pos:PipedOutputStream = new PipedOutputStream();
  //            val pis:PipedInputStream  = new PipedInputStream(pos);
  //            val worker = new util.UploadFileWorker(pis, files);
  //            worker.contentType = contentType.get;
  //            worker.start();
  //
  ////            val mongoFile = files.createFile(f.ref.file)
  ////            val filename = f.ref.file.getName()
  ////            Logger.debug("Uploading file " + filename)
  ////            mongoFile.filename = filename
  ////            mongoFile.contentType = FileUtils.getContentType(filename, contentType)
  ////            mongoFile.save
  ////            val id = mongoFile.getAs[ObjectId]("_id").get.toString
  ////            Ok(views.html.file(mongoFile.asDBObject, id))
  //            
  //            
  //            //Read content to the POS
  //            Iteratee.fold[Array[Byte], PipedOutputStream](pos) { (os, data) =>
  //              os.write(data)
  //              os
  //            }.mapDone { os =>
  //              os.close()
  //              Ok("upload done")
  //            }
  //        }
  //   }
  //  
  //  /**
  //   * Ajax upload. How do we pass in the file name?(parse.temporaryFile)
  //   */
  //  
  //  
  //  def uploadAjax = Action(parse.temporaryFile) { implicit request =>
  //
  //    val f = request.body.file
  //    val filename=f.getName()
  //    
  //    // store file
  //    // TODO is this still used? if so replace null with user.
  //        Logger.debug("uploadAjax")
  //    val file = files.save(new FileInputStream(f.getAbsoluteFile()), filename, None, null)
  //    
  //    file match {
  //      case Some(f) => {
  //         var fileType = f.contentType
  //        
  //        // TODO RK need to replace unknown with the server name
  //        val key = "unknown." + "file."+ f.contentType.replace(".", "_").replace("/", ".")
  //        // TODO RK : need figure out if we can use https
  //        val host = request.origin + request.path.replaceAll("upload$", "")
  //        val id = f.id.toString
  //        current.plugin[RabbitmqPlugin].foreach{_.extract(ExtractorMessage(id, id, host, key, Map.empty, f.length.toString, "", ""))}
  //        current.plugin[ElasticsearchPlugin].foreach{
  //          _.index("files", "file", id, List(("filename",f.filename), ("contentType", f.contentType)))
  //        }
  //        // redirect to file page
  //        Redirect(routes.Files.file(f.id.toString))  
  //      }
  //      case None => {
  //        Logger.error("Could not retrieve file that was just saved.")
  //        InternalServerError("Error uploading file")
  //      }
  //    }
  //  }

  /**
   * Reactive file upload.
   */
  //  def reactiveUpload = Action(BodyParser(rh => new SomeIteratee)) { implicit request =>
  //     Ok("Done")
  //   }

  /**
   * Iteratee for reactive file upload.
   *
   * TODO Finish implementing. Right now it doesn't write to anything.
   */
  // case class SomeIteratee(state: Symbol = 'Cont, input: Input[Array[Byte]] = Empty, 
  //     received: Int = 0) extends Iteratee[Array[Byte], Either[Result, Int]] {
  //   Logger.debug(state + " " + input + " " + received)
  //
  ////   val files = current.plugin[MongoSalatPlugin] match {
  ////			  case None    => throw new RuntimeException("No MongoSalatPlugin");
  ////			  case Some(x) =>  x.gridFS("uploads")
  ////			}
  ////
  ////   val pos:PipedOutputStream = new PipedOutputStream();
  ////   val pis:PipedInputStream  = new PipedInputStream(pos);
  ////   val file = files(pis) { fh =>
  ////     fh.filename = "test-file.txt"
  ////     fh.contentType = "text/plain"
  ////   }
  //			
  //   
  //   def fold[B](
  //     done: (Either[Result, Int], Input[Array[Byte]]) => Promise[B],
  //     cont: (Input[Array[Byte]] => Iteratee[Array[Byte], Either[Result, Int]]) => Promise[B],
  //     error: (String, Input[Array[Byte]]) => Promise[B]
  //   ): Promise[B] = state match {
  //     case 'Done => { 
  //       Logger.debug("Done with upload")
  ////       pos.close()
  //       done(Right(received), Input.Empty) 
  //     }
  //     case 'Cont => cont(in => in match {
  //       case in: El[Array[Byte]] => {
  //         Logger.debug("Getting ready to write " +  in.e.length)
  //    	 try {
  ////         pos.write(in.e)
  //    	 } catch {
  //    	   case error => Logger.error("Error writing to gridfs" + error.toString())
  //    	 }
  //    	 Logger.debug("Calling recursive function")
  //         copy(input = in, received = received + in.e.length)
  //       }
  //       case Empty => {
  //         Logger.debug("Empty")
  //         copy(input = in)
  //       }
  //       case EOF => {
  //         Logger.debug("EOF")
  //         copy(state = 'Done, input = in)
  //       }
  //       case _ => {
  //         Logger.debug("_")
  //         copy(state = 'Error, input = in)
  //       }
  //     })
  //     case _ => { Logger.error("Error uploading file"); error("Some error.", input) }
  //   }
  // }
}
