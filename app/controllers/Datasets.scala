package controllers

import play.api.Logger
import play.api.data.Form
import play.api.data.Forms._
import play.api.mvc.Cookie
import java.io.FileInputStream
import play.api.Play.current
import play.api.libs.json.Json._
import services._
import java.util.Date
import java.text.SimpleDateFormat
import views.html.defaultpages.badRequest
import models._
import fileutils.FilesUtils
import api.Permission
import javax.inject.Inject
import scala.Some
import scala.xml.Utility
import services.ExtractorMessage
import api.WithPermission
import org.apache.commons.lang.StringEscapeUtils


/**
 * A dataset is a collection of files and streams.
 *
 * @author Luigi Marini
 *
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
  previewService: PreviewService) extends SecuredController {

  object ActivityFound extends Exception {}

  /**
   * Display the page that allows users to create new datasets, either by uploading multiple new files,
   * or by selecting multiple existing files.
   */
  def newDataset() = SecuredAction(authorization = WithPermission(Permission.CreateDatasets)) {
    implicit request =>
      implicit val user = request.user
      val filesList = for (file <- files.listFilesNotIntermediate.sortBy(_.filename)) yield (file.id.toString(), file.filename)
      Ok(views.html.newDataset(filesList)).flashing("error" -> "Please select ONE file (upload new or existing)")
  }

  /**
   * List datasets.
   */
  def list(when: String, date: String, limit: Int, mode: String) = SecuredAction(authorization = WithPermission(Permission.ListDatasets)) {
    implicit request =>      
      implicit val user = request.user
      var direction = "b"
      if (when != "") direction = when
      val formatter = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS")
      var prev, next = ""
      var datasetList = List.empty[models.Dataset]
      if (direction == "b") {
        datasetList = datasets.listDatasetsBefore(date, limit)
      } else if (direction == "a") {
        datasetList = datasets.listDatasetsAfter(date, limit)
      } else {
        badRequest
      }
      
      // latest object
      val latest = datasets.latest()
      // first object
      val first = datasets.first()
      var firstPage = false
      var lastPage = false
      if (latest.size == 1) {
        firstPage = datasetList.exists(_.id.equals(latest.get.id))
        lastPage = datasetList.exists(_.id.equals(first.get.id))
        Logger.debug("latest " + latest.get.id + " first page " + firstPage)
        Logger.debug("first " + first.get.id + " last page " + lastPage)
      }
      if (datasetList.size > 0) {
        if (date != "" && !firstPage) {
          // show prev button
          prev = formatter.format(datasetList.head.created)
        }
        if (!lastPage) {
          // show next button
          next = formatter.format(datasetList.last.created)
        }
      }

      val commentMap = datasetList.map{dataset =>
        var allComments = comments.findCommentsByDatasetId(dataset.id)
        dataset.files.map { file =>
          allComments ++= comments.findCommentsByFileId(file.id)
          sections.findByFileId(file.id).map { section =>
            allComments ++= comments.findCommentsBySectionId(section.id)
          }
        }
        dataset.id -> allComments.size
      }.toMap


      //Modifications to decode HTML entities that were stored in an encoded fashion as part 
      //of the datasets names or descriptions
      val aBuilder = new StringBuilder()
      for (aDataset <- datasetList) {
          decodeDatasetElements(aDataset)
      }
      
        //Code to read the cookie data. On default calls, without a specific value for the mode, the cookie value is used.
	    //Note that this cookie will, in the long run, pertain to all the major high-level views that have the similar 
	    //modal behavior for viewing data. Currently the options are tile and list views. MMF - 12/14	
		var viewMode = mode;
		//Always check to see if there is a session value          
		request.cookies.get("view-mode") match {
	    	case Some(cookie) => {
	    		viewMode = cookie.value
	    	}
	    	case None => {
	    		//If there is no cookie, and a mode was not passed in, default it to tile
	    	    if (viewMode == null || viewMode == "") {
	    	        viewMode = "tile"
	    	    }
	    	}
		}
      
      //Pass the viewMode into the view
      Ok(views.html.datasetList(datasetList, commentMap, prev, next, limit, viewMode))
  }
  def userDatasets(when: String, date: String, limit: Int, mode: String, email: String) = SecuredAction(authorization = WithPermission(Permission.ListDatasets)) {
    implicit request =>
      implicit val user = request.user
      var direction = "b"
      if (when != "") direction = when
      val formatter = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS")
      var prev, next = ""
      var datasetList = List.empty[models.Dataset]
      if (direction == "b") {
        datasetList = datasets.listUserDatasetsBefore(date, limit, email)
      } else if (direction == "a") {
        datasetList = datasets.listUserDatasetsAfter(date, limit, email)
      } else {
        badRequest
      }
      // latest object
      val latest = datasets.latest()
      // first object
      val first = datasets.first()
      var firstPage = false
      var lastPage = false
      if (latest.size == 1) {
        firstPage = datasetList.exists(_.id.equals(latest.get.id))
        lastPage = datasetList.exists(_.id.equals(first.get.id))
        Logger.debug("latest " + latest.get.id + " first page " + firstPage)
        Logger.debug("first " + first.get.id + " last page " + lastPage)
      }
      if (datasetList.size > 0) {
        if (date != "" && !firstPage) {
          // show prev button
          prev = formatter.format(datasetList.head.created)
        }
        if (!lastPage) {
          // show next button
          next = formatter.format(datasetList.last.created)
        }
      }
      

      

      val commentMap = datasetList.map{dataset =>
        var allComments = comments.findCommentsByDatasetId(dataset.id)
        dataset.files.map { file =>
          allComments ++= comments.findCommentsByFileId(file.id)
          sections.findByFileId(file.id).map { section =>
            allComments ++= comments.findCommentsBySectionId(section.id)
          }
        }
        dataset.id -> allComments.size
      }.toMap


      //Modifications to decode HTML entities that were stored in an encoded fashion as part 
      //of the datasets names or descriptions
      val aBuilder = new StringBuilder()
      for (aDataset <- datasetList) {
          decodeDatasetElements(aDataset)
      }
      
      //Code to read the cookie data. On default calls, without a specific value for the mode, the cookie value is used.
      //Note that this cookie will, in the long run, pertain to all the major high-level views that have the similar 
      //modal behavior for viewing data. Currently the options are tile and list views. MMF - 12/14      
      var viewMode = mode;
      
      //Always check to see if there is a session value          
      request.cookies.get("view-mode") match {
          case Some(cookie) => {                  
              viewMode = cookie.value
          }
          case None => {
              //If there is no cookie, and viewMode is not set, default it to tile
              if (viewMode == null || viewMode == "") {
                  viewMode = "tile"
              }
          }
      }                       
      
      Ok(views.html.datasetList(datasetList, commentMap, prev, next, limit, viewMode))
  }


  def addViewer(id: UUID, user: Option[securesocial.core.Identity]) = {
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
  def dataset(id: UUID) = SecuredAction(authorization = WithPermission(Permission.ShowDataset)) { implicit request =>
      implicit val user = request.user
      Previewers.findPreviewers.foreach(p => Logger.info("Previewer found " + p.id))
      datasets.get(id) match {
        case Some(dataset) => {

          val filesInDataset = dataset.files.map(f => files.get(f.id).get)

          val datasetWithFiles = dataset.copy(files = filesInDataset)
          decodeDatasetElements(datasetWithFiles)
          val previewers = Previewers.findPreviewers
          //NOTE Should the following code be unified somewhere since it is duplicated in Datasets and Files for both api and controllers
          val previewslist = for (f <- datasetWithFiles.files) yield {


            // add sections to file
            val sectionsByFile = sections.findByFileId(f.id)
            Logger.debug("Sections: " + sectionsByFile)
            val sectionsWithPreviews = sectionsByFile.map { s =>
              val p = previewService.findBySectionId(s.id)
              if(p.length>0)
                s.copy(preview = Some(p(0)))
              else
                s.copy(preview = None)
            }
            val fileWithSections = f.copy(sections = sectionsWithPreviews)


            val pvf = for (p <- previewers; pv <- fileWithSections.previews; if (fileWithSections.showPreviews.equals("DatasetLevel")) && (p.contentType.contains(pv.contentType))) yield {
              (pv.id.toString, p.id, p.path, p.main, api.routes.Previews.download(pv.id).toString, pv.contentType, pv.length)
            }
            if (pvf.length > 0) {
              fileWithSections -> pvf
            } else {
              val ff = for (p <- previewers; if (f.showPreviews.equals("DatasetLevel")) && (p.contentType.contains(f.contentType))) yield {
                //Change here. If the license allows the file to be downloaded by the current user, go ahead and use the 
                //file bytes as the preview, otherwise return the String null and handle it appropriately on the front end
                if (f.checkLicenseForDownload(user)) {
                    (f.id.toString, p.id, p.path, p.main, routes.Files.download(f.id).toString, f.contentType, f.length)
                }
                else {
                    (f.id.toString, p.id, p.path, p.main, "null", f.contentType, f.length)
                }
              }
              fileWithSections -> ff
            }
          }

          val metadata = datasets.getMetadata(id)
          Logger.debug("Metadata: " + metadata)
          for (md <- metadata) {
            Logger.debug(md.toString)
          }
          val userMetadata = datasets.getUserMetadata(id)
          Logger.debug("User metadata: " + userMetadata.toString)

	          val collectionsOutside = collections.listOutsideDataset(id).sortBy(_.name)
	          val collectionsInside = collections.listInsideDataset(id).sortBy(_.name)
	          val filesOutside = files.listOutsideDataset(id).sortBy(_.filename)

	          var commentsByDataset = comments.findCommentsByDatasetId(id)
	          filesInDataset.map {
	            file =>
	              commentsByDataset ++= comments.findCommentsByFileId(file.id)
	              sections.findByFileId(UUID(file.id.toString)).map { section =>
	                commentsByDataset ++= comments.findCommentsBySectionId(section.id)
	              }
	          }
	          commentsByDataset = commentsByDataset.sortBy(_.posted)
	          
	          val isRDFExportEnabled = current.plugin[RDFExportService].isDefined

          Ok(views.html.dataset(datasetWithFiles, commentsByDataset, previewslist.toMap, metadata, userMetadata, collectionsOutside, collectionsInside, filesOutside, isRDFExportEnabled))
        }
        case None => {
          Logger.error("Error getting dataset" + id); InternalServerError
        }
      }
  }
  
  /**
   * Utility method to modify the elements in a dataset that are encoded when submitted and stored. These elements
   * are decoded when a view requests the objects, so that they can be human readable.
   * 
   * Currently, the following dataset elements are encoded:
   * name
   * description
   *  
   */
  def decodeDatasetElements(dataset: Dataset) {      
      dataset.name = StringEscapeUtils.unescapeHtml(dataset.name)
      dataset.description = StringEscapeUtils.unescapeHtml(dataset.description)
  }

  /**
   * Dataset by section.
   */
  def datasetBySection(section_id: UUID) = SecuredAction(authorization = WithPermission(Permission.ShowDataset)) {
    request =>
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
   * TODO where is this used?
  def upload = Action(parse.temporaryFile) { request =>
    request.body.moveTo(new File("/tmp/picture"))
    Ok("File uploaded")
  }
   */

  /**
   * Controller flow that handles the new multi file uploader workflow for creating a new dataset. Requires name, description, 
   * and id for the dataset. The interface should validate to ensure that these are present before reaching this point, but
   * the checks are made here as well. 
   * 
   */
	def submit() = SecuredAction(parse.multipartFormData, authorization=WithPermission(Permission.CreateDatasets)) { implicit request =>
    implicit val user = request.user
    Logger.debug("------- in Datasets.submit ---------")
    var dsName = request.body. asFormUrlEncoded.getOrElse("name", null)
    var dsDesc = request.body.asFormUrlEncoded.getOrElse("description", null)
    var dsLevel = request.body.asFormUrlEncoded.get("datasetLevel")
    var dsId = request.body.asFormUrlEncoded.getOrElse("datasetid", null)    
	
	if (dsName == null || dsDesc == null) {
		//Changed to return appropriate data and message to the upload interface
	    var retMap = Map("files" -> 
	        Seq(
	            toJson(
	                Map(
	                    "name" -> toJson("Mising Form Data"),
	                    "size" -> toJson(0),
	                    "error" -> toJson("Please ensure that there is a name and a description set.")
	                )
	            )
	        )
	     )
	     Ok(toJson(retMap))
	}
    
    if (dsId == null) {
		//Changed to return appropriate data and message to the upload interface
	    var retMap = Map("files" -> 
	        Seq(
	            toJson(
	                Map(
	                    "name" -> toJson("Dataset was not created correctly."),
	                    "size" -> toJson(0),
	                    "error" -> toJson("Dataset not created correctly. Please try again.")
	                )
	            )
	        )
	     )
	     Ok(toJson(retMap))
	}
    
    user match {
      case Some(identity) => {
          var nameOfFile : String = null
          request.body.file("files[]").map { f =>                     
	          nameOfFile = f.filename
          }
          //The reference for the new dataset          
          datasets.get(UUID(dsId(0))) match {          
              case Some(dataset) => {                                             
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
    
            	    Logger.debug("Datset submit, new file - uploading file " + nameOfFile)
    		        
    		        // store file
            	    Logger.info("Adding file" + identity)
            	    val showPreviews = request.body.asFormUrlEncoded.get("datasetLevel").get(0)
            	    val file = files.save(new FileInputStream(f.ref.file), nameOfFile, f.contentType, identity, showPreviews)
    			    Logger.debug("Uploaded file id is " + file.get.id)
    			    Logger.debug("Uploaded file type is " + f.contentType)
    			    
    			    val uploadedFile = f
    			    file match {
    			      case Some(f) => {
    			        					        
    			        val id = f.id	                	                
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
    			          if(fileType.equals("imageset/ptmimages-zipped") || fileType.equals("imageset/ptmimages+zipped") || fileType.equals("multi/files-ptm-zipped") ){
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
    			        } else if(nameOfFile.toLowerCase().endsWith(".mov")) {
    					  		fileType = "ambiguous/mov";
    			        }
    			        
    			        current.plugin[FileDumpService].foreach{_.dump(DumpOfFile(uploadedFile.ref.file, f.id.toString, nameOfFile))}
    			        
    		    		// TODO RK need to replace unknown with the server name
    		    		val key = "unknown." + "file."+ fileType.replace(".", "_").replace("/", ".")
    
    					val host = Utils.baseUrl(request) + request.path.replaceAll("dataset/submit$", "")
        					
    			        //directly add the file to the dataset via the service
    					datasets.addFile(dataset.id, f)
    			        						
    		    		val dsId = dataset.id
    		    		val dsName = dataset.name
    		    		
    			        current.plugin[RabbitmqPlugin].foreach{_.extract(ExtractorMessage(id, id, host, key, Map.empty, f.length.toString, dsId, flags))}
    			        
    			        val dateFormat = new SimpleDateFormat("dd/MM/yyyy")
    			        
    			        //for metadata files
    					  if(fileType.equals("application/xml") || fileType.equals("text/xml")){
					  		  val xmlToJSON = FilesUtils.readXMLgetJSON(uploadedFile.ref.file)
							  files.addXMLMetadata(f.id, xmlToJSON)
							  datasets.addXMLMetadata(dsId, f.id, xmlToJSON)

							  Logger.debug("xmlmd=" + xmlToJSON)
							  
							  //index the file
							  current.plugin[ElasticsearchPlugin].foreach{
					  			  _.index("data", "file", id, List(("filename",f.filename), ("contentType", fileType), ("author", identity.fullName), ("uploadDate", dateFormat.format(new Date())), ("datasetId",dsId.toString()),("datasetName",dsName), ("xmlmetadata", xmlToJSON)))

					  		  }
					  		  // index dataset
					  		  current.plugin[ElasticsearchPlugin].foreach{_.index("data", "dataset", dsId, 
					  		  List(("name",dsName), ("description", dataset.description), ("author", identity.fullName), ("created", dateFormat.format(new Date())), ("fileId",f.id.toString),("fileName",f.filename), ("collId",""),("collName",""), ("xmlmetadata", xmlToJSON)))}
    					  } else {
    						  //index the file
    
    						  current.plugin[ElasticsearchPlugin].foreach{_.index("data", "file", id, List(("filename",f.filename), ("contentType", fileType), ("author", identity.fullName), ("uploadDate", dateFormat.format(new Date())), ("datasetId",dsId.toString),("datasetName",dsName)))}
    
    						  // index dataset
    						  current.plugin[ElasticsearchPlugin].foreach{_.index("data", "dataset", dsId, 
    						  List(("name",dsName), ("description", dataset.description), ("author", identity.fullName), ("created", dateFormat.format(new Date())), ("fileId",f.id.toString),("fileName",f.filename), ("collId",""),("collName","")))}
    					  }
    			        
        			      // index the file using Versus for content-based retrieval
        			      current.plugin[VersusPlugin].foreach{ _.index(f.id.toString,fileType) }
    
    			    	  // TODO RK need to replace unknown with the server name and dataset type		            
     			    	  val dtkey = "unknown." + "dataset."+ "unknown"
     			    	  current.plugin[RabbitmqPlugin].foreach{_.extract(ExtractorMessage(dsId, dsId, host, dtkey, Map.empty, "0", dsId, ""))}
     			    	
     			    	//add file to RDF triple store if triple store is used
     			    	if(fileType.equals("application/xml") || fileType.equals("text/xml")){
    			             play.api.Play.configuration.getString("userdfSPARQLStore").getOrElse("no") match{      
    				             case "yes" => {
    				               sparql.addFileToGraph(f.id)
    				               sparql.linkFileToDataset(f.id, dsId)
    				             }
    				             case _ => {}
    			             }
    		             }
    
     			    	// Insert DTS Request to the database
     			    	val clientIP=request.remoteAddress
     			    	val serverIP= request.host
     			    	dtsrequests.insertRequest(serverIP,clientIP, f.filename, id, fileType, f.length,f.uploadDate)
     			    	
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
    			        Logger.error("---------- ERROR Could not retrieve file that was just saved.")
    			        //No need to update the service anymore since the dataset has already been created and added earlier. 
    			        //Just send the notifications. MMF - 1/15
    		            current.plugin[AdminsNotifierPlugin].foreach{
    			        _.sendAdminsNotification(Utils.baseUrl(request), "Dataset","added",dataset.id.stringify, dataset.name)}
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
    	        }.getOrElse{
    	            var retMap = Map("files" ->
    	                                Seq(
    	                                    toJson(
    	                                        Map(
    	                                            "name" -> toJson("File not received"),
    	                                            "size" -> toJson(0),
    	                                            "error" -> toJson("The file was not correctly received by the server. Please try again.")
    	                                           )
    	                                          )
    	                                   )
    	                            )
    	                            Ok(toJson(retMap))
    	        }	        
            }
              case None => {
                  var retMap = Map("files" -> 
						        Seq(
						            toJson(
						                Map(
						                    "name" -> toJson("Dataset ID Invalid."),
						                    "size" -> toJson(0),
						                    "error" -> toJson("Dataset with the specified ID was not found. Please try again.")
						                )
						            )
						        )
						     )
						     Ok(toJson(retMap))
              }
          }
        }
        case None => Redirect(routes.Datasets.list()).flashing("error" -> "You are not authorized to create new datasets.")
      }
  }
	

  def metadataSearch() = SecuredAction(authorization = WithPermission(Permission.SearchDatasets)) {
    implicit request =>
      implicit val user = request.user
      Ok(views.html.metadataSearch())
  }

  def generalMetadataSearch() = SecuredAction(authorization = WithPermission(Permission.SearchDatasets)) {
    implicit request =>
      implicit val user = request.user
      Ok(views.html.generalMetadataSearch())
  }
}

