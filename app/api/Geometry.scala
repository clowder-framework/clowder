package api

import java.io.FileInputStream
import javax.inject.Inject

import play.api.Logger
import play.api.libs.json.Json._
import play.api.mvc.Controller
import services.ThreeDService

class Geometry @Inject()(threeD: ThreeDService) extends Controller with ApiController {

  /**
   * Upload a 3D binary geometry file.
   */  
  def uploadGeometry() = PermissionAction(Permission.AddFile)(parse.multipartFormData) { implicit request =>
      request.body.file("File").map { f =>
        Logger.info("Uploading binary geometry file " + f.filename)
        // store file
        val id = threeD.saveGeometry(new FileInputStream(f.ref.file), f.filename, f.contentType)
        Ok(toJson(Map("id" -> id)))
      } finally {
        f.ref.clean()
      }
    }.getOrElse {
       BadRequest(toJson("File not attached."))
    }
  }
}