package controllers

import play.api.Logger
import play.api.data.Form
import play.api.data.Forms._
import java.io.FileInputStream
import play.api.Play.current
import services._
import java.util.Date
import java.text.SimpleDateFormat
import views.html.defaultpages.badRequest
import models._
import fileutils.FilesUtils
import api.Permission
import javax.inject.Inject
import scala.Some
import services.ExtractorMessage
import api.WithPermission


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
  sparql: RdfSPARQLService) extends SecuredController {

  object ActivityFound extends Exception {}

  /**
   * New dataset form.
   */
  val datasetForm = Form(
    mapping(
      "name" -> nonEmptyText,
      "description" -> nonEmptyText
    )
      ((name, description) => Dataset(name = name, description = description, created = new Date, author = null))
      ((dataset: Dataset) => Some((dataset.name, dataset.description)))
  )

  def newDataset() = SecuredAction(authorization = WithPermission(Permission.CreateDatasets)) {
    implicit request =>
      implicit val user = request.user
      val filesList = for (file <- files.listFiles.sortBy(_.filename)) yield (file.id.toString(), file.filename)
      Ok(views.html.newDataset(datasetForm, filesList)).flashing("error" -> "Please select ONE file (upload new or existing)")
  }

  /**
   * List datasets.
   */
  def list(when: String, date: String, limit: Int) = SecuredAction(authorization = WithPermission(Permission.ListDatasets)) {
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
	      Ok(views.html.datasetList(datasetList, prev, next, limit))
  }


  /**
   * Dataset.
   */
  def dataset(id: UUID) = SecuredAction(authorization = WithPermission(Permission.ShowDataset)) {
    implicit request =>
      implicit val user = request.user
      Previewers.findPreviewers.foreach(p => Logger.info("Previewer found " + p.id))
      datasets.get(id) match {
        case Some(dataset) => {
          val filesInDataset = dataset.files.map(f => files.get(f.id).get)

          //Search whether dataset is currently being processed by extractor(s)
          var isActivity = false
          try {
            for (f <- filesInDataset) {
              extractions.findIfBeingProcessed(f.id) match {
                case false =>
                case true => isActivity = true; throw ActivityFound
              }
            }
          } catch {
            case ActivityFound =>
          }


          val datasetWithFiles = dataset.copy(files = filesInDataset)
          val previewers = Previewers.findPreviewers
          val previewslist = for (f <- datasetWithFiles.files) yield {
            val pvf = for (p <- previewers; pv <- f.previews; if (f.showPreviews.equals("DatasetLevel")) && (p.contentType.contains(pv.contentType))) yield {
              (pv.id.toString, p.id, p.path, p.main, api.routes.Previews.download(pv.id).toString, pv.contentType, pv.length)
            }
            if (pvf.length > 0) {
              (f -> pvf)
            } else {
              val ff = for (p <- previewers; if (f.showPreviews.equals("DatasetLevel")) && (p.contentType.contains(f.contentType))) yield {
                (f.id.toString, p.id, p.path, p.main, routes.Files.download(f.id).toString, f.contentType, f.length)
              }
              (f -> ff)
            }
          }
          val previews = Map(previewslist: _*)
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

          val isRDFExportEnabled = play.Play.application().configuration().getString("rdfexporter").equals("on")

          Ok(views.html.dataset(datasetWithFiles, commentsByDataset, previews, metadata, userMetadata, isActivity, collectionsOutside, collectionsInside, filesOutside, isRDFExportEnabled))
        }
        case None => {
          Logger.error("Error getting dataset" + id); InternalServerError
        }
      }
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
   * Upload file.
   */
def submit() = SecuredAction(parse.multipartFormData, authorization=WithPermission(Permission.CreateDatasets)) { implicit request =>

    implicit val user = request.user
    
    user match {
      case Some(identity) => {
        datasetForm.bindFromRequest.fold(
          errors => BadRequest(views.html.newDataset(errors, for(file <- files.listFiles.sortBy(_.filename)) yield (file.id.toString(), file.filename))),
	      dataset => {
	           request.body.file("file").map { f =>
	             //Uploaded file selected
	             
	             //Can't have both an uploaded file and a selected existing file
	             request.body.asFormUrlEncoded.get("existingFile").get(0).equals("__nofile") match{
	               case true => {
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
					          if(fileType.equals("imageset/ptmimages-zipped") || fileType.equals("imageset/ptmimages+zipped") ){
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
					        else if(nameOfFile.toLowerCase().endsWith(".mov")){
							  fileType = "ambiguous/mov";
					        }
					        
					        current.plugin[FileDumpService].foreach{_.dump(DumpOfFile(uploadedFile.ref.file, f.id.toString, nameOfFile))}
					        
					    	// TODO RK need to replace unknown with the server name
					    	val key = "unknown." + "file."+ fileType.replace(".", "_").replace("/", ".")
		//			        val key = "unknown." + "file."+ "application.x-ptm"
					    	
			                // TODO RK : need figure out if we can use https
			                val host = "http://" + request.host + request.path.replaceAll("dataset/submit$", "")
		      
			                //If uploaded file contains zipped files to be unzipped and added to the dataset, wait until the dataset is saved before sending extractor messages to unzip
			                //and return the files
			                if(!fileType.equals("multi/files-zipped")){
						        current.plugin[RabbitmqPlugin].foreach{_.extract(ExtractorMessage(id, id, host, key, Map.empty, f.length.toString, null, flags))}
						        //current.plugin[ElasticsearchPlugin].foreach{_.index("data", "file", id, List(("filename",nameOfFile), ("contentType", f.contentType)))}
					        }
					        
					        // add file to dataset 
					        val dt = dataset.copy(files = List(f), author=identity)					        
					        // TODO create a service instead of calling salat directly
				            datasets.update(dt)				            
				            
				            if(fileType.equals("multi/files-zipped")){
						        current.plugin[RabbitmqPlugin].foreach{_.extract(ExtractorMessage(id, id, host, key, Map.empty, f.length.toString, dt.id, flags))}
						        //current.plugin[ElasticsearchPlugin].foreach{_.index("data", "file", id, List(("filename",nameOfFile), ("contentType", f.contentType)))}
					        }
					        
					        val dateFormat = new SimpleDateFormat("dd/MM/yyyy")
					        
					        //for metadata files
							  if(fileType.equals("application/xml") || fileType.equals("text/xml")){
								  		  val xmlToJSON = FilesUtils.readXMLgetJSON(uploadedFile.ref.file)
										  files.addXMLMetadata(f.id, xmlToJSON)
										  datasets.addXMLMetadata(dt.id, f.id, xmlToJSON)
		
										  Logger.debug("xmlmd=" + xmlToJSON)
										  
										  //index the file
										  current.plugin[ElasticsearchPlugin].foreach{
								  			  _.index("data", "file", id, List(("filename",f.filename), ("contentType", fileType), ("author", identity.fullName), ("uploadDate", dateFormat.format(new Date())), ("datasetId",dt.id.toString()),("datasetName",dt.name), ("xmlmetadata", xmlToJSON)))

								  		  }
								  		  // index dataset
								  		  current.plugin[ElasticsearchPlugin].foreach{_.index("data", "dataset", dt.id, 
								  		  List(("name",dt.name), ("description", dt.description), ("author", identity.fullName), ("created", dateFormat.format(new Date())), ("fileId",f.id.toString),("fileName",f.filename), ("collId",""),("collName",""), ("xmlmetadata", xmlToJSON)))}
							  }
							  else{
								  //index the file

								  current.plugin[ElasticsearchPlugin].foreach{_.index("data", "file", id, List(("filename",f.filename), ("contentType", fileType), ("author", identity.fullName), ("uploadDate", dateFormat.format(new Date())), ("datasetId",dt.id.toString),("datasetName",dt.name)))}

								  // index dataset
								  current.plugin[ElasticsearchPlugin].foreach{_.index("data", "dataset", dt.id, 
								  List(("name",dt.name), ("description", dt.description), ("author", identity.fullName), ("created", dateFormat.format(new Date())), ("fileId",f.id.toString),("fileName",f.filename), ("collId",""),("collName","")))}
							  }
					    	// TODO RK need to replace unknown with the server name and dataset type		            
		 			    	val dtkey = "unknown." + "dataset."+ "unknown"
					        current.plugin[RabbitmqPlugin].foreach{_.extract(ExtractorMessage(dt.id, dt.id, host, dtkey, Map.empty, "0", dt.id, ""))}
		 			    	
		 			    	//add file to RDF triple store if triple store is used
		 			    	if(fileType.equals("application/xml") || fileType.equals("text/xml")){
					             play.api.Play.configuration.getString("userdfSPARQLStore").getOrElse("no") match{      
						             case "yes" => {
						               sparql.addFileToGraph(f.id)
						               sparql.linkFileToDataset(f.id, dt.id)
						             }
						             case _ => {}
					             }
				             }
		 			    	
		 			    	var extractJobId=current.plugin[VersusPlugin].foreach{_.extract(id)} 
		 			    	Logger.debug("Inside File: Extraction Id : "+ extractJobId)
		 			    	
				            // redirect to dataset page
				            Redirect(routes.Datasets.dataset(dt.id))
		//		            Ok(views.html.dataset(dt, Previewers.searchFileSystem))
					      }
					      
					      case None => {
					        Logger.error("Could not retrieve file that was just saved.")
					        // TODO create a service instead of calling salat directly
					        val dt = dataset.copy(author=identity)
				            datasets.update(dt) 
				            // redirect to dataset page
				            Redirect(routes.Datasets.dataset(dt.id))
				            current.plugin[AdminsNotifierPlugin].foreach{_.sendAdminsNotification("Dataset","added",dt.id.toString, dt.name)}
				            Redirect(routes.Datasets.dataset(dt.id))				            
		//		            Ok(views.html.dataset(dt, Previewers.searchFileSystem))
					      }
					    }   	                 
	                 }
	               case false => Redirect(routes.Datasets.newDataset()).flashing("error"->"Please select ONE file (upload new or existing)")	
	               }
	             
	           
	        }.getOrElse{
	          val fileId = request.body.asFormUrlEncoded.get("existingFile").get(0)
	          fileId match{
	            case "__nofile" => Redirect(routes.Datasets.newDataset()).flashing("error"->"Please select ONE file (upload new or existing)")
	            case _ => {
	              //Existing file selected	          
	          
		          // add file to dataset 
		          val theFile = files.get(UUID(fileId))
		          if(theFile.isEmpty)
		            Redirect(routes.Datasets.newDataset()).flashing("error"->"Selected file not found. Maybe it was removed.")		            
		          val theFileGet = theFile.get
		          
		          val thisFileThumbnail: Option[String] = theFileGet.thumbnail_id
		          var thisFileThumbnailString: Option[String] = None
		          if(!thisFileThumbnail.isEmpty)
		            thisFileThumbnailString = Some(thisFileThumbnail.get)
		          
				  val dt = dataset.copy(files = List(theFileGet), author=identity, thumbnail_id=thisFileThumbnailString)
				  datasets.update(dt)
			      
			      val dateFormat = new SimpleDateFormat("dd/MM/yyyy")

			      
		          if(!theFileGet.xmlMetadata.isEmpty){
		            val xmlToJSON = files.getXMLMetadataJSON(UUID(fileId))
		            datasets.addXMLMetadata(dt.id, UUID(fileId), xmlToJSON)
		            // index dataset
		            current.plugin[ElasticsearchPlugin].foreach{_.index("data", "dataset", dt.id, 
			        List(("name",dt.name), ("description", dt.description), ("author", identity.fullName), ("created", dateFormat.format(new Date())), ("fileId",theFileGet.id.toString),("fileName",theFileGet.filename), ("collId",""),("collName",""), ("xmlmetadata", xmlToJSON)))}
		          }else{
		            // index dataset
		        	  current.plugin[ElasticsearchPlugin].foreach{_.index("data", "dataset", dt.id, 
			    	   List(("name",dt.name), ("description", dt.description), ("author", identity.fullName), ("created", dateFormat.format(new Date())), ("fileId",theFileGet.id.toString),("fileName",theFileGet.filename), ("collId",""),("collName","")))}
		          }
		          
		          //reindex file
		          files.index(theFileGet.id)
		          
		          // TODO RK : need figure out if we can use https
		          val host = "http://" + request.host + request.path.replaceAll("dataset/submit$", "")
				  // TODO RK need to replace unknown with the server name and dataset type		            
				  val dtkey = "unknown." + "dataset."+ "unknown"
						  current.plugin[RabbitmqPlugin].foreach{_.extract(ExtractorMessage(dt.id, dt.id, host, dtkey, Map.empty, "0", dt.id, ""))}

		          //link file to dataset in RDF triple store if triple store is used
		          if(theFileGet.filename.endsWith(".xml")){
				             play.api.Play.configuration.getString("userdfSPARQLStore").getOrElse("no") match{      
					             case "yes" => {
					               sparql.linkFileToDataset(UUID(fileId), dt.id)
					             }
					             case _ => {}
				             }
				   }
		          
				  // redirect to dataset page
				  Redirect(routes.Datasets.dataset(dt.id))
				  current.plugin[AdminsNotifierPlugin].foreach{_.sendAdminsNotification("Dataset","added",dt.id.stringify, dt.name)}
				  Redirect(routes.Datasets.dataset(dt.id)) 				  
	            }	            
	          }  
	        }
		  }
		 )
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
