package api

import play.api.mvc.Controller
import java.io.FileInputStream
import play.api.libs.json.Json._
import play.api.Logger
import javax.inject.{Inject, Singleton}
import services.ThumbnailService

import com.wordnik.swagger.annotations.{ApiOperation, Api}

import play.api.libs.json.JsValue
import models.Thumbnail

// import play.api.libs.json

// import scala.collection.mutable.MutableList



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
    toJson(Map("id" -> thumbnail.id.toString, "filename" -> thumbnail.filename, "content-type" -> thumbnail.contentType,  "size" -> thumbnail.length.toString))
  }

}