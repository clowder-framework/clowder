package services

import models.{UUID, ThreeDAnnotation, Preview}
import java.io.InputStream
import play.api.libs.json.JsValue

/**
 * Created by lmarini on 2/17/14.
 */
trait PreviewService {

  def get(previewId: UUID): Option[Preview]

  def setIIPReferences(id: UUID, iipURL: String, iipImage: String, iipKey: String)

  def findByFileId(id: UUID): List[Preview]

  def findBySectionId(id: UUID): List[Preview]

  def findByDatasetId(id: UUID): List[Preview]

  def findByCollectionId(id: UUID): List[Preview]

  /**
   * Save blob.
   */
  def save(inputStream: InputStream, filename: String, contentType: Option[String]): String

  /**
   * Get blob.
   */
  def getBlob(id: UUID): Option[(InputStream, String, String, Long)]

  /**
   * Add annotation to 3D model preview.
   */
  def annotation(id: UUID, annotation: ThreeDAnnotation)

  def findAnnotation(preview_id: UUID, x_coord: String, y_coord: String, z_coord: String): Option[ThreeDAnnotation]

  def updateAnnotation(preview_id: UUID, annotation_id: UUID, description: String)
  
  def listAnnotations(preview_id: UUID): List[ThreeDAnnotation]

  def removePreview(p: Preview)

  def attachToFile(previewId: UUID, fileId: UUID, extractorId: Option[String], json: JsValue)

  def attachToCollection(previewId: UUID, collectionId: UUID, previewType: String, extractorId: Option[String], json: JsValue)

  def updateMetadata(previewId: UUID, json: JsValue)
  
  def getMetadata(id: UUID): scala.collection.immutable.Map[String,Any]
  
  def getExtractorId(id: UUID):Option[String]

  
}
  
