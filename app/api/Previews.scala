package api

import java.io.{BufferedReader, FileInputStream, FileReader}
import javax.inject.{Inject, Singleton}

import models.{ResourceRef, ThreeDAnnotation, UUID}
import play.api.Logger
import play.api.libs.concurrent.Execution.Implicits._
import play.api.libs.iteratee.Enumerator
import play.api.libs.json.{JsObject, JsValue}
import play.api.libs.json.Json._
import com.mongodb.casbah.Imports._
import org.bson.types.ObjectId
import play.api.libs.json.JsObject
import com.mongodb.WriteConcern
import models.{Preview, UUID, ThreeDAnnotation}
import play.api.libs.json.JsValue
import play.api.libs.concurrent.Execution.Implicits._
import java.io.BufferedReader
import java.io.FileReader
import javax.inject.{Inject, Singleton}
import services.{TileService, PreviewService}
import com.wordnik.swagger.annotations.{ApiOperation, Api}
/**
 * Files and datasets previews.
 */
@Singleton
class Previews @Inject()(previews: PreviewService, tiles: TileService) extends ApiController {

  @ApiOperation(value = "List all preview files", notes = "Returns list of preview files and descriptions.", responseClass = "None", httpMethod = "GET")
  def list = PermissionAction(Permission.ViewFile) {
    request =>
      val list = for (p <- previews.listPreviews()) yield jsonPreview(p)
      Ok(toJson(list))
  }


    @ApiOperation(value = "Delete previews",
      notes = "Remove preview file from system).",
      responseClass = "None", httpMethod = "POST")
    def removePreview(id: UUID) = PermissionAction(Permission.DeleteFile, Some(ResourceRef(ResourceRef.preview, id))) {
      request =>
        previews.get(id) match {
          case Some(preview) => {
            previews.remove(id)
            Ok(toJson(Map("status"->"success")))
          }
          case None => Ok(toJson(Map("status" -> "success")))
        }
    }

  def downloadPreview(id: UUID, datasetid: UUID) = PermissionAction(Permission.ViewFile, Some(ResourceRef(ResourceRef.preview, id))) {request =>
    Redirect(routes.Previews.download(id))
  }

  /**
   * Download preview bytes.
   */
  def download(id: UUID) =
    PermissionAction(Permission.ViewFile, Some(ResourceRef(ResourceRef.preview, id))) { implicit request =>
        previews.getBlob(id) match {

          case Some((inputStream, filename, contentType, contentLength)) => {
            request.headers.get(RANGE) match {
              case Some(value) => {
                val range: (Long, Long) = value.substring("bytes=".length).split("-") match {
                  case x if x.length == 1 => (x.head.toLong, contentLength - 1)
                  case x => (x(0).toLong, x(1).toLong)
                }
                range match {
                  case (start, end) =>

                    inputStream.skip(start)
                    import play.api.mvc.{ResponseHeader, SimpleResult}
                    SimpleResult(
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
                  .withHeaders(CONTENT_DISPOSITION -> ("attachment; filename=" + filename))

              }
            }
          }
          case None => Logger.error("No preview find " + id); InternalServerError("No preview found")
        }
    }


  /**
   * Upload a preview.
   */
  def upload(iipKey: String = "") =
    PermissionAction(Permission.AddFile)(parse.multipartFormData) { implicit request =>
        request.body.file("File").map { f =>
          try {
            Logger.debug("########Uploading Preview----" + f.filename)
            // store file
            //change stored preview type for zoom.it previews to avoid messup with uploaded XML metadata files
            var realContentType = f.contentType
            if (f.contentType.getOrElse("application/octet-stream").equals("application/xml"))
              realContentType = Some("application/dzi")

            val id = UUID(previews.save(new FileInputStream(f.ref.file), f.filename, realContentType))
            Logger.debug("ctp: " + realContentType)
            // for IIP server references, store the IIP URL, key and filename on the IIP server for possible later deletion of the previewed file
            if (f.filename.endsWith(".imageurl")) {
              val iipRefReader = new BufferedReader(new FileReader(f.ref.file));

              val serverLine = iipRefReader.readLine()
              var urlEnd = serverLine.indexOf("/", serverLine.indexOf("://") + 3)
              if (urlEnd == -1) {
                urlEnd = serverLine.length()
              }
              val iipURL = serverLine.substring(8, urlEnd)

              val imageLine = iipRefReader.readLine()
              val iipImage = imageLine.substring(imageLine.lastIndexOf("/") + 1)

              iipRefReader.close()

              previews.setIIPReferences(id, iipURL, iipImage, iipKey)
            }

            // Check whether a title for the preview was sent
            request.body.dataParts.get("title") match {
              case Some(t: List[String]) => previews.setTitle(id, t.head)
              case None => {}
            }

            Ok(toJson(Map("id" -> id.stringify)))
          } finally {
            f.ref.clean()
          }
        }.getOrElse {
          BadRequest(toJson("File not attached."))
        }
    }

  /**
   * Upload preview metadata.
   *
   */
  def uploadMetadata(id: UUID) = PermissionAction(Permission.AddFile, Some(ResourceRef(ResourceRef.preview, id)))(parse.json) { implicit request =>
        Logger.debug(request.body.toString)
        request.body match {
          case JsObject(fields) => {
            Logger.debug(fields.toString)
            previews.get(id) match {
              case Some(preview) =>
                previews.updateMetadata(id, request.body)

                Ok(toJson(Map("status" -> "success")))
              case None => BadRequest(toJson("Preview not found"))
            }
          }
          case _ => Logger.error("Expected a JSObject"); BadRequest(toJson("Expected a JSObject"))

        }
    }

  /**
   * Get preview metadata.
   *
   */
  def getMetadata(id: UUID) = PermissionAction(Permission.ViewFile, Some(ResourceRef(ResourceRef.preview, id))) { implicit request =>
        previews.get(id) match {
          case Some(preview) => {
            val title = preview.title.orNull
            Ok(toJson(Map("id" -> preview.id.toString, "contentType" -> preview.contentType, "title" -> title)))
          }
          case None => Logger.error("Preview metadata not found " + id); InternalServerError
        }
    }

  /**
   * Add pyramid tile to preview.
   */
  def attachTile(preview_id: UUID, tile_id: UUID, level: String) =  PermissionAction(Permission.AddFile, Some(ResourceRef(ResourceRef.preview, preview_id)))(parse.json) { implicit request =>
        request.body match {
          case JsObject(fields) => {
            previews.get(preview_id) match {
              case Some(preview) => {
                tiles.get(tile_id) match {
                  case Some(tile) => {
                    tiles.updateMetadata(tile_id, preview_id, level, request.body)
                    Ok(toJson(Map("status" -> "success")))
                  }
                  case None => BadRequest(toJson("Tile not found"))
                }
              }
              case None => BadRequest(toJson("Preview not found " + preview_id))
            }
          }
          case _ => Ok("received something else: " + request.body + '\n')
        }
    }


  /**
   * Find tile for given preview, level and filename (row and column).
   */
  def getTile(dzi_id_dir: String, level: String, filename: String) = PermissionAction(Permission.ViewFile, Some(ResourceRef(ResourceRef.preview, UUID(dzi_id_dir.replaceAll("_files", ""))))) { implicit request =>
        val dzi_id = dzi_id_dir.replaceAll("_files", "")
        tiles.findTile(UUID(dzi_id), filename, level) match {
          case Some(tile) => {

            tiles.getBlob(tile.id) match {

              case Some((inputStream, tilename, contentType, contentLength)) => {
                request.headers.get(RANGE) match {
                  case Some(value) => {
                    val range: (Long, Long) = value.substring("bytes=".length).split("-") match {
                      case x if x.length == 1 => (x.head.toLong, contentLength - 1)
                      case x => (x(0).toLong, x(1).toLong)
                    }
                    range match {
                      case (start, end) =>

                        inputStream.skip(start)
                        import play.api.mvc.{ResponseHeader, SimpleResult}
                        SimpleResult(
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
                      .withHeaders(CONTENT_DISPOSITION -> ("attachment; filename=" + tilename))

                  }
                }
              }
              case None => Logger.error("No tile found: " + tile.id.toString()); InternalServerError("No tile found")

            }

          }
          case None => Logger.error("Tile not found"); InternalServerError
        }
    }

  /**
   * Add annotation to 3D model preview.
   */
  def attachAnnotation(preview_id: UUID) = PermissionAction(Permission.AddFile, Some(ResourceRef(ResourceRef.preview, preview_id)))(parse.json) { implicit request =>
        val x_coord = (request.body \ "x_coord").asOpt[String].getOrElse("0.0")
        val y_coord = (request.body \ "y_coord").asOpt[String].getOrElse("0.0")
        val z_coord = (request.body \ "z_coord").asOpt[String].getOrElse("0.0")
        val description = (request.body \ "description").asOpt[String].getOrElse("")

        previews.get(preview_id) match {
          case Some(preview) => {
            val annotation = ThreeDAnnotation(x_coord, y_coord, z_coord, description)
            previews.annotation(preview_id, annotation)
            Ok(toJson(Map("status" -> "success")))
          }
          case None => BadRequest(toJson("Preview not found " + preview_id))
        }
    }

  def editAnnotation(preview_id: UUID) = PermissionAction(Permission.AddFile, Some(ResourceRef(ResourceRef.preview, preview_id)))(parse.json) { implicit request =>
        Logger.debug("thereq: " + request.body.toString)
        val x_coord = (request.body \ "x_coord").asOpt[String].getOrElse("0.0")
        val y_coord = (request.body \ "y_coord").asOpt[String].getOrElse("0.0")
        val z_coord = (request.body \ "z_coord").asOpt[String].getOrElse("0.0")
        val description = (request.body \ "description").asOpt[String].getOrElse("")

        previews.get(preview_id) match {
          case Some(preview) => {
            previews.findAnnotation(preview_id, x_coord, y_coord, z_coord) match {
              case Some(annotation) => {
                previews.updateAnnotation(preview_id, annotation.id, description)
                Ok(toJson(Map("status" -> "success")))
              }
              case None => Ok(toJson(Map("status" -> "success"))) //What the user sees locally must not change if an annotation is deleted after the user loads the dataset
              //but before attempting to modify the selected annotation's description.
              //BadRequest(toJson("Annotation for preview " + preview_id + " not found: " + x_coord + "," + y_coord + "," + z_coord))
            }
          }
          case None => BadRequest(toJson("Preview not found " + preview_id))
        }
    }

  def listAnnotations(preview_id: UUID) = PermissionAction(Permission.ViewFile, Some(ResourceRef(ResourceRef.preview, preview_id))) { implicit request =>
        previews.get(preview_id) match {
          case Some(preview) => {
            val annotationsOfPreview = previews.listAnnotations(preview_id)
            val list = for (annotation <- annotationsOfPreview) yield jsonAnnotation(annotation)
            Logger.debug("thelist: " + toJson(list))
            Ok(toJson(list))
          }
          case None => BadRequest(toJson("Preview not found " + preview_id))
        }
    }

  /**
    * Update the title field of a preview to change what is displayed on preview tab
    * @param preview_id UUID of preview to change
    */
  @ApiOperation(value = "Update displayed title of preview",
    notes = "Change the title of the preview that is displayed when viewing the associated file. String must be passed as 'title' parameter in request.",
    responseClass = "None", httpMethod = "POST")
  def setTitle(preview_id: UUID) = PermissionAction(Permission.AddFile, Some(ResourceRef(ResourceRef.preview, preview_id)))(parse.json) { implicit request =>
    request.body match {
      case JsObject(fields) => {
        previews.get(preview_id) match {
          case Some(preview) => {
            (request.body \ "title").asOpt[String] match {
              case Some(t) => {
                previews.setTitle(preview_id, t.replace("\"",""))
                Ok(toJson(Map("status" -> "success")))
              }
              case None => BadRequest(toJson("Preview not found"))
            }
          }
        }
      }
      case _ => Logger.error("Expected a JSObject"); BadRequest(toJson("Expected a JSObject"))
    }
  }

  def jsonAnnotation(annotation: ThreeDAnnotation): JsValue = {
    toJson(Map("x_coord" -> annotation.x_coord.toString, "y_coord" -> annotation.y_coord.toString, "z_coord" -> annotation.z_coord.toString, "description" -> annotation.description))
  }


  def jsonPreview(preview: Preview): JsValue = {
    toJson(Map("id" -> preview.id.toString, "filename" -> preview.filename.getOrElse(""), "contentType" -> preview.contentType))
  }

}
