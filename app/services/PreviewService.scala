package services

import models.{UUID, ThreeDAnnotation, Preview, DBResult}
import java.io.InputStream
import play.api.libs.json.JsValue

/**
 * Service to manipulate previews in files and datasets.
 */
trait PreviewService {

  /**
   * Count all preview files
   */
  def count(): Long

  /**
   * List all preview files.
   */
  def listPreviews(): List[Preview]

  def remove(id: UUID)

  def get(previewId: UUID): Option[Preview]

  def get(previewIds: List[UUID]): DBResult[Preview]

  def findByFileId(id: UUID): List[Preview]

  def findBySectionId(id: UUID): List[Preview]

  def findByDatasetId(id: UUID): List[Preview]

  def findByCollectionId(id: UUID): List[Preview]

  /**
   * Save blob.
   */
  def save(inputStream: InputStream, filename: String, contentLength: Long, contentType: Option[String]): String

  /**
   * Get blob.
   */
  def getBlob(id: UUID): Option[(InputStream, String, String, Long)]

  def removePreview(p: Preview)

  def attachToFile(previewId: UUID, fileId: UUID, extractorId: Option[String], json: JsValue)

  def attachToCollection(previewId: UUID, collectionId: UUID, previewType: String, extractorId: Option[String], json: JsValue)

  def updateMetadata(previewId: UUID, json: JsValue)

  /**
    * Updated title property of preview. If no file is given, previewer default is used.
    */
  def setTitle(previewId: UUID, title: String)

  def getMetadata(id: UUID): scala.collection.immutable.Map[String,Any]
  
  def getExtractorId(id: UUID): String
  
}
