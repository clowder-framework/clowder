package services.mongodb

import com.mongodb.casbah.commons.MongoDBObject
import com.mongodb.casbah.Imports._

import services.TempFileService
import models.{UUID, TempFile}
import javax.inject.Singleton
import org.bson.types.ObjectId
import com.novus.salat.dao.{ModelCompanion, SalatDAO}
import MongoContext.context
import play.api.Play.current


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
  val dao = current.plugin[MongoSalatPlugin] match {
    case None => throw new RuntimeException("No MongoSalatPlugin");
    case Some(x) => new SalatDAO[TempFile, ObjectId](collection = x.collection("uploadquery")) {}
  }
}

