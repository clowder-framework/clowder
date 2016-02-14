package services

import java.io.InputStream

import models.{Logo, UUID, File}
import securesocial.core.Identity

trait LogoService {
  /**
   * Return list of all logos
   */
  def list(path: Option[String], name: Option[String]): List[Logo]

  /**
   * Save a logo from an input stream.
   */
  def save(inputStream: InputStream, path: String, name: String, showText:Boolean, contentType: Option[String], author: Identity): Option[Logo]

  /**
   * Updates a logo
   */
  def update(logo: Logo)

  /**
   * Return the specified object.
   */
  def get(path: String, name: String): Option[Logo]

  /**
   * Return the specified object.
   */
  def get(id: UUID): Option[Logo]

  /**
   * Get the input stream of a file given a file id.
   * Returns input stream, file name, content type, content length.
   */
  def getBytes(path: String, name: String): Option[(InputStream, String, String, Long)]

  /**
   * Get the input stream of a file given a file id.
   * Returns input stream, file name, content type, content length.
   */
  def getBytes(id: UUID): Option[(InputStream, String, String, Long)]

  /**
   * Remove the file from mongo
   */
  def delete(path: String, name: String)

  /**
   * Remove the file from mongo
   */
  def delete(id: UUID)
}
