package services.mongodb

import com.mongodb.casbah.Imports._
import com.mongodb.casbah.commons.MongoDBObject
import javax.inject.Singleton
import models.{TempFile, UUID}
import org.bson.types.ObjectId
import salat.dao.{ModelCompanion, SalatDAO}
import services.mongodb.MongoContext.context
import services.{DI, TempFileService}


/**
 * Use Mongodb to store tempfiles.
 */
@Singleton
class MongoDBTempFileService extends TempFileService {

  def get(query_id: UUID): Option[TempFile] = {
    TempFileDAO.findOneById(new ObjectId(query_id.stringify))
  }
  
  /**
   * Update thumbnail used to represent this query.
   */
  def updateThumbnail(queryId: UUID, thumbnailId: UUID) {
    TempFileDAO.update(MongoDBObject("_id" -> new ObjectId(queryId.stringify)),
      $set("thumbnail_id" -> thumbnailId.stringify), false, false, WriteConcern.Safe)
  }
  
  
}

object TempFileDAO extends ModelCompanion[TempFile, ObjectId] {
  val COLLECTION = "uploadquery"
  val mongos: MongoStartup = DI.injector.getInstance(classOf[MongoStartup])
  val dao = new SalatDAO[TempFile, ObjectId](collection = mongos.collection(COLLECTION)) {}
}

