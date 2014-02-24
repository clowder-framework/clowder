package services.mongodb

import services.TempFileService
import models.{TempFileDAO, TempFile}
import com.mongodb.casbah.Imports._
import javax.inject.Singleton

/**
 * Created by lmarini on 2/24/14.
 */
@Singleton
class MongoDBTempFileService extends TempFileService {

  def get(query_id: String): Option[TempFile] = {
    TempFileDAO.findOneById(new ObjectId(query_id))
  }
}

