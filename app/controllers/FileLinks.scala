package controllers

import java.text.SimpleDateFormat
import java.util.Date

import api.Permission
import com.sun.xml.internal.ws.api.model.wsdl.WSDLBoundOperation.ANONYMOUS
import javax.inject.Inject
import models._
import play.api.Logger
import play.api.libs.iteratee.Enumerator
import play.api.libs.json.Json
import play.api.mvc.{Action, ResponseHeader, Result}
import services.{FileLinkService, FileService}
import util.FileUtils
import play.api.libs.concurrent.Execution.Implicits._


class FileLinks @Inject() (files: FileService, linksService: FileLinkService) extends SecuredController {

  implicit val miniUserReads = Json.reads[MiniUser]
  implicit val miniUserWrites = Json.writes[MiniUser]
  implicit val fileLinkReads = Json.reads[FileLink]
  implicit val fileLinkWrites = Json.writes[FileLink]

  /**
    * Share the bytes of a file using a special link. Anyone with the link will be able to download this file
    */
  def share(fileId: UUID) = PermissionAction(Permission.ViewFile) { implicit request =>
    implicit val user = request.user
    files.get(fileId) match {
      case Some(file) =>
        val host = routes.Application.index().absoluteURL(controllers.Utils.https(request))
        val links = linksService.getLinkByFileId(fileId).map(link => (createURL(host, link.id.stringify), formatDate(link.expire)))
        Ok(views.html.files.share(file, links))
      case None => {
        Logger.error(s"The file with id ${fileId} is not found.")
        BadRequest(views.html.notFound("File does not exist."))
      }
    }
  }

  /**
    * API endpoint to create a new link. Returns JSON. (FIXME Should probably be moved to api package)
    * @param fileId
    * @return
    */
  def createLink(fileId: UUID) = PermissionAction(Permission.EditFile)(parse.json) { implicit request =>
    implicit val user = request.user
    request.user match {
      case Some(user) =>
        val link = linksService.createLink(fileId, user.getMiniUser, 7)
        val host = routes.Application.index().absoluteURL(controllers.Utils.https(request))
        Ok(Json.toJson(Map("link"->createURL(host, link.id.stringify), "expire"->formatDate(link.expire))))
      case None => {
        Logger.error(s"User not available in request.")
        BadRequest(views.html.notFound("User not available in request."))
      }
    }
  }

  def download(linkId: UUID) = Action { implicit request =>
    implicit val user = Some(User.anonymous)
    linksService.getLinkByLinkId(linkId) match {
      case Some(filelink) => {
        val fileid = filelink.fileId
        files.get(fileid) match {
          case Some(file) => {
            files.getBytes(fileid) match {
              case Some((inputStream, filename, contentType, contentLength)) => {

                files.incrementDownloads(fileid, user)

                request.headers.get(RANGE) match {
                  case Some(value) => {
                    val range: (Long, Long) = value.substring("bytes=".length).split("-") match {
                      case x if x.length == 1 => (x.head.toLong, contentLength - 1)
                      case x => (x(0).toLong, x(1).toLong)
                    }

                    range match {
                      case (start, end) =>
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
                          body = Enumerator.fromStream(inputStream)
                        )
                    }
                  }
                  case None => {
                    val userAgent = request.headers.get("user-agent").getOrElse("")

                    Ok.chunked(Enumerator.fromStream(inputStream))
                      .withHeaders(CONTENT_TYPE -> contentType)
                      .withHeaders(CONTENT_DISPOSITION -> (FileUtils.encodeAttachment(filename, userAgent)))
                  }
                }
              }
              case None => {
                Logger.error("Error getting file" + fileid)
                NotFound
              }
            }
          }
        }
      }
      case None => {
        Logger.debug(s"Error getting the linkId $linkId.")
        BadRequest(views.html.notFound("Invalid download link."))
      }
    }
  }
//  def download(linkId: UUID) = PermissionAction(Permission.EditFile) { implicit request =>
//    implicit val user = request.user
//    request.user match {
//      case Some(user) =>
//        val someLink = linksService.getLinkByLinkId(linkId)
//        someLink match {
//          case Some(link) =>
//            files.get(link.fileId) match {
//              case Some(file) =>
//                Ok(views.html.files.linkDownload(file, link))
//              case None =>
//                Logger.error(s"The file with id ${link.fileId} is not found.")
//                Ok()
////                BadRequest(views.html.notFound("File does not exist."))
//            }
//          case None =>
//            Logger.error(s"Download link not found.")
//            Ok()
////            BadRequest(views.html.notFound("Download Link does not exist."))
//
//        }
//      case None => {
//        Logger.error(s"User not available in request.")
////        BadRequest(views.html.notFound("User not available in request."))
//        Ok()
//      }
//
//    }
//  }

  private def formatDate(date: Date): String = {
    val format = new SimpleDateFormat("yyyy-MM-dd HH:mm")
    format.format(date)
  }

  private def createURL(prefix: String, key: String): String = prefix + "files/links/" + key
}
