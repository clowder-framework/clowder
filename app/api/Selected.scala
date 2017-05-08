package api

import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.util.zip.ZipOutputStream

import Iterators.SelectedIterator
import play.api.libs.iteratee.Enumerator
import play.api.mvc.Controller
import play.api.libs.json.Json._
import play.api.Logger
import play.api.Play.current
import javax.inject.Inject
import services._
import models.{User, Dataset, UUID}
import util.FileUtils

import scala.concurrent.{ExecutionContext, Future}
import play.api.libs.concurrent.Execution.Implicits._

/**
 * Selected items.
 * 
 * @author Luigi Marini
 *
 */
class Selected @Inject()(selections: SelectionService,
                         datasets: DatasetService,
                         files: FileService,
                         spaces:SpaceService,
                         folders : FolderService,
                         metadataService : MetadataService) extends Controller with ApiController {

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
        selections.get(user.email.get).map(d => {
          datasets.removeDataset(d.id)
          selections.remove(d.id, user.email.get)
        })
        Ok(toJson(Map("sucess"->"true")))
      }
    }
  }

  def downloadAll() = AuthenticatedAction { implicit request =>
    Logger.debug("Requesting Selected.downloadAll")
    request.user match {
      case Some(user) => {
        val bagit = play.api.Play.configuration.getBoolean("downloadDatasetBagit").getOrElse(true)
        val selected = selections.get(user.email.get)
        Ok.chunked(enumeratorFromSelected(selected,1024*1024,bagit,Some(user))).withHeaders(
          "Content-Type" -> "application/zip",
          "Content-Disposition" -> (FileUtils.encodeAttachment("Selected Datasets.zip", request.headers.get("user-agent").getOrElse("")))
        )
      }
      case None => NotFound
    }
  }

  def enumeratorFromSelected(selected: List[Dataset], chunkSize: Int = 1024 * 8, bagit: Boolean, user : Option[User])
                            (implicit ec: ExecutionContext): Enumerator[Array[Byte]] = {
    implicit val pec = ec.prepare()
    val md5Files = scala.collection.mutable.HashMap.empty[String, MessageDigest]
    val md5Bag = scala.collection.mutable.HashMap.empty[String, MessageDigest]

    var totalBytes = 0L
    var bytesSet = false

    val byteArrayOutputStream = new ByteArrayOutputStream(chunkSize)
    val zip = new ZipOutputStream(byteArrayOutputStream)

    val current_iterator = new SelectedIterator("Selected Datasets", selected, zip, md5Files, md5Bag, user,
      totalBytes, bagit, datasets, files, folders, metadataService, spaces)

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
              Logger.debug("-1")
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
              Logger.debug("READ")
              if (!current_iterator.isBagIt()){
                totalBytes += bytesRead
              }
              zip.write(buffer, 0, read)
              Some(byteArrayOutputStream.toByteArray)
            }
          }
          Logger.debug("CHUNKZ")
          byteArrayOutputStream.reset()
          Future.successful(chunk)
        }
        case None => {
          Logger.debug("NUNCHUNKZ")
          Future.successful(None)
        }
      }
    })(pec)
  }
}