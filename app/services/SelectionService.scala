package services

import models.{UUID, Dataset}

/**
 * Created by lmarini on 4/24/14.
 */
trait SelectionService {

  def add(dataset: UUID, user: String)

  def remove(dataset: UUID, user: String)

  def get(user: String): List[Dataset]

}
