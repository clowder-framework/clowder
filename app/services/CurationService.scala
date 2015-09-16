package services

import models._


/**
 * Service to manipulate curation objects.
 */
trait CurationService {
  def insert(curation: CurationObject)

  /**
   * Get curation object.
   */
  def get(id: UUID): Option[CurationObject]

  /**
   * Update submitted indicator.
   */
  def updateStatus(id: UUID, status: String)

  /**
   * remove curation object, also delete it from staging area.
   */
  def remove(id: UUID): Unit

  /**
   * add metadata to curation object, no influence to live object
   */
  def addDatasetUserMetaData(id: UUID, json: String)

  /**
   * add metadata to curation object, no influence to live object
   */
  def addFileUserMetaData(curationId: UUID, file: Int, json: String)

  /**
   * update the repository selected
   */
  def updateRepository(curationId: UUID, repository: String)
}
