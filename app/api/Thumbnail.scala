package api


import java.io.FileInputStream
import javax.inject.{Inject, Singleton}

import models.{ResourceRef, Thumbnail, UUID}
import play.api.Logger
import play.api.libs.iteratee.Enumerator
import play.api.libs.json.JsValue
import play.api.libs.json.Json._
import play.api.libs.concurrent.Execution.Implicits._
import play.api.mvc.Controller
import services.ThumbnailService
import util.FileUtils


@Singleton
class Thumbnails @Inject() (thumbnails: ThumbnailService) extends Controller with ApiController {

  /**
   * List all files.
   */
  def list = PrivateServerAction { implicit request =>
    val list = for (t <- thumbnails.listThumbnails()) yield jsonThumbnail(t)
    Ok(toJson(list))
  }

  def get(id: UUID) = PermissionAction(Permission.ViewFile, Some(ResourceRef(ResourceRef.thumbnail, id))) { implicit request =>
    thumbnails.getBlob(id) match {
      case Some((inputStream, filename, contentType, contentLength)) => {
        request.headers.get(RANGE) match {
          case Some(value) => {
            val range: (Long, Long) = value.substring("bytes=".length).split("-") match {
              case x if x.length == 1 => (x.head.toLong, contentLength - 1)
              case x => (x(0).toLong, x(1).toLong)
            }
            range match { case (start,end) =>
              inputStream.skip(start)
              import play.api.mvc.{ ResponseHeader, Result }
              Result(
                header = ResponseHeader(PARTIAL_CONTENT,
                  Map(
                    CONNECTION -> "keep-alive",
                    ACCEPT_RANGES -> "bytes",
                    CONTENT_RANGE -> "bytes %d-%d/%d".format(start, end, contentLength),
                    CONTENT_LENGTH -> (end - start + 1).toString,
                    CONTENT_TYPE -> contentType
                  )
                ),
                body = Enumerator.fromStream(inputStream)
              )
            }
          }
          case None => {
            Ok.chunked(Enumerator.fromStream(inputStream))
              .withHeaders(CONTENT_TYPE -> contentType)
              .withHeaders(CONTENT_DISPOSITION -> (FileUtils.encodeAttachment(filename, request.headers.get("user-agent").getOrElse(""))))
          }
        }
      }
      case None => {
        Logger.error("Error getting thumbnail " + id)
        NotFound
      }
    }

  }

  def removeThumbnail(id: UUID) = PermissionAction(Permission.EditFile, Some(ResourceRef(ResourceRef.thumbnail, id))) { implicit request =>
    thumbnails.get(id) match {
      case Some(thumbnail) => {
        Logger.debug("Deleting file: " + thumbnail.filename)
        thumbnails.remove(id)
        Ok(toJson(Map("status"->"success")))
      }
      case None => {
        Logger.debug("Couldn't find thumbnail")
        Ok(toJson(Map("status" -> "success")))
      }
    }
  }

  /**
   * Upload a file thumbnail.
   */
  def uploadThumbnail() = PermissionAction(Permission.CreatePreview)(parse.multipartFormData) { implicit request =>
    request.body.file("File").map { f =>
      f.ref.file.length() match{
        case 0L => {
          BadRequest(toJson("File is empty."))
        }
        case _ => {
          Logger.debug("Uploading thumbnail " + f.filename)
        // store file
        val id = thumbnails.save(new FileInputStream(f.ref.file), f.filename, f.ref.file.length, f.contentType)
        Ok(toJson(Map("id"->id)))
        }
      }
    }.getOrElse {
       BadRequest(toJson("File not attached."))
    }
  }

  def jsonThumbnail(thumbnail: Thumbnail): JsValue = {
    toJson(Map("id" -> thumbnail.id.toString(),  "filename" -> thumbnail.filename.getOrElse(""),
      "content-type" -> thumbnail.contentType, "date-created" -> thumbnail.uploadDate.toString(), "size" -> thumbnail.length.toString()))

  }
}
