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
    ((name, description) => Dataset(name = name, description = description))
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
          Ok("Successful")
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
}