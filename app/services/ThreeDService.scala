package services

import models.{ThreeDGeometry, ThreeDTexture}
import java.io.InputStream
import play.api.libs.json.JsValue

/**
 * Created by lmarini on 2/26/14.
 */
trait ThreeDService {

  def getTexture(textureId: String): Option[ThreeDTexture]

  def findTexture(fileId: String, filename: String): Option[ThreeDTexture]

  def findTexturesByFileId(fileId: String): List[ThreeDTexture]

  def updateTexture(fileId: String, textureId: String, fields: Seq[(String, JsValue)])

  def updateGeometry(fileId: String, geometryId: String, fields: Seq[(String, JsValue)])

  def save(inputStream: InputStream, filename: String, contentType: Option[String]): String

  def getBlob(id: String): Option[(InputStream, String, String, Long)]

  def findGeometry(fileId: String, filename: String): Option[ThreeDGeometry]

  def getGeometry(id: String): Option[ThreeDGeometry]

  def saveGeometry(inputStream: InputStream, filename: String, contentType: Option[String]): String

  def getGeometryBlob(id: String): Option[(InputStream, String, String, Long)]
}
