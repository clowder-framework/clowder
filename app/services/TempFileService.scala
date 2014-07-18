package services

import models.{UUID, TempFile}

/**
 * Created by lmarini on 2/24/14.
 */
trait TempFileService {

  def get(query_id: UUID): Option[TempFile]
}
