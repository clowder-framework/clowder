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

/**
 * A dataset is a collection of files and streams.
 * 
 * @author Luigi Marini
 *
 */
object Datasets extends Controller with securesocial.core.SecureSocial {
   
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
   
  def newDataset() = Action {
    Ok(views.html.newDataset(datasetForm))
  }
  
  def createDataset() = Action { implicit request =>
    datasetForm.bindFromRequest.fold(
        failure => BadRequest("Oops"),
        {case dataset => {
          Dataset.save(dataset)
          Redirect(routes.Datasets.dataset(dataset.id.toString))   
          }
        }
    )
  }
   
  /**
   * List datasets.
   */
  def list() = Action {
    Services.files.listFiles().map(f => Logger.debug(f.toString))
    Ok(views.html.datasetList(Services.datasets.listDatasets()))
  }
  
  /**
   * Dataset.
   */
  def dataset(id: String) = Action {
    Previewers.searchFileSystem.foreach(p => Logger.info("Previewer found " + p.id))
    Services.datasets.get(id)  match {
      case Some(dataset) => Ok(views.html.dataset(dataset, Previewers.searchFileSystem))
      case None => {Logger.error("Error getting dataset" + id); InternalServerError}
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
    
    val dataset : Option[Dataset] = datasetForm.bindFromRequest.fold(
        failure => None,
        dataset => Some(dataset)
    )
    
      request.body.file("file").map { f =>
        dataset.map { dataset =>
	        Logger.info("Uploading file " + f.filename)
	        // store file
		    val id = Services.files.save(new FileInputStream(f.ref.file), f.filename, None)
		    val file = Services.files.getFile(id)
		    Logger.debug("Uploaded file id is " + id)
		    file match {
		      case Some(x) => {
		    	// TODO RK need to replace unknown with the server name
		    	val key = "unknown." + "file."+ x.contentType.replace("/", ".")
                // TODO RK : need figure out if we can use https
                val host = "http://" + request.host + request.path.replaceAll("upload$", "")
		        current.plugin[RabbitmqPlugin].foreach{_.extract(ExtractorMessage(id, host, key))}
		        current.plugin[ElasticsearchPlugin].foreach{_.index("files", "file", id, List(("filename",x.filename), ("contentType", x.contentType)))}

	            // add file to dataset
		        val dt = dataset.copy(files = List(x))
		        // TODO create a service instead of calling salat directly
	            Dataset.save(dt)
		    	// TODO RK need to replace unknown with the server name and dataset type
		    	val dtkey = "unknown." + "dataset."+ "unknown"
		        current.plugin[RabbitmqPlugin].foreach{_.extract(ExtractorMessage(id, host, dtkey))}
	            // redirect to file page
	            Redirect(routes.Datasets.dataset(dt.id.toString))
		      }
		      
		      case None => {
		        Logger.error("Could not retrieve file that was just saved.")
		        // TODO create a service instead of calling salat directly
	            Dataset.save(dataset)
	            // redirect to file page
	            Redirect(routes.Datasets.dataset(dataset.id.toString))
		      }
		    }
	        
        }.getOrElse{
          BadRequest("Form binding error")
        }
      }.getOrElse {
         BadRequest("File not attached")
      }
  }
}
