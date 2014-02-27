package services.mongodb

import services.TempFileService
import models.{MongoContext, TempFile}
import javax.inject.Singleton
import org.bson.types.ObjectId
import com.novus.salat.dao.{ModelCompanion, SalatDAO}
import MongoContext.context
import play.api.Play.current

/**
 * Created by lmarini on 2/24/14.
 */
@Singleton
class MongoDBTempFileService extends TempFileService {

  def get(query_id: String): Option[TempFile] = {
    TempFileDAO.findOneById(new ObjectId(query_id))
  }
}

object TempFileDAO extends ModelCompanion[TempFile, ObjectId] {
  val dao = current.plugin[MongoSalatPlugin] match {
    case None => throw new RuntimeException("No MongoSalatPlugin");
    case Some(x) => new SalatDAO[TempFile, ObjectId](collection = x.collection("uploadquery.files")) {}
  }
}

