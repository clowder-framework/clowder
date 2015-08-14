package services.mongodb

import javax.inject.{Inject, Singleton}

import com.novus.salat.dao.{SalatDAO, ModelCompanion}
import models.{CurationObj, ProjectSpace, SpaceInvite}
import org.bson.types.ObjectId
import play.api.Play._
import MongoContext.context
import models.{User, UUID, Collection, Dataset}
import services.CurationService
import util.Direction._
import java.util.Date
import play.api.Logger
import com.mongodb.casbah.commons.MongoDBObject
import com.mongodb.casbah.Imports._


@Singleton
class MongoDBCurationService  @Inject()  extends CurationService {

  def insert(curation: CurationObj) = {
    if (CurationDAO != null) {
      //CurationDAO.save(curation)
      Logger.debug("insert a new CO with ID: " + curation.id)
      CurationDAO.insert(curation)
    }
  }

  def get(id: UUID): Option[CurationObj]  = {
    CurationDAO.findOneById(new ObjectId(id.stringify))
  }

}

/**
 * Salat CurationObj model companion.
 */
object CurationDAO extends ModelCompanion[CurationObj, ObjectId] {
  val dao = current.plugin[MongoSalatPlugin] match {
    case None => throw new RuntimeException("No MongoSalatPlugin");
    case Some(x) => new SalatDAO[CurationObj, ObjectId](collection = x.collection("curationObjs")) {}
  }
}