package controllers

import java.io._
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import javax.inject.Inject
import javax.mail.internet.MimeUtility

import api.Permission
import fileutils.FilesUtils
import models._
import play.api.Logger
import play.api.Play.current
import play.api.data.Form
import play.api.data.Forms._
import play.api.libs.concurrent.Execution.Implicits._
import play.api.libs.iteratee._
import play.api.libs.json.Json._
import services._
import views.html.defaultpages.badRequest

import scala.collection.mutable.ListBuffer



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
  thumbnails: ThumbnailService) extends SecuredController {

  /**
   * Upload form.
   */
  val uploadForm = Form(
    mapping(
      "userid" -> nonEmptyText
    )(FileMD.apply)(FileMD.unapply)
  )  
  
  /**
   * File info.
   */
  def file(id: UUID) = PermissionAction(Permission.ViewFile, Some(ResourceRef(ResourceRef.file, id))) { implicit request =>
    implicit val user = request.user
    Logger.info("GET file with id " + id)
    files.get(id) match {
      case Some(file) => {
        val previewsFromDB = previews.findByFileId(file.id)
        Logger.debug("Previews available: " + previewsFromDB)
        val previewers = Previewers.findPreviewers
        //NOTE Should the following code be unified somewhere since it is duplicated in Datasets and Files for both api and controllers
        val previewsWithPreviewer = {
          val pvf = for (
            p <- previewers; pv <- previewsFromDB;
            if (!p.collection);
            if (!file.showPreviews.equals("None")) && (p.contentType.contains(pv.contentType))
          ) yield {
            (pv.id.toString, p.id, p.path, p.main, api.routes.Previews.download(pv.id).toString, pv.contentType, pv.length)
          }
          if (pvf.length > 0) {
            Map(file -> pvf)
          } else {
            val ff = for (
              p <- previewers;
              if (!p.collection);
              if (!file.showPreviews.equals("None")) && (p.contentType.contains(file.contentType))
            ) yield {
              if (file.licenseData.isDownloadAllowed(user)) {
                (file.id.toString, p.id, p.path, p.main, routes.Files.file(file.id) + "/blob", file.contentType, file.length)
              }
              else {
                (file.id.toString, p.id, p.path, p.main, "null", file.contentType, file.length)
              }
            }
            Map(file -> ff)
          }
        }
        Logger.debug("Previewers available: " + previewsWithPreviewer)


        // add sections to file
        val sectionsByFile = sections.findByFileId(file.id)
        Logger.debug("Sections: " + sectionsByFile)
        val sectionsWithPreviews = sectionsByFile.map { s =>
          val p = previews.findBySectionId(s.id)
          if(p.length>0)
        		s.copy(preview = Some(p(0)))
        	else
        		s.copy(preview = None)
        }

        // Check if file is currently being processed by extractor(s)
        val extractorsActive = extractions.findIfBeingProcessed(file.id)
        
        val userMetadata = files.getUserMetadata(file.id)
        Logger.debug("User metadata: " + userMetadata.toString)
        
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
        val datasetsContainingFile = datasets.findByFileId(file.id).sortBy(_.name)
        val datasetsNotContaining = datasets.findNotContainingFile(file.id).sortBy(_.name)              
        val decodedDatasetsContaining = ListBuffer.empty[models.Dataset]
        val decodedDatasetsNotContaining = ListBuffer.empty[models.Dataset]
        
        for (aDataset <- datasetsContainingFile) {
        	val dDataset = Utils.decodeDatasetElements(aDataset)
        	decodedDatasetsContaining += dDataset
        }
        
        for (aDataset <- datasetsNotContaining) {
        	val dDataset = Utils.decodeDatasetElements(aDataset)
        	decodedDatasetsNotContaining += dDataset
        }
        
        val isRDFExportEnabled = current.plugin[RDFExportService].isDefined

        val extractionsByFile = extractions.findByFileId(id)
        

        Ok(views.html.file(file, id.stringify, decodedCommentsByFile.toList, previewsWithPreviewer, sectionsWithPreviews,
          extractorsActive, decodedDatasetsContaining.toList, decodedDatasetsNotContaining.toList, userMetadata, isRDFExportEnabled, extractionsByFile))
      }
      case None => {
        val error_str = "The file with id " + id + " is not found."
        Logger.error(error_str)
        NotFound(toJson(error_str))
        }
    }
  }
  
  /**
   * List a specific number of files before or after a certain date.
   */
  def list(when: String, date: String, limit: Int, mode: String) = DisabledAction() { implicit request =>
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

    val commentMap = fileList.map{file =>
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
    Ok(views.html.filesList(fileList, commentMap, prev, next, limit, viewMode))
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
	          var nameOfFile = f.filename
	          var flags = ""
	          if(nameOfFile.toLowerCase().endsWith(".ptm")){
		          var thirdSeparatorIndex = nameOfFile.indexOf("__")
	              if(thirdSeparatorIndex >= 0){
	                var firstSeparatorIndex = nameOfFile.indexOf("_")
	                var secondSeparatorIndex = nameOfFile.indexOf("_", firstSeparatorIndex+1)
	            	flags = flags + "+numberofIterations_" +  nameOfFile.substring(0,firstSeparatorIndex) + "+heightFactor_" + nameOfFile.substring(firstSeparatorIndex+1,secondSeparatorIndex)+ "+ptm3dDetail_" + nameOfFile.substring(secondSeparatorIndex+1,thirdSeparatorIndex)
	            	nameOfFile = nameOfFile.substring(thirdSeparatorIndex+2)
	              }
	          }
	        
	        Logger.debug("Uploading file " + nameOfFile)

	        var showPreviews = request.body.asFormUrlEncoded.get("datasetLevel").get(0)

	        // store file       
	        val file = files.save(new FileInputStream(f.ref.file), nameOfFile, f.contentType, identity, showPreviews)
	        val uploadedFile = f
	        file match {
	          case Some(f) => {
		        current.plugin[FileDumpService].foreach{_.dump(DumpOfFile(uploadedFile.ref.file, f.id.toString, nameOfFile))}
	            
	            if(showPreviews.equals("FileLevel"))
	                	flags = flags + "+filelevelshowpreviews"
	            else if(showPreviews.equals("None"))
	                	flags = flags + "+nopreviews"
	             var fileType = f.contentType
				    if(fileType.contains("/zip") || fileType.contains("/x-zip") || nameOfFile.toLowerCase().endsWith(".zip")){
				          fileType = FilesUtils.getMainFileTypeOfZipFile(uploadedFile.ref.file, nameOfFile, "file")			          
                if (fileType.startsWith("ERROR: ")) {
                  Logger.error(fileType.substring(7))
                  InternalServerError(fileType.substring(7))
                }
              }

              // TODO RK need to replace unknown with the server name
              val key = "unknown." + "file." + fileType.replace(".", "_").replace("/", ".")

              val host = Utils.baseUrl(request)
              val id = f.id
              val extra = Map("filename" -> f.filename)
	          current.plugin[RabbitmqPlugin].foreach{_.extract(ExtractorMessage(id, id, host, key, extra, f.length.toString, null, flags))}
              /***** Inserting DTS Requests   **/  
              val clientIP=request.remoteAddress
              val domain=request.domain
              val keysHeader=request.headers.keys

              Logger.debug("clientIP:"+clientIP+ "   domain:= "+domain+ "  keysHeader="+ keysHeader.toString +"\n")
              Logger.debug("Origin: "+request.headers.get("Origin") + "  Referer="+ request.headers.get("Referer")+ " Connections="+request.headers.get("Connection")+"\n \n")
       		  val serverIP= request.host
              dtsrequests.insertRequest(serverIP,clientIP, f.filename, id, fileType, f.length,f.uploadDate)
              /****************************/ 
              //for metadata files
              if(fileType.equals("application/xml") || fileType.equals("text/xml")){
            	  val xmlToJSON = FilesUtils.readXMLgetJSON(uploadedFile.ref.file)
            			  files.addXMLMetadata(id, xmlToJSON)

            			  Logger.debug("xmlmd=" + xmlToJSON)

            			  current.plugin[ElasticsearchPlugin].foreach{
            		  _.index("data", "file", id, List(("filename",f.filename), ("contentType", f.contentType),("datasetId",""),("datasetName",""), ("xmlmetadata", xmlToJSON)))
            	  }
              }
              else{
            	  current.plugin[ElasticsearchPlugin].foreach{
            		  _.index("data", "file", id, List(("filename",f.filename), ("contentType", f.contentType),("datasetId",""),("datasetName","")))
            	  }
              }
	          current.plugin[VersusPlugin].foreach{ _.index(f.id.toString,fileType) }
	        
	           // redirect to extract page
	          Ok(views.html.extract(f.id))
	          }
	         case None => {
	           Logger.error("Could not retrieve file that was just saved.")
	           InternalServerError("Error uploading file")
	         }
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
		          var thirdSeparatorIndex = nameOfFile.indexOf("__")
	              if(thirdSeparatorIndex >= 0){
	                var firstSeparatorIndex = nameOfFile.indexOf("_")
	                var secondSeparatorIndex = nameOfFile.indexOf("_", firstSeparatorIndex+1)
	            	flags = flags + "+numberofIterations_" +  nameOfFile.substring(0,firstSeparatorIndex) + "+heightFactor_" + nameOfFile.substring(firstSeparatorIndex+1,secondSeparatorIndex)+ "+ptm3dDetail_" + nameOfFile.substring(secondSeparatorIndex+1,thirdSeparatorIndex)
	            	nameOfFile = nameOfFile.substring(thirdSeparatorIndex+2)
	              }
	          }	       
	        Logger.debug("Uploading file " + nameOfFile)

	        var showPreviews = request.body.asFormUrlEncoded.get("datasetLevel").get(0)

	        // store file       
	        val file = files.save(new FileInputStream(f.ref.file), nameOfFile, f.contentType, identity, showPreviews)
	        val uploadedFile = f
	        file match {
	          case Some(f) => {
              var option_user = users.findByIdentity(identity)
              events.addObjectEvent(option_user, f.id, f.filename, "upload_file")
	            if(showPreviews.equals("FileLevel"))
	                	flags = flags + "+filelevelshowpreviews"
	            else if(showPreviews.equals("None"))
	                	flags = flags + "+nopreviews"
	             var fileType = f.contentType
				    if(fileType.contains("/zip") || fileType.contains("/x-zip") || nameOfFile.toLowerCase().endsWith(".zip")){
				          fileType = FilesUtils.getMainFileTypeOfZipFile(uploadedFile.ref.file, nameOfFile, "file")			          
				          if(fileType.startsWith("ERROR: ")){
				             Logger.error(fileType.substring(7))
				             InternalServerError(fileType.substring(7))
				          }
				          if(fileType.equals("imageset/ptmimages-zipped") || fileType.equals("imageset/ptmimages+zipped")|| fileType.equals("multi/files-ptm-zipped") ){
				             if(fileType.equals("multi/files-ptm-zipped")){
	            				    fileType = "multi/files-zipped";
	            				  }
				            
				              var thirdSeparatorIndex = nameOfFile.indexOf("__")
				              if(thirdSeparatorIndex >= 0){
				                var firstSeparatorIndex = nameOfFile.indexOf("_")
				                var secondSeparatorIndex = nameOfFile.indexOf("_", firstSeparatorIndex+1)
				            	flags = flags + "+numberofIterations_" +  nameOfFile.substring(0,firstSeparatorIndex) + "+heightFactor_" + nameOfFile.substring(firstSeparatorIndex+1,secondSeparatorIndex)+ "+ptm3dDetail_" + nameOfFile.substring(secondSeparatorIndex+1,thirdSeparatorIndex)
				            	nameOfFile = nameOfFile.substring(thirdSeparatorIndex+2)
				            	files.renameFile(f.id, nameOfFile)
				              }
				              files.setContentType(f.id, fileType)
				          }
				    }
	            
	            current.plugin[FileDumpService].foreach{_.dump(DumpOfFile(uploadedFile.ref.file, f.id.toString, nameOfFile))}
	            
	            // TODO RK need to replace unknown with the server name
	            val key = "unknown." + "file."+ fileType.replace(".","_").replace("/", ".")

	            val host = Utils.baseUrl(request) + request.path.replaceAll("upload$", "")
	            val id = f.id
	            
	            /***** Inserting DTS Requests   **/  
	            
	            val clientIP=request.remoteAddress
	            //val clientIP=request.headers.get("Origin").get
                val domain=request.domain
                val keysHeader=request.headers.keys
                //request.
                Logger.debug("---\n \n")
            
                Logger.debug("clientIP:"+clientIP+ "   domain:= "+domain+ "  keysHeader="+ keysHeader.toString +"\n")
                Logger.debug("Origin: "+request.headers.get("Origin") + "  Referer="+ request.headers.get("Referer")+ " Connections="+request.headers.get("Connection")+"\n \n")
                
                Logger.debug("----")
                val serverIP= request.host
              val extra = Map("filename" -> f.filename)
	            dtsrequests.insertRequest(serverIP,clientIP, f.filename, id, fileType, f.length,f.uploadDate)
	           /****************************/ 
              // TODO replace null with None
	            current.plugin[RabbitmqPlugin].foreach{_.extract(ExtractorMessage(id, id, host, key, extra, f.length.toString, null, flags))}

	            val dateFormat = new SimpleDateFormat("dd/MM/yyyy") 

	            
	            //for metadata files
	            if(fileType.equals("application/xml") || fileType.equals("text/xml")){
	              val xmlToJSON = FilesUtils.readXMLgetJSON(uploadedFile.ref.file)
	              files.addXMLMetadata(id, xmlToJSON)
	              
	              Logger.debug("xmlmd=" + xmlToJSON)
	              
	              current.plugin[ElasticsearchPlugin].foreach{
		              _.index("data", "file", id, List(("filename",f.filename), ("contentType", f.contentType), ("author", identity.fullName), ("uploadDate", dateFormat.format(new Date())),("datasetId",""),("datasetName",""), ("xmlmetadata", xmlToJSON)))
		            }
	            }
	            else{
		            current.plugin[ElasticsearchPlugin].foreach{
		              _.index("data", "file", id, List(("filename",f.filename), ("contentType", f.contentType), ("author", identity.fullName), ("uploadDate", dateFormat.format(new Date())),("datasetId",""),("datasetName","")))
		            }
	            }

	             current.plugin[VersusPlugin].foreach{ _.indexFile(f.id, fileType) }

	             //add file to RDF triple store if triple store is used
	             if(fileType.equals("application/xml") || fileType.equals("text/xml")){
		             play.api.Play.configuration.getString("userdfSPARQLStore").getOrElse("no") match{      
			             case "yes" => sparql.addFileToGraph(f.id)
			             case _ => {}		             
		             }
	             }
	             
	            current.plugin[AdminsNotifierPlugin].foreach{
                _.sendAdminsNotification(Utils.baseUrl(request), "File","added",f.id.stringify, nameOfFile)}
	            
	            //Correctly set the updated URLs and data that is needed for the interface to correctly 
	            //update the display after a successful upload.
	            var retMap = Map("files" -> 
	                Seq(
	                    toJson(
	                        Map(
	                            "name" -> toJson(nameOfFile),
	                            "size" -> toJson(uploadedFile.ref.file.length()),
	                            "url" -> toJson(routes.Files.file(f.id).absoluteURL(false)),
	                            "deleteUrl" -> toJson(api.routes.Files.removeFile(f.id).absoluteURL(false)),
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
	           var retMap = Map("files" -> 
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
	         var retMap = Map("files" -> 
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
      if (UUID.isValid(id.stringify)) {
          //Check the license type before doing anything. 
          files.get(id) match {
              case Some(file) => {                                                                                                             
                  if (file.licenseData.isDownloadAllowed(request.user)) {
                      files.getBytes(id) match {
                      case Some((inputStream, filename, contentType, contentLength)) => {
                          request.headers.get(RANGE) match {
                          case Some(value) => {
                              val range: (Long, Long) = value.substring("bytes=".length).split("-") match {
                              case x if x.length == 1 => (x.head.toLong, contentLength - 1)
                              case x => (x(0).toLong, x(1).toLong)
                          }
                          range match { case (start,end) =>

                          inputStream.skip(start)
                          import play.api.mvc.{ResponseHeader, SimpleResult}
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
                                          body = Enumerator.fromStream(inputStream)
                                  )
                          }
                          }
                          case None => {
                            val userAgent = request.headers("user-agent")
                            val filenameStar = if (userAgent.indexOf("MSIE") > -1) {
                              URLEncoder.encode(filename, "UTF-8")
                            } else {
                              MimeUtility.encodeText(filename).replaceAll(",", "%2C")
                            }
                            Ok.chunked(Enumerator.fromStream(inputStream))
                              .withHeaders(CONTENT_TYPE -> contentType)
                              .withHeaders(CONTENT_DISPOSITION -> ("attachment; filename*=UTF-8''" + filenameStar))

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
                  Logger.info(s"Error getting the file with id $id.")
                  BadRequest("Invalid file ID")
              }
          }
               
      }
      else {
          Logger.error(s"The given id $id is not a valid ObjectId.")
          BadRequest(toJson(s"The given id $id is not a valid ObjectId."))
      }
  }

  def thumbnail(id: UUID) = PermissionAction(Permission.ViewFile) { implicit request =>
    thumbnails.getBlob(id) match {
      case Some((inputStream, filename, contentType, contentLength)) => {
        request.headers.get(RANGE) match {
	          case Some(value) => {
	            val range: (Long,Long) = value.substring("bytes=".length).split("-") match {
	              case x if x.length == 1 => (x.head.toLong, contentLength - 1)
	              case x => (x(0).toLong,x(1).toLong)
	            }
	            range match { case (start,end) =>

	             
	              inputStream.skip(start)
	              import play.api.mvc.{ResponseHeader, SimpleResult}
	              SimpleResult(
	                header = ResponseHeader(PARTIAL_CONTENT,
	                  Map(
	                    CONNECTION -> "keep-alive",
	                    ACCEPT_RANGES -> "bytes",
	                    CONTENT_RANGE -> "bytes %d-%d/%d".format(start,end,contentLength),
	                    CONTENT_LENGTH -> (end - start + 1).toString,
	                    CONTENT_TYPE -> contentType
	                  )
	                ),
	                body = Enumerator.fromStream(inputStream)
	              )
	            }
	          }
	          case None => {
	            Ok.chunked(Enumerator.fromStream(inputStream))
	            	.withHeaders(CONTENT_TYPE -> contentType)
	            	.withHeaders(CONTENT_DISPOSITION -> ("attachment; filename=" + filename))
      
	          }
	        }
      }
      case None => {
        Logger.error("Error getting thumbnail" + id)
        NotFound
      }      
    }
    
  }

  /**
   * Uploads query to temporary folder.
   * Gets type of index and list of sections, and passes on to the Search controller
  */
  def uploadSelectQuery() = PermissionAction(Permission.ViewDataset)(parse.multipartFormData) { implicit request =>
    //=== processing searching within files or sections of files or both ===
    //dataParts are from the seach form in view/multimediasearch
    //get type of index and list of sections, and pass on to the Search controller
    //pass them on to Search.findSimilarToQueryFile for further processing
    val dataParts = request.body.dataParts   
    //indexType in dataParts is a sequence of just one element
    val typeToSearch = dataParts("indexType").head
    //get a list of sections to be searched
    var sections:List[String] = List.empty[String]    
    if  ( typeToSearch.equals("sectionsSome")  &&  dataParts.contains("sections") ){
        sections = dataParts("sections").toList
    }  
    //END OF: processing searching within files or sections of files or both    
    request.body.file("File").map { f =>
        var nameOfFile = f.filename
      	var flags = ""
      	if(nameOfFile.toLowerCase().endsWith(".ptm")){
      			  var thirdSeparatorIndex = nameOfFile.indexOf("__")
	              if(thirdSeparatorIndex >= 0){
	                var firstSeparatorIndex = nameOfFile.indexOf("_")
	                var secondSeparatorIndex = nameOfFile.indexOf("_", firstSeparatorIndex+1)
	            	flags = flags + "+numberofIterations_" +  nameOfFile.substring(0,firstSeparatorIndex) + "+heightFactor_" +
                        nameOfFile.substring(firstSeparatorIndex+1,secondSeparatorIndex)+ "+ptm3dDetail_" +
                        nameOfFile.substring(secondSeparatorIndex+1,thirdSeparatorIndex)
	            	nameOfFile = nameOfFile.substring(thirdSeparatorIndex+2)
	              }
      	}        
        Logger.debug("Controllers/Files Uploading file " + nameOfFile)
        
        // store file       
        Logger.info("uploadSelectQuery")
         val file = queries.save(new FileInputStream(f.ref.file), nameOfFile, f.contentType)
         val uploadedFile = f
        
        file match {
          case Some(f) => {
                       
            var fileType = f.contentType
			    if(fileType.contains("/zip") || fileType.contains("/x-zip") || nameOfFile.toLowerCase().endsWith(".zip")){
			          fileType = FilesUtils.getMainFileTypeOfZipFile(uploadedFile.ref.file, nameOfFile, "file")			          
			          if(fileType.startsWith("ERROR: ")){
			             Logger.error(fileType.substring(7))
			             InternalServerError(fileType.substring(7))
			          }
			          if(fileType.equals("imageset/ptmimages-zipped") || fileType.equals("imageset/ptmimages+zipped")|| fileType.equals("multi/files-ptm-zipped") ){
			        	  		if(fileType.equals("multi/files-ptm-zipped")){
	            				    fileType = "multi/files-zipped";
	            				  }
			            
				              var thirdSeparatorIndex = nameOfFile.indexOf("__")
				              if(thirdSeparatorIndex >= 0){
				                var firstSeparatorIndex = nameOfFile.indexOf("_")
				                var secondSeparatorIndex = nameOfFile.indexOf("_", firstSeparatorIndex+1)
				            	flags = flags + "+numberofIterations_" +  nameOfFile.substring(0,firstSeparatorIndex) + "+heightFactor_" + nameOfFile.substring(firstSeparatorIndex+1,secondSeparatorIndex)+ "+ptm3dDetail_" + nameOfFile.substring(secondSeparatorIndex+1,thirdSeparatorIndex)
				            	nameOfFile = nameOfFile.substring(thirdSeparatorIndex+2)
				            	files.renameFile(f.id, nameOfFile)
				              }
				              files.setContentType(f.id, fileType)
				      }
			    }
            
            current.plugin[FileDumpService].foreach{_.dump(DumpOfFile(uploadedFile.ref.file, f.id.toString, nameOfFile))}
            
            // TODO RK need to replace unknown with the server name
            //key needs to contain 'query' when uploading a query
            //since the thumbnail extractor during processing will need to upload to correct mongo collection.
            val key = "unknown." + "query."+ fileType.replace("/", ".")           
            val host = Utils.baseUrl(request)
            val id = f.id
            val path=f.path

            // TODO replace null with None
            current.plugin[RabbitmqPlugin].foreach{_.extract(ExtractorMessage(id, id, host, key, Map.empty, f.length.toString, null, flags))}
            
            val dateFormat = new SimpleDateFormat("dd/MM/yyyy") 
            
            //for metadata files
	            if(fileType.equals("application/xml") || fileType.equals("text/xml")){
	              val xmlToJSON = FilesUtils.readXMLgetJSON(uploadedFile.ref.file)
	              files.addXMLMetadata(id, xmlToJSON)	              
	              Logger.debug("xmlmd=" + xmlToJSON)	              
	              current.plugin[ElasticsearchPlugin].foreach{
		              _.index("files", "file", id, List(("filename",nameOfFile), ("contentType", f.contentType), ("uploadDate", dateFormat.format(new Date())), ("xmlmetadata", xmlToJSON)))
		            }
	            }
	            else{
		            current.plugin[ElasticsearchPlugin].foreach{
		            	_.index("files", "file", id, List(("filename",nameOfFile), ("contentType", f.contentType), ("uploadDate", dateFormat.format(new Date()))))
		            }
	            }	            
	            //add file to RDF triple store if triple store is used
	            if(fileType.equals("application/xml") || fileType.equals("text/xml")){
	             play.api.Play.configuration.getString("userdfSPARQLStore").getOrElse("no") match{      
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
    }.getOrElse {
      BadRequest("File not attached.")
    }
  }

  /* Drag and drop */
  def uploadDragDrop() = PermissionAction(Permission.ViewDataset)(parse.multipartFormData) { implicit request =>
      request.body.file("File").map { f =>
        var nameOfFile = f.filename
      	var flags = ""
      	if(nameOfFile.toLowerCase().endsWith(".ptm")){
      			  var thirdSeparatorIndex = nameOfFile.indexOf("__")
	              if(thirdSeparatorIndex >= 0){
	                var firstSeparatorIndex = nameOfFile.indexOf("_")
	                var secondSeparatorIndex = nameOfFile.indexOf("_", firstSeparatorIndex+1)
	            	flags = flags + "+numberofIterations_" +  nameOfFile.substring(0,firstSeparatorIndex) + "+heightFactor_" + nameOfFile.substring(firstSeparatorIndex+1,secondSeparatorIndex)+ "+ptm3dDetail_" + nameOfFile.substring(secondSeparatorIndex+1,thirdSeparatorIndex)
	            	nameOfFile = nameOfFile.substring(thirdSeparatorIndex+2)
	              }
      	}
        
        Logger.debug("Uploading file " + nameOfFile)
        
        // store file
        Logger.info("uploadDragDrop")
        val file = queries.save(new FileInputStream(f.ref.file), nameOfFile, f.contentType)
        val uploadedFile = f
        file match {
          case Some(f) => {
                       
             var fileType = f.contentType
			    if(fileType.contains("/zip") || fileType.contains("/x-zip") || nameOfFile.toLowerCase().endsWith(".zip")){
			          fileType = FilesUtils.getMainFileTypeOfZipFile(uploadedFile.ref.file, nameOfFile, "file")			          
			          if(fileType.startsWith("ERROR: ")){
			             Logger.error(fileType.substring(7))
			             InternalServerError(fileType.substring(7))
			          }
			          if(fileType.equals("imageset/ptmimages-zipped") || fileType.equals("imageset/ptmimages+zipped")|| fileType.equals("multi/files-ptm-zipped") ){
			        	  		if(fileType.equals("multi/files-ptm-zipped")){
	            				    fileType = "multi/files-zipped";
	            				  }
			            
				              var thirdSeparatorIndex = nameOfFile.indexOf("__")
				              if(thirdSeparatorIndex >= 0){
				                var firstSeparatorIndex = nameOfFile.indexOf("_")
				                var secondSeparatorIndex = nameOfFile.indexOf("_", firstSeparatorIndex+1)
				            	flags = flags + "+numberofIterations_" +  nameOfFile.substring(0,firstSeparatorIndex) + "+heightFactor_" + nameOfFile.substring(firstSeparatorIndex+1,secondSeparatorIndex)+ "+ptm3dDetail_" + nameOfFile.substring(secondSeparatorIndex+1,thirdSeparatorIndex)
				            	nameOfFile = nameOfFile.substring(thirdSeparatorIndex+2)
				            	files.renameFile(f.id, nameOfFile)
				              }
				              files.setContentType(f.id, fileType)
				      }
			    }
             
             current.plugin[FileDumpService].foreach{_.dump(DumpOfFile(uploadedFile.ref.file, f.id.toString, nameOfFile))}
            
            // TODO RK need to replace unknown with the server name
            val key = "unknown." + "file."+ fileType.replace(".","_").replace("/", ".")

            val host = Utils.baseUrl(request) + request.path.replaceAll("upload$", "")
            val id = f.id
            val extra = Map("filename" -> f.filename)

            // TODO replace null with None
            current.plugin[RabbitmqPlugin].foreach{_.extract(ExtractorMessage(id, id, host, key, extra, f.length.toString, null, flags))}
            
            val dateFormat = new SimpleDateFormat("dd/MM/yyyy")
            
            //for metadata files
	            if(fileType.equals("application/xml") || fileType.equals("text/xml")){
	              val xmlToJSON = FilesUtils.readXMLgetJSON(uploadedFile.ref.file)
	              files.addXMLMetadata(id, xmlToJSON)	              
	              Logger.debug("xmlmd=" + xmlToJSON)	              
	              current.plugin[ElasticsearchPlugin].foreach{
		              _.index("data", "file", id, List(("filename",nameOfFile), ("contentType", f.contentType), ("uploadDate", dateFormat.format(new Date())), ("xmlmetadata", xmlToJSON)))
		            }
	            }
	            else{
		            current.plugin[ElasticsearchPlugin].foreach{
		            	_.index("data", "file", id, List(("filename",nameOfFile), ("contentType", f.contentType), ("uploadDate", dateFormat.format(new Date()))))
		            }
	            }	            
	            //add file to RDF triple store if triple store is used
	            if(fileType.equals("application/xml") || fileType.equals("text/xml")){
	             play.api.Play.configuration.getString("userdfSPARQLStore").getOrElse("no") match{      
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
    }.getOrElse {
      BadRequest("File not attached.")
    }
  }


  def uploaddnd(dataset_id: UUID) = PermissionAction(Permission.CreateDataset, Some(ResourceRef(ResourceRef.dataset, dataset_id)))(parse.multipartFormData) { implicit request =>
    request.user match {
      case Some(identity) => {
        datasets.get(dataset_id) match {
          case Some(dataset) => {
            request.body.file("File").map { f =>
			      var nameOfFile = f.filename
			      var flags = ""
			      if(nameOfFile.toLowerCase().endsWith(".ptm")){
			    	  var thirdSeparatorIndex = nameOfFile.indexOf("__")
		              if(thirdSeparatorIndex >= 0){
		                var firstSeparatorIndex = nameOfFile.indexOf("_")
		                var secondSeparatorIndex = nameOfFile.indexOf("_", firstSeparatorIndex+1)
		            	flags = flags + "+numberofIterations_" +  nameOfFile.substring(0,firstSeparatorIndex) + "+heightFactor_" + nameOfFile.substring(firstSeparatorIndex+1,secondSeparatorIndex)+ "+ptm3dDetail_" + nameOfFile.substring(secondSeparatorIndex+1,thirdSeparatorIndex)
		            	nameOfFile = nameOfFile.substring(thirdSeparatorIndex+2)
		              }
			      }	
			    
				  Logger.debug("Uploading file " + nameOfFile)
				  val showPreviews = request.body.asFormUrlEncoded.get("datasetLevel").get(0)
				  // store file
				  val file = files.save(new FileInputStream(f.ref.file), nameOfFile,f.contentType, identity, showPreviews)
				  val uploadedFile = f
				  
				  // submit file for extraction			
				  file match {
				  case Some(f) => {
				    				    
	                if(showPreviews.equals("FileLevel"))
	                	flags = flags + "+filelevelshowpreviews"
	                else if(showPreviews.equals("None"))
	                	flags = flags + "+nopreviews"
					  var fileType = f.contentType
					  if(fileType.contains("/zip") || fileType.contains("/x-zip") || nameOfFile.toLowerCase().endsWith(".zip")){
						  fileType = FilesUtils.getMainFileTypeOfZipFile(uploadedFile.ref.file, nameOfFile, "dataset")			          
						  if(fileType.startsWith("ERROR: ")){
								Logger.error(fileType.substring(7))
								InternalServerError(fileType.substring(7))
								}
						  if(fileType.equals("imageset/ptmimages-zipped") || fileType.equals("imageset/ptmimages+zipped")|| fileType.equals("multi/files-ptm-zipped") ){
							  if(fileType.equals("multi/files-ptm-zipped")){
	            				    fileType = "multi/files-zipped";
	            				  }
						    
				              var thirdSeparatorIndex = nameOfFile.indexOf("__")
				              if(thirdSeparatorIndex >= 0){
				                var firstSeparatorIndex = nameOfFile.indexOf("_")
				                var secondSeparatorIndex = nameOfFile.indexOf("_", firstSeparatorIndex+1)
				            	flags = flags + "+numberofIterations_" +  nameOfFile.substring(0,firstSeparatorIndex) + "+heightFactor_" + nameOfFile.substring(firstSeparatorIndex+1,secondSeparatorIndex)+ "+ptm3dDetail_" + nameOfFile.substring(secondSeparatorIndex+1,thirdSeparatorIndex)
				            	nameOfFile = nameOfFile.substring(thirdSeparatorIndex+2)
				            	files.renameFile(f.id, nameOfFile)
				              }
				              files.setContentType(f.id, fileType)
						  }
					  }
	                
	                current.plugin[FileDumpService].foreach{_.dump(DumpOfFile(uploadedFile.ref.file, f.id.toString, nameOfFile))}
				  	  
					  // TODO RK need to replace unknown with the server name
					  val key = "unknown." + "file."+ fileType.replace(".", "_").replace("/", ".")

							  val host = Utils.baseUrl(request) + request.path.replaceAll("uploaddnd/[A-Za-z0-9_]*$", "")
							  val id = f.id
							  
							  /***** Inserting DTS Requests   **/  
	            
						            val clientIP=request.remoteAddress
					                val domain=request.domain
					                val keysHeader=request.headers.keys
					                //request.
					                Logger.debug("---\n \n")
					            
					                Logger.debug("clientIP:"+clientIP+ "   domain:= "+domain+ "  keysHeader="+ keysHeader.toString +"\n")
					                Logger.debug("Origin: "+request.headers.get("Origin") + "  Referer="+ request.headers.get("Referer")+ " Connections="+request.headers.get("Connection")+"\n \n")
					                
					                Logger.debug("----")
					                val serverIP= request.host
						            dtsrequests.insertRequest(serverIP,clientIP, f.filename, id, fileType, f.length,f.uploadDate)
						      
						      /****************************/

		            val extra = Map("filename" -> f.filename)
							  current.plugin[RabbitmqPlugin].foreach{_.extract(ExtractorMessage(id, id, host, key, extra, f.length.toString, dataset_id, flags))}
					  
					  val dateFormat = new SimpleDateFormat("dd/MM/yyyy")

					  //for metadata files
					  if(fileType.equals("application/xml") || fileType.equals("text/xml")){
						  		  val xmlToJSON = FilesUtils.readXMLgetJSON(uploadedFile.ref.file)
								  files.addXMLMetadata(id, xmlToJSON)

								  Logger.debug("xmlmd=" + xmlToJSON)

								  current.plugin[ElasticsearchPlugin].foreach{
						  			  _.index("data", "file", id, List(("filename",f.filename), ("contentType", f.contentType), ("author", identity.fullName), ("uploadDate", dateFormat.format(new Date())),("datasetId",dataset.id.toString()),("datasetName",dataset.name), ("xmlmetadata", xmlToJSON)))
						  		  }
					  }
					  else{
						  current.plugin[ElasticsearchPlugin].foreach{
							  _.index("data", "file", id, List(("filename",f.filename), ("contentType", f.contentType), ("author", identity.fullName), ("uploadDate", dateFormat.format(new Date())),("datasetId",dataset.id.toString()),("datasetName",dataset.name)))
						  }
					  }
					  
					  // add file to dataset
					  // TODO create a service instead of calling salat directly
					  val theFile = files.get(f.id).get
					  datasets.addFile(dataset.id, theFile)
					  if(!theFile.xmlMetadata.isEmpty){
						  datasets.index(dataset_id)
					  }
					  
					// TODO RK need to replace unknown with the server name and dataset type
 			    	val dtkey = "unknown." + "dataset."+ "unknown"
			    	
			        current.plugin[RabbitmqPlugin].foreach{_.extract(ExtractorMessage(dataset_id, dataset_id, host, dtkey, Map.empty, f.length.toString, dataset_id, ""))}
 			    	
 			    	//add file to RDF triple store if triple store is used
 			    	if(fileType.equals("application/xml") || fileType.equals("text/xml")){
		             play.api.Play.configuration.getString("userdfSPARQLStore").getOrElse("no") match{      
			             case "yes" => {
                     sparql.addFileToGraph(f.id)
                     sparql.linkFileToDataset(f.id, dataset_id)
			             }
			             case _ => {}
		             }
 			    	}
		
					  // redirect to dataset page
					  Logger.info("Uploading Completed")
					  
					  Redirect(routes.Datasets.dataset(dataset_id))
				  	}
				  	case None => {
					  Logger.error("Could not retrieve file that was just saved.")
					  InternalServerError("Error uploading file")
				  	}
				  }
			  }.getOrElse {
				  BadRequest("File not attached.")
			  }
		  }
		  case None => {Logger.error("Error getting dataset" + dataset_id); InternalServerError}
      	}
      }
      case None => { Logger.error("Error getting dataset" + dataset_id); InternalServerError }
    }
  }

  def metadataSearch() = PermissionAction(Permission.ViewFile) { implicit request =>
    implicit val user = request.user
  	Ok(views.html.fileMetadataSearch()) 
  }

  def generalMetadataSearch()  = PermissionAction(Permission.ViewFile) { implicit request =>
    implicit val user = request.user
  	Ok(views.html.fileGeneralMetadataSearch()) 
  }

  ///////////////////////////////////
  //
  //  def myPartHandler: BodyParsers.parse.Multipart.PartHandler[MultipartFormData.FilePart[Result]] = {
  //        parse.Multipart.handleFilePart {
  //          case parse.Multipart.FileInfo(partName, filename, contentType) =>
  //            Logger.info("Part: " + partName + " filename: " + filename + " contentType: " + contentType);
  //            // TODO RK handle exception for instance if we switch to other DB
  //        Logger.info("myPartHandler")
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
  ////            mongoFile.contentType = play.api.libs.MimeTypes.forFileName(filename).getOrElse(play.api.http.ContentTypes.BINARY)
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
  //        Logger.info("uploadAjax")
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
