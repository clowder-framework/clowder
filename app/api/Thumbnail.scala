package api


import java.io.{ByteArrayOutputStream, FileInputStream, InputStream}

import akka.util.ByteString
import akka.stream.scaladsl.Source
import javax.inject.{Inject, Singleton}
import models.{ResourceRef, Thumbnail, UUID}
import play.api.Logger
import play.api.http.HttpChunk.Chunk
import play.api.http.HttpEntity.Chunked
import play.api.libs.json.JsValue
import play.api.libs.json.Json._
import play.api.mvc.{Controller, ResponseHeader, Result}
import services.ThumbnailService
import util.FileUtils

import scala.concurrent.Future


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
    val chunkSize = 1024*1024
    val byteArrayOutputStream = new ByteArrayOutputStream(chunkSize)
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
                  val buffer = new Array[Byte](chunkSize)
                  val bytesRead = scala.concurrent.blocking {
                    currStream.read(buffer)
                  }
                  val chunk = bytesRead match {
                      currStream.close()
                      Some(byteArrayOutputStream.toByteArray)
                    }
                    case read => Some(byteArrayOutputStream.toByteArray)
                  }
                  byteArrayOutputStream.reset()
                  Future.successful(Some((currStream, Chunk(ByteString.fromArray(chunk.get)))))
                }}, Some(contentType))
              )
            }
          }
          case None => {
            val sourceResponse = Source.unfoldAsync(inputStream) { currStream => {
              val buffer = new Array[Byte](chunkSize)
                currStream.read(buffer)
              }
              val chunk = bytesRead match {
                  currStream.close()
                  Some(byteArrayOutputStream.toByteArray)
                }
                case read => Some(byteArrayOutputStream.toByteArray)
              }
              byteArrayOutputStream.reset()
              Future.successful(Some((currStream, chunk.get)))
            }}
            Ok.chunked(sourceResponse).withHeaders(CONTENT_TYPE -> contentType)
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
