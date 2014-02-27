package services

import models.{ThreeDAnnotation, Preview}
import java.io.InputStream
import play.api.libs.json.JsValue

/**
 * Created by lmarini on 2/17/14.
 */
trait PreviewService {

  def get(previewId: String): Option[Preview]

  def setIIPReferences(id: String, iipURL: String, iipImage: String, iipKey: String)

  def findByFileId(id: String): List[Preview]

  def findBySectionId(id: String): List[Preview]

  def findByDatasetId(id: String): List[Preview]

  /**
   * Save blob.
   */
  def save(inputStream: InputStream, filename: String, contentType: Option[String]): String

  /**
   * Get blob.
   */
  def getBlob(id: String): Option[(InputStream, String, String, Long)]

  /**
   * Add annotation to 3D model preview.
   */
  def annotation(id: String, annotation: ThreeDAnnotation)

  def findAnnotation(preview_id: String, x_coord: String, y_coord: String, z_coord: String): Option[ThreeDAnnotation]

  def updateAnnotation(preview_id: String, annotation_id: String, description: String)


  def listAnnotations(preview_id: String): List[ThreeDAnnotation]

  def removePreview(p: Preview)

  def attachToFile(previewId: String, fileId: String, extractorId: String, json: JsValue)

  def updateMetadata(previewId: String, json: JsValue)
}
