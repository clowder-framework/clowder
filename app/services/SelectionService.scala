package services

import models.{UUID, Dataset}

/**
 * Service for adding & removing user selections, as well as downloading/deleting selected datasets.
 */
trait SelectionService {

  def add(dataset: UUID, user: String)

  def remove(dataset: UUID, user: String)

  def get(user: String): List[Dataset]

  def deleteAll(user: String)

  def downloadAll(user: String)

}
