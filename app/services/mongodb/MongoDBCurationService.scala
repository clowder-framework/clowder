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
import com.mongodb.casbah.WriteConcern
import com.mongodb.casbah.Imports._


@Singleton
class MongoDBCurationService  @Inject() (spaces: SpaceService)  extends CurationService {

  def insert(curation: CurationObject) = {

      //CurationDAO.save(curation)
      Logger.debug("insert a new CO with ID: " + curation.id)
      CurationDAO.insert(curation)
      spaces.addCurationObject(curation.space, curation.id)
  }

  def get(id: UUID): Option[CurationObject]  = {
    CurationDAO.findOneById(new ObjectId(id.stringify))
  }

  def updateStatus(id: UUID, status: String) {
    CurationDAO.update(MongoDBObject("_id" -> new ObjectId(id.stringify)), $set("status" -> status), false, false, WriteConcern.Safe)
  }

  def setSubmitted(id: UUID) {
    CurationDAO.update(MongoDBObject("_id" -> new ObjectId(id.stringify)), $set("status" -> "Submitted", "submittedDate" -> Some(new Date())), false, false, WriteConcern.Safe)
  }

  def setPublished(id: UUID) {
    CurationDAO.update(MongoDBObject("_id" -> new ObjectId(id.stringify)), $set("status" -> "Published", "publishedDate" -> Some(new Date())), false, false, WriteConcern.Safe)
  }

  def remove(id: UUID): Unit = {
    val curation = get(id)
    curation match {
      case Some(c) => {
        spaces.removeCurationObject(c.space, c.id)
        CurationDAO.remove(MongoDBObject("_id" ->new ObjectId(id.stringify)))
      }
      case None =>
    }
  }

  def addDatasetUserMetaData(id: UUID, json: String) {
    Logger.debug("Adding/modifying user metadata to curation " + id + " : " + json)
    val md = com.mongodb.util.JSON.parse(json).asInstanceOf[DBObject]
    CurationDAO.update(MongoDBObject("_id" -> new ObjectId(id.stringify)), $set("datasets.0.userMetadata" -> md),
      false, false, WriteConcern.Safe)
  }


  def addFileUserMetaData(curationId: UUID, file: Int, json: String) {
    val md = com.mongodb.util.JSON.parse(json).asInstanceOf[DBObject]
    CurationDAO.update(MongoDBObject("_id" -> new ObjectId(curationId.stringify)), $set("files."+file+".userMetadata" -> md),
      false, false, WriteConcern.Safe)

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