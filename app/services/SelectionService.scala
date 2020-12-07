package services

import models.{Dataset, File, UUID}

/**
 * Service for adding & removing user selections, as well as downloading/deleting selected datasets.
 */
trait SelectionService {

  def add(dataset: UUID, user: String)

  def remove(dataset: UUID, user: String)

  def addFile(fileId: UUID, user: String)

  def removeFile(fileId: UUID, user: String)

  def get(user: String): List[Dataset]

  def getFiles(user: String) : List[File]

  def deleteAll(user: String)

  def downloadAll(user: String)

}
