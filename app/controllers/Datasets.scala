/**
 *
 */
package controllers

import play.api.mvc.Controller
import play.api.Logger
import play.api.mvc.Action
import play.api.data.Form
import play.api.data.Forms._
import views.html.defaultpages.error
import java.io.FileInputStream
import play.api.Play.current
import services._
import java.io.File
import org.bson.types.ObjectId
import java.util.Date
import java.util.TimeZone
import securesocial.core.SecureSocial
import java.text.SimpleDateFormat
import views.html.defaultpages.badRequest
import com.mongodb.casbah.commons.MongoDBObject
import models.SectionDAO
import play.api.mvc.Flash
import scala.collection.immutable.Nil
import models._
import fileutils.FilesUtils
import api.WithPermission
import api.Permission

import javax.inject.{Singleton, Inject}
import scala.Some
import services.ExtractorMessage
import api.WithPermission


/**
 * A dataset is a collection of files and streams.
 * 
 * @author Luigi Marini
 *
 */
class Datasets @Inject() (datasets: DatasetService, files: FileService, collections: CollectionService) extends SecuredController {

object ActivityFound extends Exception { }
   
  /**
   * New dataset form.
   */
  val datasetForm = Form(
    mapping(
      "name" -> nonEmptyText,
      "description" -> nonEmptyText
    )
    ((name, description) => Dataset(name = name, description = description, created = new Date, author=null))
    ((dataset: Dataset) => Some((dataset.name, dataset.description)))
   )
   
  def newDataset()  = SecuredAction(authorization=WithPermission(Permission.CreateDatasets)) { implicit request =>
    implicit val user = request.user
    val filesList = for(file <- files.listFiles.sortBy(_.filename)) yield (file.id.toString(), file.filename)
  	Ok(views.html.newDataset(datasetForm, filesList)).flashing("error"->"Please select ONE file (upload new or existing)") 
  }
   
  /**
   * List datasets.
   */
  def list(when: String, date: String, limit: Int) = SecuredAction(authorization=WithPermission(Permission.ListDatasets)) { implicit request =>
    implicit val user = request.user
    var direction = "b"
    if (when != "") direction = when
    val formatter = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss")
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
    val latest = Dataset.find(MongoDBObject()).sort(MongoDBObject("created" -> -1)).limit(1).toList
    // first object
    val first = Dataset.find(MongoDBObject()).sort(MongoDBObject("created" -> 1)).limit(1).toList
    var firstPage = false
    var lastPage = false
    if (latest.size == 1) {
    	firstPage = datasetList.exists(_.id == latest(0).id)
    	lastPage = datasetList.exists(_.id == first(0).id)
    	Logger.debug("latest " + latest(0).id + " first page " + firstPage )
    	Logger.debug("first " + first(0).id + " last page " + lastPage )
    }
    if (datasetList.size > 0) {  
      if (date != "" && !firstPage) { // show prev button
    	prev = formatter.format(datasetList.head.created)
      }
      if (!lastPage) { // show next button
    	next = formatter.format(datasetList.last.created)
      }
    }
    Ok(views.html.datasetList(datasetList, prev, next, limit))
  }
  
 
 
  /**
   * Dataset.
   */
  def dataset(id: String) = SecuredAction(authorization=WithPermission(Permission.ShowDataset)) { implicit request =>
    implicit val user = request.user    
    Previewers.findPreviewers.foreach(p => Logger.info("Previewer found " + p.id))
    datasets.get(id)  match {
      case Some(dataset) => {
        val files = dataset.files map { f =>{
        		FileDAO.get(f.id.toString).get
        	}
        }
        
        //Search whether dataset is currently being processed by extractor(s)
        var isActivity = false
        try{
        	for(f <- files){
        		Extraction.findIfBeingProcessed(f.id) match {
        			case false => 
        			case true => isActivity = true; throw ActivityFound  
        		}
        	}
        }catch{
          case ActivityFound =>
        }
        
        
        val datasetWithFiles = dataset.copy(files = files)
        val previewers = Previewers.findPreviewers
        val previewslist = for(f <- datasetWithFiles.files) yield {
          val pvf = for(p <- previewers ; pv <- f.previews; if (f.showPreviews.equals("DatasetLevel")) && (p.contentType.contains(pv.contentType))) yield { 
            (pv.id.toString, p.id, p.path, p.main, api.routes.Previews.download(pv.id.toString).toString, pv.contentType, pv.length)
          }        
          if (pvf.length > 0) {
            (f -> pvf)
          } else {
  	        val ff = for(p <- previewers ; if (f.showPreviews.equals("DatasetLevel")) && (p.contentType.contains(f.contentType))) yield {
  	          (f.id.toString, p.id, p.path, p.main, routes.Files.download(f.id.toString).toString, f.contentType, f.length)
  	        }
  	        (f -> ff)
          }
        }
        val previews = Map(previewslist:_*)
        val metadata = Dataset.getMetadata(id)
        Logger.debug("Metadata: " + metadata)
        for (md <- metadata) {
          Logger.debug(md.toString)
        }       
        val userMetadata = Dataset.getUserMetadata(id)
        Logger.debug("User metadata: " + userMetadata.toString)
        
        val collectionsOutside = Collection.listOutsideDataset(id).sortBy(_.name)
        val collectionsInside = Collection.listInsideDataset(id).sortBy(_.name)
        val filesOutside = FileDAO.listOutsideDataset(id).sortBy(_.filename)
        
        var comments = Comment.findCommentsByDatasetId(id)
        files.map { file =>
          comments ++= Comment.findCommentsByFileId(file.id.toString())
          SectionDAO.findByFileId(file.id).map { section =>
            comments ++= Comment.findCommentsBySectionId(section.id.toString())
          } 
        }
        comments = comments.sortBy(_.posted)
        
        val isRDFExportEnabled = play.Play.application().configuration().getString("rdfexporter").equals("on")
        
        Ok(views.html.dataset(datasetWithFiles, comments, previews, metadata, userMetadata, isActivity, collectionsOutside, collectionsInside, filesOutside, isRDFExportEnabled))
      }
      case None => {Logger.error("Error getting dataset" + id); InternalServerError}
    }
  }
  
  /**
   * Dataset by section.
   */
  def datasetBySection(section_id: String) = SecuredAction(authorization=WithPermission(Permission.ShowDataset)) { request =>
    SectionDAO.findOneById(new ObjectId(section_id)) match {
      case Some(section) => {
        Dataset.findOneByFileId(section.file_id) match {
          case Some(dataset) => Redirect(routes.Datasets.dataset(dataset.id.toString))
          case None => InternalServerError("Dataset not found")
        }   
      }
      case None =>  InternalServerError("Section not found")
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
					        					        
					        val id = f.id.toString	                	                
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
					            	FileDAO.renameFile(f.id.toString, nameOfFile)
					              }
					        	  FileDAO.setContentType(f.id.toString, fileType)
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
						        current.plugin[RabbitmqPlugin].foreach{_.extract(ExtractorMessage(id, id, host, key, Map.empty, f.length.toString, "", flags))}
						        //current.plugin[ElasticsearchPlugin].foreach{_.index("data", "file", id, List(("filename",nameOfFile), ("contentType", f.contentType)))}
					        }
					        
					        // add file to dataset 
					        val dt = dataset.copy(files = List(f), author=identity)
					        // TODO create a service instead of calling salat directly
				            Dataset.save(dt)
				            
				            if(fileType.equals("multi/files-zipped")){
						        current.plugin[RabbitmqPlugin].foreach{_.extract(ExtractorMessage(id, id, host, key, Map.empty, f.length.toString, dt.id.toString, flags))}
						        //current.plugin[ElasticsearchPlugin].foreach{_.index("data", "file", id, List(("filename",nameOfFile), ("contentType", f.contentType)))}
					        }
					        
					        //for metadata files
							  if(fileType.equals("application/xml") || fileType.equals("text/xml")){
								  		  val xmlToJSON = FilesUtils.readXMLgetJSON(uploadedFile.ref.file)
										  FileDAO.addXMLMetadata(f.id.toString, xmlToJSON)
										  Dataset.addXMLMetadata(dt.id.toString, f.id.toString, xmlToJSON)
		
										  Logger.debug("xmlmd=" + xmlToJSON)
										  
										  //index the file
										  current.plugin[ElasticsearchPlugin].foreach{
								  			  _.index("data", "file", id, List(("filename",f.filename), ("contentType", fileType),("datasetId",dt.id.toString()),("datasetName",dt.name), ("xmlmetadata", xmlToJSON)))
								  		  }
								  		  // index dataset
								  		  current.plugin[ElasticsearchPlugin].foreach{_.index("data", "dataset", dt.id.toString, 
								  		  List(("name",dt.name), ("description", dt.description), ("xmlmetadata", xmlToJSON)))}
							  }
							  else{
								  //index the file
								  current.plugin[ElasticsearchPlugin].foreach{_.index("data", "file", id, List(("filename",f.filename), ("contentType", fileType),("datasetId",dt.id.toString),("datasetName",dt.name)))}
								  // index dataset
								  current.plugin[ElasticsearchPlugin].foreach{_.index("data", "dataset", dt.id.toString, 
								  List(("name",dt.name), ("description", dt.description)))}
							  }

					    	// TODO RK need to replace unknown with the server name and dataset type		            
		 			    	val dtkey = "unknown." + "dataset."+ "unknown"
					        current.plugin[RabbitmqPlugin].foreach{_.extract(ExtractorMessage(dt.id.toString, dt.id.toString, host, dtkey, Map.empty, "0", dt.id.toString, ""))}
		 			    	
		 			    	//add file to RDF triple store if triple store is used
		 			    	if(fileType.equals("application/xml") || fileType.equals("text/xml")){
					             play.api.Play.configuration.getString("userdfSPARQLStore").getOrElse("no") match{      
						             case "yes" => {
						               services.Services.rdfSPARQLService.addFileToGraph(f.id.toString)
						               services.Services.rdfSPARQLService.linkFileToDataset(f.id.toString, dt.id.toString)
						             }
						             case _ => {}
					             }
				             }
		 			    	
		 			    	var extractJobId=current.plugin[VersusPlugin].foreach{_.extract(id)} 
		 			    	Logger.debug("Inside File: Extraction Id : "+ extractJobId)
		 			    	
				            // redirect to dataset page
				            Redirect(routes.Datasets.dataset(dt.id.toString))
		//		            Ok(views.html.dataset(dt, Previewers.searchFileSystem))
					      }
					      
					      case None => {
					        Logger.error("Could not retrieve file that was just saved.")
					        // TODO create a service instead of calling salat directly
					        val dt = dataset.copy(author=identity)
				            Dataset.save(dt)
				            // redirect to dataset page
				            Redirect(routes.Datasets.dataset(dt.id.toString))
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
		          val theFile = files.get(fileId)
		          if(theFile.isEmpty)
		            Redirect(routes.Datasets.newDataset()).flashing("error"->"Selected file not found. Maybe it was removed.")		            
		          val theFileGet = theFile.get  
		          
				  val dt = dataset.copy(files = List(theFileGet), author=identity)
				  // TODO create a service instead of calling salat directly
			      Dataset.save(dt)
			      
		          if(!theFileGet.xmlMetadata.isEmpty){
		            val xmlToJSON = FileDAO.getXMLMetadataJSON(fileId)
		            Dataset.addXMLMetadata(dt.id.toString, fileId, xmlToJSON)
		            // index dataset
		            current.plugin[ElasticsearchPlugin].foreach{_.index("data", "dataset", dt.id.toString, 
			        List(("name",dt.name), ("description", dt.description),  ("xmlmetadata", xmlToJSON)))}
		          }else{
		            // index dataset
		        	  current.plugin[ElasticsearchPlugin].foreach{_.index("data", "dataset", dt.id.toString, 
			    	   List(("name",dt.name), ("description", dt.description)))}
		          }
		          
		          //reindex file
		          files.index(theFileGet.id.toString())
		          
		          // TODO RK : need figure out if we can use https
		          val host = "http://" + request.host + request.path.replaceAll("dataset/submit$", "")
				  // TODO RK need to replace unknown with the server name and dataset type		            
				  val dtkey = "unknown." + "dataset."+ "unknown"
						  current.plugin[RabbitmqPlugin].foreach{_.extract(ExtractorMessage(dt.id.toString, dt.id.toString, host, dtkey, Map.empty, "0", dt.id.toString, ""))}
		          
		          //link file to dataset in RDF triple store if triple store is used
		          if(theFileGet.filename.endsWith(".xml")){
				             play.api.Play.configuration.getString("userdfSPARQLStore").getOrElse("no") match{      
					             case "yes" => {
					               services.Services.rdfSPARQLService.linkFileToDataset(fileId, dt.id.toString)
					             }
					             case _ => {}
				             }
				   }
		          
				  // redirect to dataset page
				  Redirect(routes.Datasets.dataset(dt.id.toString)) 
	            }	            
	          }  
	        }
		  }
		)
      }
      case None => Redirect(routes.Datasets.list()).flashing("error" -> "You are not authorized to create new datasets.")
    }
  }
  
  def metadataSearch()  = SecuredAction(authorization=WithPermission(Permission.SearchDatasets)) { implicit request =>
    implicit val user = request.user
  	Ok(views.html.metadataSearch()) 
  }
  def generalMetadataSearch()  = SecuredAction(authorization=WithPermission(Permission.SearchDatasets)) { implicit request =>
    implicit val user = request.user
  	Ok(views.html.generalMetadataSearch()) 
  }
  
  
  
}
