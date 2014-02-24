package services

import models.TempFile

/**
 * Created by lmarini on 2/24/14.
 */
trait TempFileService {

  def get(query_id: String): Option[TempFile]
}
