package services

import models.Tile
import java.io.InputStream
import play.api.libs.json.JsValue

/**
 * Created by lmarini on 2/27/14.
 */
trait TileService {

  def get(tileId: String): Option[Tile]

  def updateMetadata(tileId: String, previewId: String, level: String, json: JsValue)

  def findTile(previewId: String, filename: String, level: String): Option[Tile]

  def findByPreviewId(previewId: String): List[Tile]

  def save(inputStream: InputStream, filename: String, contentType: Option[String]): String

  def getBlob(id: String): Option[(InputStream, String, String, Long)]
}
