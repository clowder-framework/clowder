package services

import java.io.InputStream
import models.{UUID, Thumbnail, File}

import play.api.libs.json._
import play.api.libs.json.JsValue
import play.api.libs.json.Json._

/**
 * Created by lmarini on 2/27/14.
 */
trait ThumbnailService {


  /**
   * Count all Thumbnails
   */
  def count()


  /**
   * List all Thumbnails in the system.
   */
  def listThumbnails(): List[Thumbnail]



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
