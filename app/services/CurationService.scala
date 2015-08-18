package services

import models._
import services.core.CRUDService

/**
 * Service to manipulate curation objects.
 */
trait CurationService {
  def insert(curation: CurationObject)

  /**
   * Get collection.
   */
  def get(id: UUID): Option[CurationObject]
}
