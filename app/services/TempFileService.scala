package services

import models.{UUID, TempFile}

/**
 * Service to manipulate tempfiles.
 */
trait TempFileService {

  def get(query_id: UUID): Option[TempFile]
  
  /**
   * Update thumbnail used to represent this query file.
   */
  def updateThumbnail(queryId: UUID, thumbnailId: UUID)
}
