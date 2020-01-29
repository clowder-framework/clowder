package api

import java.io.FileInputStream
import javax.inject.Inject

import models.{Logo, ResourceRef, UUID, User}
import play.api.libs.iteratee.Enumerator
import play.api.libs.json.JsValue
import play.api.libs.json.Json._
import play.api.mvc.{Action, Result, SimpleResult}
import services.LogoService

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class Logos @Inject()(logos: LogoService) extends ApiController {

  def list(path: Option[String], name: Option[String]) = AuthenticatedAction { implicit request =>
    Ok(toJson(logos.list(path, name)))
  }

  def getId(id: UUID) = Action { implicit request =>
    logos.get(id) match {
      case Some(logo) => Ok(toJson(logo))
      case None => NotFound(s"Did not find logo with ${id.stringify}")
    }
  }

  def getPath(path: String, name: String) = Action { implicit request =>
    logos.get(path, name) match {
      case Some(logo) => Ok(toJson(logo))
      case None => NotFound(s"Did not find logo with ${path}/${name}")
    }
  }

  def putId(id: UUID) = AuthenticatedAction(parse.json) { implicit request =>
    put(logos.get(id), request)
  }

  def putPath(path: String, name: String) = AuthenticatedAction(parse.json) { implicit request =>
    put(logos.get(path, name), request)
  }

  // Actual implementation of put operation
  private def put(logo: Option[Logo], request: UserRequest[JsValue]): Result = {
    logo match {
      case Some(l) => {
        checkLogoPermission(logo, request.user) match {
          case Left(_) => {
            (request.body \ "showText").asOpt[Boolean] match {
              case Some(b) => logos.update(l.copy(showText = b))
              case None => {}
            }
            Ok(toJson(Map("status" -> "success")))
          }
          case Right(result) => result
        }
      }
      case None => NotFound(s"Did not find logo")
    }
  }

  def upload = AuthenticatedAction(parse.multipartFormData) { implicit request =>
    val user = request.user.get // user is always there

    if (!request.body.dataParts.get("name").exists(_.nonEmpty)) {
      BadRequest("Missing name parameter")
    } else if (!request.body.dataParts.get("path").exists(_.nonEmpty)) {
      BadRequest("Missing path parameter")
    } else {

      // get name, showtext we know they exist
      val name = request.body.dataParts.get("name").get.head
      val showText = request.body.dataParts.get("showText").fold(true)(_.head.toBoolean)

      checkLogoPermission(request.body.dataParts.get("path").get.head, name, request.user) match {
        case Left(p) => {
          request.body.file("image") match {
            case Some(f) => {
              val ct = util.FileUtils.getContentType(f.filename, f.contentType)
              logos.save(new FileInputStream(f.ref.file), p, name, showText,
                f.ref.file.length, Some(ct), user) match {
                case Some(logo) => {
                  // delete old images
                  logos.list(Some(p), Some(name)).foreach { l =>
                    if (l.id != logo.id)
                      logos.delete(l.id)
                  }
                  Ok(toJson(logo))
                }
                case None => BadRequest("Could not save file")
              }
            }
            case None => BadRequest("Missing image parameter")
          }
        }
        case Right(result) => result
      }
    }
  }

  def downloadId(id: UUID, file: Option[String]) = Action.async { implicit request =>
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

  def downloadPath(path: String, name: String, file: Option[String]) = Action.async { implicit request =>
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

  def deletePath(path: String, name: String) = AuthenticatedAction { implicit request =>
    checkLogoPermission(path, name, request.user) match {
      case Left(_) => {
        logos.delete(path, name)
        NoContent
      }
      case Right(result) => result
    }
  }


  def deleteId(id: UUID) = AuthenticatedAction { implicit request =>
    checkLogoPermission(logos.get(id), request.user) match {
      case Left(_) => {
        logos.delete(id)
        NoContent
      }
      case Right(result) => result
    }
  }

  private def checkLogoPermission(logo: Option[Logo], user: Option[User]):Either[String, SimpleResult] = {
    logo match {
      case Some(l) => checkLogoPermission(l.path, l.name, user)
      case None => Right(Forbidden(s"You do not have permission to modify logo"))
    }
  }

  private def checkLogoPermission(path: String, name: String, user: Option[User]):Either[String, SimpleResult] = {
    path.split("-", 2) match {
      case Array("GLOBAL", id @_*) => {
        if (Permission.checkServerAdmin(user)) {
          Left("GLOBAL")
        } else {
          Right(Forbidden(s"Need to be server admin to modify ${name}."))
        }
      }
      case Array("space", id) => {
        if (Permission.checkPermission(user, Permission.EditSpace, new ResourceRef(ResourceRef.space, UUID(id)))) {
          Left("space-" + id)
        } else {
          Right(Forbidden(s"You do not have permission to modify '${name}' to space"))
        }
      }
      case Array("user", id) => {
        if (Permission.checkPermission(user, Permission.EditUser, new ResourceRef(ResourceRef.user, UUID(id)))) {
          Left("user-" + id)
        } else {
          Right(Forbidden(s"You do not have permission to modify '${name}' to user"))
        }
      }
      case Array(t, _) => Right(Forbidden(s"You do not have permission to modify '${name}' to ${t}"))
    }
  }
}
