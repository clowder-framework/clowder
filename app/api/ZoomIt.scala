package api

import play.api.mvc.Controller
import java.io.FileInputStream
import play.api.libs.json.Json._
import play.api.Logger
import javax.inject.{Inject, Singleton}
import services.TileService

@Singleton
class ZoomIt @Inject()(tiles: TileService) extends Controller with ApiController {

  /**
   * Upload a pyramid tile.
   */
  def uploadTile() = SecuredAction(parse.multipartFormData, authorization = WithPermission(Permission.CreatePreview)) {
    request =>
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