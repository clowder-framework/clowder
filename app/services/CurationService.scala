package services

import models._


/**
 * Service to manipulate curation objects.
 */
trait CurationService {
  def insert(curation: CurationObject)

  /**
   * Get collection.
   */
  def get(id: UUID): Option[CurationObject]

  /**
   * Update submitted indicator.
   */
  def setSubmitted(id: UUID, submitted: Boolean)
}
