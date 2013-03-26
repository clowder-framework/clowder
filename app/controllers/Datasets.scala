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
import models.Dataset
import views.html.defaultpages.error
import java.io.FileInputStream
import play.api.Play.current
import services.RabbitmqPlugin
import services.ElasticsearchPlugin
import java.io.File
import org.bson.types.ObjectId
import models.FileDAO
import java.util.Date
import services.ExtractorMessage
import securesocial.core.SecureSocial
import java.text.SimpleDateFormat
import views.html.defaultpages.badRequest
import com.mongodb.casbah.commons.MongoDBObject
import models.SectionDAO
import play.api.mvc.Flash

/**
 * A dataset is a collection of files and streams.
 * 
 * @author Luigi Marini
 *
 */
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
   
  def newDataset() = SecuredAction { implicit request =>
  	Ok(views.html.newDataset(datasetForm)).flashing("error"->"Please select a file")
  }
   
  /**
   * List datasets.
   */
  def list(when: String, date: String, limit: Int) = Action {
    var direction = "b"
    if (when != "") direction = when
    val formatter = new SimpleDateFormat("yyyy-MM-dd'T'hh:mm:ss")
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
  def dataset(id: String) = Action {
    Previewers.searchFileSystem.foreach(p => Logger.info("Previewer found " + p.id))
    Services.datasets.get(id)  match {
      case Some(dataset) => {
        val files = dataset.files map { f =>
          FileDAO.get(f.id.toString).get
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
        Ok(views.html.dataset(datasetWithFiles, previews))
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
  def submit() = Action(parse.multipartFormData) { implicit request =>
    
        datasetForm.bindFromRequest.fold(
          errors => BadRequest(views.html.newDataset(errors)),
	      dataset => {
	           request.body.file("file").map { f =>
		        Logger.debug("Uploading file " + f.filename)
		        
		        // store file		        
			    val file = Services.files.save(new FileInputStream(f.ref.file), f.filename, f.contentType)
			    Logger.debug("Uploaded file id is " + file.get.id)
			    Logger.debug("Uploaded file type is " + f.contentType)
			    file match {
			      case Some(f) => {
			    	// TODO RK need to replace unknown with the server name
			    	val key = "unknown." + "file."+ f.contentType.replace("/", ".")
//			        val key = "unknown." + "file."+ "application.x-ptm"
	                // TODO RK : need figure out if we can use https
	                val host = "http://" + request.host + request.path.replaceAll("dataset/submit$", "")
	                val id = f.id.toString
			        current.plugin[RabbitmqPlugin].foreach{_.extract(ExtractorMessage(id, host, key, Map.empty))}
			        current.plugin[ElasticsearchPlugin].foreach{_.index("files", "file", id, List(("filename",f.filename), ("contentType", f.contentType)))}
	
		            // add file to dataset
			        val dt = dataset.copy(files = List(f))
			        // TODO create a service instead of calling salat directly
		            Dataset.save(dt)
			    	// TODO RK need to replace unknown with the server name and dataset type
//			    	val dtkey = "unknown." + "dataset."+ "unknown"
		            val dtkey = "unknown." + "dataset."+ "ARC3D"
			        current.plugin[RabbitmqPlugin].foreach{_.extract(ExtractorMessage(id, host, dtkey, Map.empty))}
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
}
