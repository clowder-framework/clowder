package services

import models.{UUID, TempFile}

/**
 * Created by lmarini on 2/24/14.
 */
trait TempFileService {

  def get(query_id: UUID): Option[TempFile]
  
  /**
   * Update thumbnail used to represent this query file.
   */
  def updateThumbnail(queryId: UUID, thumbnailId: UUID)
}
