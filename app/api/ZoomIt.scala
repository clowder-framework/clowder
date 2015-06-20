package api

import java.io.FileInputStream
import javax.inject.{Inject, Singleton}

import play.api.Logger
import play.api.libs.json.Json._
import play.api.mvc.Controller
import services.TileService

@Singleton
class ZoomIt @Inject()(tiles: TileService) extends Controller with ApiController {

  /**
   * Upload a pyramid tile.
   */
  def uploadTile() = PermissionAction(Permission.CreatePreview)(parse.multipartFormData) { request =>
      request.body.file("File").map {
        f =>
          Logger.info("Uploading pyramid tile " + f.filename.substring(0, f.filename.lastIndexOf("_")) + f.filename.substring(f.filename.lastIndexOf(".")))
          // store file
          val id = tiles.save(new FileInputStream(f.ref.file), f.filename.substring(0, f.filename.lastIndexOf("_")) + f.filename.substring(f.filename.lastIndexOf(".")), f.contentType)
          Ok(toJson(Map("id" -> id)))
      }.getOrElse {
        BadRequest(toJson("File not attached."))
      }
  }
}