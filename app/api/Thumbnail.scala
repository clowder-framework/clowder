package api

import controllers.Utils
import play.api.Play._
import play.api.mvc.Controller
import java.io.FileInputStream
import play.api.libs.json.Json._
import play.api.libs.json._
import play.api.Logger
import javax.inject.{Inject, Singleton}
import services.{AdminsNotifierPlugin, ElasticsearchPlugin, VersusPlugin, ThumbnailService}

import com.wordnik.swagger.annotations.{ApiOperation, Api}

import java.net.{URLEncoder}
import models._
import play.api.libs.json.JsValue
import javax.inject.Inject
import com.wordnik.swagger.annotations.{ApiOperation, Api}



@Singleton
@Api(value = "/thumbnails", listingPath = "/api-docs.json/thumbnails", description = "A thumbnail is the raw bytes plus metadata.")
class Thumbnails @Inject() (thumbnails: ThumbnailService) extends Controller with ApiController {


   /**
   * List all files.
   */
  @ApiOperation(value = "List all thumbnail files", notes = "Returns list of thumbnail files and descriptions.", responseClass = "None", httpMethod = "GET")
  def list = SecuredAction(parse.anyContent, authorization = WithPermission(Permission.ListThumbnails)) {
    request =>
      val list = for (t <- thumbnails.listThumbnails()) yield jsonThumbnail(t)
      Ok(toJson(list))
  }



  @ApiOperation(value = "Delete thumbnail",
    notes = "Remove thumbnail file from system).",
    responseClass = "None", httpMethod = "DELETE")
  def removeThumbnail(id: UUID) = SecuredAction(parse.anyContent, authorization = WithPermission(Permission.DeleteThumbnails)) {
    request =>
      thumbnails.get(id) match {
        case Some(thumbnail) => {

          //this stmt has to be before files.removeFile
          Logger.debug("Deleting file from indexes" + thumbnail.filename)
          current.plugin[VersusPlugin].foreach {
            _.removeFromIndexes(id)
          }
          Logger.debug("Deleting file: " + thumbnail.filename)
          thumbnails.removeThumbnail(id)

          Ok(toJson(Map("status"->"success")))
          current.plugin[AdminsNotifierPlugin].foreach{
            _.sendAdminsNotification(Utils.baseUrl(request), "File","removed",id.stringify, getFilenameOrEmpty(thumbnail))}
          Ok(toJson(Map("status"->"success")))
        }
        case None => Ok(toJson(Map("status" -> "success")))
      }
  }






   /**
   * Upload a file thumbnail.
   */  
  def uploadThumbnail() = SecuredAction(parse.multipartFormData, authorization=WithPermission(Permission.AddThumbnail)) { request => 
      request.body.file("File").map { f =>
        f.ref.file.length() match{
          case 0L => {
            BadRequest(toJson("File is empty."))
          }
          case _ => {
            Logger.info("Uploading thumbnail " + f.filename)
	        // store file
	        val id = thumbnails.save(new FileInputStream(f.ref.file), f.filename, f.contentType)
	        Ok(toJson(Map("id"->id))) 
          }
        }  
      }.getOrElse {
         BadRequest(toJson("File not attached."))
      }
  }


  def jsonThumbnail(thumbnail: Thumbnail): JsValue = {
    toJson(Map("id" -> thumbnail.id.toString(), "chunksize" -> thumbnail.chunkSize.toString(),  "filename" -> getFilenameOrEmpty(thumbnail),
      "content-type" -> thumbnail.contentType, "date-created" -> thumbnail.uploadDate.toString(), "size" -> thumbnail.length.toString()))

  }


  def getFilenameOrEmpty(thumbanail: Thumbnail): String = {
    thumbanail.filename match {
      case Some(string) => string
      case None => ""
    }
  }

//  def jsonFile(file: File): JsValue = {
//    toJson(Map("id" -> file.id.toString, "filename" -> file.filename, "content-type" -> file.contentType, "date-created" -> file.uploadDate.toString(), "size" -> file.length.toString,
//      "authorId" -> file.author.identityId.userId))
//  }

}