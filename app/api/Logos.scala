package api

import java.io.FileInputStream
import javax.inject.Inject

import com.wordnik.swagger.annotations.{ApiOperation, Api}
import models.{ResourceRef, UUID}
import play.api.libs.iteratee.Enumerator
import play.api.libs.json.Json._
import play.api.mvc.SimpleResult
import services.LogoService
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

@Api(value = "/logos", listingPath = "/api-docs.json/logos", description = "Logos and other data used in Clowder that is customizable")
class Logos @Inject()(logos: LogoService) extends ApiController {

  @ApiOperation(value = "List logos",
    notes = "List logos known to system",
    responseClass = "None", httpMethod = "GET")
  def list(path: Option[String], name: Option[String]) = AuthenticatedAction { implicit request =>
    Ok(toJson(logos.list(path, name)))
  }

  @ApiOperation(value = "Get logo",
    notes = "Return logo information",
    responseClass = "None", httpMethod = "GET")
  def getId(id: UUID) = UserAction { implicit request =>
    logos.get(id) match {
      case Some(logo) => Ok(toJson(logo))
      case None => NotFound(s"Did not find logo with ${id.stringify}")
    }
  }

  @ApiOperation(value = "Get logo",
    notes = "Return logo information",
    responseClass = "None", httpMethod = "GET")
  def getPath(path: String, name: String) = UserAction { implicit request =>
    logos.get(path, name) match {
      case Some(logo) => Ok(toJson(logo))
      case None => NotFound(s"Did not find logo with ${path}/${name}")
    }
  }

  @ApiOperation(value = "Get logo",
    notes = "Return logo information",
    responseClass = "None", httpMethod = "GET")
  def putId(id: UUID) = UserAction(parse.json) { implicit request =>
    logos.get(id) match {
      case Some(logo) => {
        // show text
        val newlogo = (request.body \ "showText").asOpt[Boolean] match {
          case Some(b) => logo.copy(showText=b)
          case None => logo
        }
        logos.update(newlogo)
        Ok(toJson(Map("status" -> "success")))
      }
      case None => NotFound(s"Did not find logo with ${id.stringify}")
    }
  }

  @ApiOperation(value = "Get logo",
    notes = "Return logo information",
    responseClass = "None", httpMethod = "GET")
  def putPath(path: String, name: String) = UserAction(parse.json) { implicit request =>
    logos.get(path, name) match {
      case Some(logo) => {
        // show text
        val newlogo = (request.body \ "showText").asOpt[Boolean] match {
          case Some(b) => logo.copy(showText=b)
          case None => logo
        }
        logos.update(newlogo)
        Ok(toJson(Map("status" -> "success")))
      }
      case None => NotFound(s"Did not find logo with ${path}/${name}")
    }
  }

  @ApiOperation(value = "Upload file",
    notes = "Files uploaded to this endpoint will be marked as special files, such as favicon.png, logo.png. The file" +
      " needs to be specified with image.",
    responseClass = "None", httpMethod = "POST")
  def upload = AuthenticatedAction(parse.multipartFormData) { implicit request =>
    val user = request.user.get // user is always there

    // name of uploaded item
    val name: Either[String, SimpleResult] = request.body.dataParts.get("name").map(_.head) match {
      case Some(x) => Left(x)
      case None => Right(BadRequest("Missing name parameter"))
    }

    // path to uploaded item, either GLOBAL or {resource}-{id}
    val path: Either[String, SimpleResult] = request.body.dataParts.get("path").map(_.head.split("-", 2)) match {
      case Some(Array("GLOBAL", id @_*)) => {
        if (!Permission.checkServerAdmin(request.user)) {
          Right(Forbidden(s"Need to be server admin to upload ${name}."))
        }
        Left("GLOBAL")
      }
      case Some(Array("space", id)) => {
        if (!Permission.checkPermission(user, Permission.EditSpace, new ResourceRef(ResourceRef.space, UUID(id)))) {
          Right(Forbidden(s"You do not have permission to upload '${name}' to space"))
        }
        Left("space-" + id)
      }
      case Some(p) => Right(BadRequest(s"Invalid path ${p}"))
      case None => Right(BadRequest("Missing path parameter"))
    }

    // show text
    val showText = request.body.dataParts.get("showText").fold(true)(_.head.toBoolean)

    // image to be used for path/name
    (request.body.file("image"), path, name) match {
      case (_, Right(x), _) => x
      case (_, _, Right(x)) => x
      case (Some(f), Left(p), Left(n)) => {
        var ct = f.contentType.getOrElse(play.api.http.ContentTypes.BINARY)
        if (ct == play.api.http.ContentTypes.BINARY) {
          ct = play.api.libs.MimeTypes.forFileName(f.filename).getOrElse(play.api.http.ContentTypes.BINARY)
        }
        logos.save(new FileInputStream(f.ref.file), p, n, showText, Some(ct), user) match {
          case Some(logo) => {
            // delete old images
            logos.list(Some(p), Some(n)).foreach{ l =>
              if (l.id != logo.id)
                logos.delete(l.id)
            }
            Ok(toJson(logo))
          }
          case None => BadRequest("Could not save file")
        }
      }
      case (None, _, _) => BadRequest("Missing image parameter")
    }
  }

  @ApiOperation(value = "Download file",
    notes = "Download a static file, or the alternate file",
    responseClass = "None", httpMethod = "GET")
  def downloadId(id: UUID, file: Option[String]) = UserAction.async { implicit request =>
    logos.getBytes(id) match {
      case Some((inputStream, filename, contentType, contentLength)) =>
        Future(Ok.chunked(Enumerator.fromStream(inputStream))
          .withHeaders(CONTENT_TYPE -> contentType))
      case None => {
        file match {
          case Some(f) => controllers.Assets.at("/public", f)(request)
          case None => Future(NotFound)
        }
      }
    }
  }

  @ApiOperation(value = "Download file",
    notes = "Download a static file, or the alternate file",
    responseClass = "None", httpMethod = "GET")
  def downloadPath(path: String, name: String, file: Option[String]) = UserAction.async { implicit request =>
    logos.get(path, name) match {
      case Some(logo) => downloadId(logo.id, file)(request)
      case None => {
        file match {
          case Some(f) => controllers.Assets.at("/public", f).apply(request)
          case None => Future(NotFound)
        }
      }
    }
  }

  @ApiOperation(value = "Delete file",
    notes = "Delete a static file",
    responseClass = "None", httpMethod = "DELETE")
  def deletePath(path: String, name: String) = UserAction { implicit request =>
    logos.delete(path, name)
    NoContent
  }


  @ApiOperation(value = "Delete file",
    notes = "Delete a static file",
    responseClass = "None", httpMethod = "DELETE")
  def deleteId(id: UUID) = UserAction { implicit request =>
    logos.delete(id)
    NoContent
  }
}
