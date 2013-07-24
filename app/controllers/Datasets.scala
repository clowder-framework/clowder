/**
 *
 */
package controllers

import play.api.mvc.Controller
import services.Services
import play.api.Logger
import play.api.mvc.Action
import play.api.data.Form
import play.api.data.Forms._
import views.html.defaultpages.error
import java.io.FileInputStream
import play.api.Play.current
import services.RabbitmqPlugin
import services.ElasticsearchPlugin
import java.io.File
import org.bson.types.ObjectId
import java.util.Date
import java.util.TimeZone
import services.ExtractorMessage
import securesocial.core.SecureSocial
import java.text.SimpleDateFormat
import views.html.defaultpages.badRequest
import com.mongodb.casbah.commons.MongoDBObject
import models.SectionDAO
import play.api.mvc.Flash
import scala.collection.immutable.Nil
import models._
import fileutils.FilesUtils

/**
 * A dataset is a collection of files and streams.
 * 
 * @author Luigi Marini
 *
 */

object ActivityFound extends Exception { }

object Datasets extends Controller with SecureSocial {
   
  /**
   * New dataset form.
   */
  val datasetForm = Form(
    mapping(
      "name" -> nonEmptyText,
      "description" -> nonEmptyText
    )
    ((name, description) => Dataset(name = name, description = description, created = new Date))
    ((dataset: Dataset) => Some((dataset.name, dataset.description)))
   )
   
   //Secured
  def newDataset()  = UserAwareAction { implicit request =>
    implicit val user = request.user
  	Ok(views.html.newDataset(datasetForm)).flashing("error"->"Please select a file") 
  }
   
  /**
   * List datasets.
   */
  def list(when: String, date: String, limit: Int) = UserAwareAction { implicit request =>
    implicit val user = request.user
    var direction = "b"
    if (when != "") direction = when
    val formatter = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss")
    var prev, next = ""
    var datasets = List.empty[models.Dataset]
    if (direction == "b") {
	    datasets = Services.datasets.listDatasetsBefore(date, limit)
    } else if (direction == "a") {
    	datasets = Services.datasets.listDatasetsAfter(date, limit)
    } else {
      badRequest
    }
    // latest object
    val latest = Dataset.find(MongoDBObject()).sort(MongoDBObject("created" -> -1)).limit(1).toList
    var firstPage = false
    if (latest.size == 1) {
    	firstPage = datasets.exists(_.id == latest(0).id)
    	Logger.debug("latest " + latest(0).id + " first page " + firstPage )
    }
    if (datasets.size > 0) {  
      if (date != "" && !firstPage) { // show prev button
    	prev = formatter.format(datasets.head.created)
      }
      if (datasets.size == limit) { // show next button
    	next = formatter.format(datasets.last.created)
      }
    }
    Ok(views.html.datasetList(datasets, prev, next, limit))
  }
  
 
 
  /**
   * Dataset.
   */
  def dataset(id: String) = UserAwareAction { implicit request =>
    implicit val user = request.user    
    Previewers.searchFileSystem.foreach(p => Logger.info("Previewer found " + p.id))
    Services.datasets.get(id)  match {
      case Some(dataset) => {
        val files = dataset.files map { f =>
          FileDAO.get(f.id.toString).get
        }
        
        //Search whether dataset is currently being processed by extractor(s)
        var isActivity = false
        try{
        	for(f <- files){
        		Extraction.findMostRecentByFileId(f.id) match{
        		case Some(mostRecent) => {
        			mostRecent.status match{
        			case "DONE." => 
        			case _ => { 
        				isActivity = true
        				throw ActivityFound
        			  }  
        			}
        		}
        		case None =>       
        		}
        	}
        }catch{
          case ActivityFound =>
        }
        
        
        val datasetWithFiles = dataset.copy(files = files)
        val previewers = Previewers.searchFileSystem
        val previewslist = for(f <- datasetWithFiles.files) yield {
          val pvf = for(p <- previewers ; pv <- f.previews; if (p.contentType.contains(pv.contentType))) yield {
            (pv.id.toString, p.id, p.path, p.main, api.routes.Previews.download(pv.id.toString).toString, pv.contentType, pv.length)
          }        
          if (pvf.length > 0) {
            (f -> pvf)
          } else {
  	        val ff = for(p <- previewers ; if (p.contentType.contains(f.contentType))) yield {
  	          (f.id.toString, p.id, p.path, p.main, routes.Files.file(f.id.toString) + "/blob", f.contentType, f.length)
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
        
        Ok(views.html.dataset(datasetWithFiles, previews, metadata, userMetadata, isActivity))
      }
      case None => {Logger.error("Error getting dataset" + id); InternalServerError}
    }
  }
  
  /**
   * Dataset by section.
   */
  def datasetBySection(section_id: String) = Action {
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
  
  def upload = Action(parse.temporaryFile) { request =>
    request.body.moveTo(new File("/tmp/picture"))
    Ok("File uploaded")
  }
  
  /**
   * Upload file.
   */
  def submit() = UserAwareAction(parse.multipartFormData) { implicit request =>
    implicit val user = request.user
    
        datasetForm.bindFromRequest.fold(
          errors => BadRequest(views.html.newDataset(errors)),
	      dataset => {
	           request.body.file("file").map { f =>
		        Logger.debug("Uploading file " + f.filename)
		        
		        // store file		        
			    val file = Services.files.save(new FileInputStream(f.ref.file), f.filename, f.contentType)
			    Logger.debug("Uploaded file id is " + file.get.id)
			    Logger.debug("Uploaded file type is " + f.contentType)
			    
			    val uploadedFile = f
			    file match {
			      case Some(f) => {			        
			        var fileType = f.contentType
			        if(fileType.contains("/zip") || fileType.contains("/x-zip") || f.filename.endsWith(".zip")){
			          fileType = FilesUtils.getMainFileTypeOfZipFile(uploadedFile.ref.file)			          
			          if(fileType.startsWith("ERROR: ")){
			             Logger.error(fileType.substring(7))
			             InternalServerError(fileType.substring(7))
			          }			          
			        }			        			        
			    	// TODO RK need to replace unknown with the server name
			    	val key = "unknown." + "file."+ fileType.replace(".", "_").replace("/", ".")
//			        val key = "unknown." + "file."+ "application.x-ptm"

	                // TODO RK : need figure out if we can use https
	                val host = "http://" + request.host + request.path.replaceAll("dataset/submit$", "")
	                val id = f.id.toString
			        current.plugin[RabbitmqPlugin].foreach{_.extract(ExtractorMessage(id, id, host, key, Map.empty, f.length.toString, ""))}
			        current.plugin[ElasticsearchPlugin].foreach{_.index("data", "file", id, List(("filename",f.filename), ("contentType", f.contentType)))}
	
		            // add file to dataset
			        val dt = dataset.copy(files = List(f))
			        // TODO create a service instead of calling salat directly
		            Dataset.save(dt)
		            
		            // index dataset
		            current.plugin[ElasticsearchPlugin].foreach{_.index("data", "dataset", id, 
		                List(("name",dt.name), ("description", dt.description)))}
           
		            
			    	// TODO RK need to replace unknown with the server name and dataset type		            
 			    	val dtkey = "unknown." + "dataset."+ "unknown"
			        current.plugin[RabbitmqPlugin].foreach{_.extract(ExtractorMessage(dt.id.toString, dt.id.toString, host, dtkey, Map.empty, "0", ""))}
		            // redirect to file page
		            Redirect(routes.Datasets.dataset(dt.id.toString))
//		            Ok(views.html.dataset(dt, Previewers.searchFileSystem))
			      }
			      
			      case None => {
			        Logger.error("Could not retrieve file that was just saved.")
			        // TODO create a service instead of calling salat directly
		            Dataset.save(dataset)
		            // redirect to file page
		            Redirect(routes.Datasets.dataset(dataset.id.toString))
//		            Ok(views.html.dataset(dataset, Previewers.searchFileSystem))
			      }
			    }   
        }.getOrElse{
          Redirect(routes.Datasets.newDataset()).flashing("error"->"Please select a file")
        }
	    }
	)
  }

  /*
   * Add comment to a dataset
   */
  def comment(id: String) = SecuredAction(ajaxCall = true, None, parse.json) { implicit request =>
    val text = request.body.\("comment").asOpt[String].getOrElse("")
    if (text == "") {
      BadRequest("error, no comment supplied.")
    }
    val preview = request.body.\("preview").asOpt[String] match {
      case Some(id) => PreviewDAO.findOneById(new ObjectId(id))
      case None => None
    }
    val comment = Comment(request.user.id.id, new Date(), text)
    request.body.\("fileid").asOpt[String].map { fileid =>
      val x = request.body.\("x").asOpt[Double].getOrElse(-1.0)
      val y = request.body.\("y").asOpt[Double].getOrElse(-1.0)
      val w = request.body.\("w").asOpt[Double].getOrElse(-1.0)
      val h = request.body.\("h").asOpt[Double].getOrElse(-1.0)
      if ((x < 0) || (y < 0) || (w < 0) || (h < 0)) {
        FileDAO.comment(fileid, comment)
      } else {
        val section = new Section(area=Some(new Rectangle(x, y, w, h)), file_id=new ObjectId(fileid), comments=List(comment), preview=preview);
        SectionDAO.save(section)
      }    
    }.getOrElse {
      Dataset.comment(id, comment)      
    }
    Ok("")
  }

  def tag(id: String) = SecuredAction(ajaxCall = true, None, parse.json) { implicit request =>
    val text = request.body.\("text").asOpt[String].getOrElse("")
    if (text == "") {
      BadRequest("error, no tag supplied.")
    }
    Logger.debug(request.body.\("preview").toString)
    val preview = request.body.\("preview").asOpt[String] match {
      case Some(id) => PreviewDAO.findOneById(new ObjectId(id))
      case None => None
    }
    Logger.debug(preview.toString())
    request.body.\("fileid").asOpt[String].map { fileid =>
      val x = request.body.\("x").asOpt[Double].getOrElse(-1.0)
      val y = request.body.\("y").asOpt[Double].getOrElse(-1.0)
      val w = request.body.\("w").asOpt[Double].getOrElse(-1.0)
      val h = request.body.\("h").asOpt[Double].getOrElse(-1.0)
      if ((x < 0) || (y < 0) || (w < 0) || (h < 0)) {
        FileDAO.tag(fileid, text)
      } else {
        val section = new Section(area=Some(new Rectangle(x, y, w, h)), file_id=new ObjectId(fileid), tags=List(text), preview=preview);
        SectionDAO.save(section)
      }    
    }.getOrElse {
      Dataset.tag(id, text)      
    }
    Ok("")
  }
  
  def metadataSearch()  = UserAwareAction { implicit request =>
    implicit val user = request.user
  	Ok(views.html.metadataSearch()) 
  }
  
  
  
}
