package services.mongodb

import javax.inject.{Inject, Singleton}

import com.novus.salat.dao.{SalatDAO, ModelCompanion}
import models.{CurationObject, ProjectSpace, SpaceInvite}
import org.bson.types.ObjectId
import play.api.Play._
import MongoContext.context
import models.{User, UUID, Collection, Dataset}
import services.{CurationService, SpaceService}
import util.Direction._
import java.util.Date
import play.api.Logger
import com.mongodb.casbah.commons.MongoDBObject
import com.mongodb.casbah.Imports._


@Singleton
class MongoDBCurationService  @Inject() (spaces: SpaceService)  extends CurationService {

  def insert(curation: CurationObject) = {
    if (CurationDAO != null) {
      //CurationDAO.save(curation)
      Logger.debug("insert a new CO with ID: " + curation.id)
      CurationDAO.insert(curation)
      spaces.addCurationObject(curation.space, curation.id)
    }
  }

  def get(id: UUID): Option[CurationObject]  = {
    CurationDAO.findOneById(new ObjectId(id.stringify))
  }

  def remove(id: UUID): Unit = {
    val curation = get(id)
    curation match {
      case Some(c) => {spaces.removeCurationObject(c.space, c.id)
        CurationDAO.remove(MongoDBObject("_id" ->new ObjectId(id.stringify)))
      }
      case None =>
    }
  }

}

/**
 * Salat CurationObject model companion.
 */
object CurationDAO extends ModelCompanion[CurationObject, ObjectId] {
  val dao = current.plugin[MongoSalatPlugin] match {
    case None => throw new RuntimeException("No MongoSalatPlugin");
    case Some(x) => new SalatDAO[CurationObject, ObjectId](collection = x.collection("curationObjects")) {}
  }
}