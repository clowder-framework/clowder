package api

import play.api.libs.iteratee.Enumerator
import play.api.mvc.Controller
import play.api.mvc.Action
import play.api.libs.json.Json._
import services.mongodb.SelectedDAO
import play.api.Logger
import javax.inject.Inject
import services.{SelectionService, DatasetService}
import models.UUID

import scala.concurrent.Future

/**
 * Selected items.
 * 
 * @author Luigi Marini
 *
 */
class Selected @Inject()(selections: SelectionService,
                         datasets: DatasetService) extends Controller with ApiController {

  def add() = AuthenticatedAction(parse.json) { implicit request =>
    Logger.debug("Requesting Selected.add" + request.body)
    request.body.\("dataset").asOpt[String] match {
	    case Some(dataset) => {
        request.user match {
          case Some(user) => {
            selections.add(UUID(dataset), user.email.get)
            Ok(toJson(Map("success"->"true")))
          }
          case None => Ok(toJson(Map("success"->"false", "msg"->"User not logged in")))
        }
	    }
	    case None => {
	    	Logger.error("no dataset specified")
	    	BadRequest
	    }
    }
  }
  
  def remove() = AuthenticatedAction(parse.json) { implicit request =>
    Logger.debug("Requesting Selected.remove" + request.body)
    request.body.\("dataset").asOpt[String] match {
	    case Some(dataset) => {
        request.user match {
          case Some(user) => {
            selections.remove(UUID(dataset), user.email.get)
            Ok(toJson(Map("success"->"true")))
          }
          case None => Ok(toJson(Map("success"->"false", "msg"->"User not logged in")))
        }
	    }
	    case None => {
	    	Logger.error("no dataset specified")
	    	BadRequest
	    }
    }
  }

  def deleteAll() = AuthenticatedAction { implicit request =>
    Logger.debug("Requesting Selected.deleteAll")
    request.user match {
      case Some(user) => {
        var selected = selections.get(user.toString)
        selected.map(d => {
          datasets.removeDataset(d.id)
        })
        Ok(toJson(Map("sucess"->"true")))
      }
    }
  }

  def downloadAll() = AuthenticatedAction { implicit request =>
    Logger.debug("Requesting Selected.downloadAll")
    request.user match {
      case Some(user) => {
        var selected = selections.get(user.toString)
        val bagit = play.api.Play.configuration.getBoolean("downloadDatasetBagit").getOrElse(true)
        selected.map(d => {
          datasets.removeDataset(d.id)
        })
        Ok(toJson(Map("sucess"->"true")))
      }
    }
  }

  def enumeratorFromSelected(): Enumerator[Array[Byte]] = {
    var is = current_iterator.next()

    Enumerator.generateM({
      is match {
        case Some(inputStream) => {
          if (current_iterator.isBagIt() && bytesSet == false){
            current_iterator.setBytes(totalBytes)
            bytesSet = true
          }
          val buffer = new Array[Byte](chunkSize)
          val bytesRead = scala.concurrent.blocking {
            inputStream.read(buffer)

          }
          val chunk = bytesRead match {
            case -1 => {
              zip.closeEntry()
              inputStream.close()
              Some(byteArrayOutputStream.toByteArray)
              if (current_iterator.hasNext()){
                is = current_iterator.next()
              } else{
                zip.close()
                is = None
              }
              Some(byteArrayOutputStream.toByteArray)
            }
            case read => {
              if (!current_iterator.isBagIt()){
                totalBytes += bytesRead
              }
              zip.write(buffer, 0, read)
              Some(byteArrayOutputStream.toByteArray)
            }
          }
          byteArrayOutputStream.reset()
          Future.successful(chunk)
        }
        case None => {
          Future.successful(None)
        }
      }
    })(pec)
  }
}