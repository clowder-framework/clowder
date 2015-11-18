package services.mongodb

import java.net.URI
import javax.inject.{Inject, Singleton}

import com.novus.salat.dao.{SalatDAO, ModelCompanion}
import models._
import org.bson.types.ObjectId
import play.api.Play._
import MongoContext.context
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

  def addMetaData(id: UUID, metadata: Metadata) {
    Logger.debug("Adding/modifying user metadata to curation " + id + " : " + metadata.content)
    val m = CurationObjectMetadata(id, metadata)
    CurationMetadataDAO.insert(m)
  }

  def removeMetadataByCuration(id:UUID): Unit = {
    // in this method, we don't check curationservice.get(id).isDefine, and we don't recommand to do so, since the
    // curation object may already delete but the metadata is still in DB.
    CurationMetadataDAO.remove(MongoDBObject("curationObject" ->new ObjectId(id.stringify)))
  }


  def getMeatadateByCuration(id:UUID): List[CurationObjectMetadata] = {
    // in this method, we don't check curationservice.get(id).isDefine, and we don't recommand to do so, since the
    // curation object may already delete but the metadata is still in DB.
    CurationMetadataDAO.find(MongoDBObject("curationObject" ->new ObjectId(id.stringify))).toList
  }

  def updateRepository(curationId: UUID, repository: String): Unit = {
    CurationDAO.update(MongoDBObject("_id" -> new ObjectId(curationId.stringify)), $set("repository" -> repository),
      false, false, WriteConcern.Safe)
  }

  def updateExternalIdentifier(curationId: UUID, externalIdentifier: URI): Unit = {
    CurationDAO.update(MongoDBObject("_id" -> new ObjectId(curationId.stringify)), $set("externalIdentifier" -> externalIdentifier),
      false, false, WriteConcern.Safe)
  }


  def getCurationObjectByDatasetId(datasetId: UUID): List[CurationObject] = {
    CurationDAO.find(MongoDBObject("datasets" -> MongoDBObject("$elemMatch" -> MongoDBObject("_id" -> new ObjectId(datasetId.stringify))))).toList
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


/**
 * Salat CurationObjectMetadata model companion.
 */
object CurationMetadataDAO extends ModelCompanion[CurationObjectMetadata, ObjectId] {
  val dao = current.plugin[MongoSalatPlugin] match {
    case None => throw new RuntimeException("No MongoSalatPlugin");
    case Some(x) => new SalatDAO[CurationObjectMetadata, ObjectId](collection = x.collection("curationObjects.metadata")) {}
  }
}
