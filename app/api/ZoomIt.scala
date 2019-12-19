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
  def uploadTile() = PermissionAction(Permission.CreatePreview)(parse.multipartFormData) { implicit request =>
      request.body.file("File").map {
        f =>
          Logger.debug("Uploading pyramid tile " + f.filename.substring(0, f.filename.lastIndexOf("_")) + f.filename.substring(f.filename.lastIndexOf(".")))
          // store file
          try {
            val id = tiles.save(new FileInputStream(f.ref.file), f.filename.substring(0, f.filename.lastIndexOf("_")) + f.filename.substring(f.filename.lastIndexOf(".")), f.contentType)
            Ok(toJson(Map("id" -> id)))
          } finally {
            f.ref.clean()
          }
      }.getOrElse {
        BadRequest(toJson("File not attached."))
      }
  }
}
