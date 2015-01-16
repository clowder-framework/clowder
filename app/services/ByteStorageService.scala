package services

import java.io.InputStream

import models.UUID

/**
 * Interface to store bytes. This is used by other services that store the metadata
 * about the bytes.
 *
 * @author Rob Kooper
 */
trait ByteStorageService {
  /**
   * Save the inputstream, returns a path to where the bytes are stored. The
   * path can be later used to load/delete the bytes
   */
  def save(inputStream: InputStream, prefix: String, id: UUID): Option[String]

  /**
   * Load the bytes from the backing storage, returns an InputStream. The path
   * given is the same path as returned by save.
   */
  def load(path: String, prefix: String): Option[InputStream]

  /**
   * Delete the bytes from the backing storage, returns true if the bytes are
   * actually removed. The path given is the same path as returned by save.
   */
  def delete(path: String, prefix: String): Boolean
}
