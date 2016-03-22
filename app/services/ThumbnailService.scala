package services

import java.io.InputStream
import models.{UUID, Thumbnail}


/**
 * Service to manipulate thumbnails
 */
trait ThumbnailService {


  /**
   * Count all Thumbnails
   */
  def count(): Long

  /**
   * List all Thumbnails in the system.
   */
  def listThumbnails(): List[Thumbnail]


  /**
   * Retrieve information for specific thumbnail
   */
  def get(thumbnailId: UUID): Option[Thumbnail]

  /**
   * Save blob.
   */
  def save(inputStream: InputStream, filename: String, contentType: Option[String]): String

  /**
   * Get blob.
   */
  def getBlob(id: UUID): Option[(InputStream, String, String, Long)]

  /**
   * Remove the blob.
   */
  def remove(id: UUID)
}
