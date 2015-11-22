package services.mongodb

import java.net.URI
import javax.inject.{Inject, Singleton}

import com.novus.salat.dao.{SalatDAO, ModelCompanion}
import models._
import org.bson.types.ObjectId
import play.api.Play._
import MongoContext.context
import services.{MetadataService, CurationService, SpaceService}
import util.Direction._
import java.util.Date
import play.api.Logger
import com.mongodb.casbah.commons.MongoDBObject
import com.mongodb.casbah.WriteConcern
import com.mongodb.casbah.Imports._


@Singleton
class MongoDBCurationService  @Inject() (metadatas: MetadataService, spaces: SpaceService)  extends CurationService {

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
        metadatas.removeMetadataByAttachTo(ResourceRef(ResourceRef.curationObject, c.id))
        c.files.map(f => metadatas.removeMetadataByAttachTo(ResourceRef(ResourceRef.curationFile, f)))
        spaces.removeCurationObject(c.space, c.id)
        c.files.map(f => CurationFileDAO.remove(MongoDBObject("_id" ->new ObjectId(f.stringify))))
        CurationDAO.remove(MongoDBObject("_id" ->new ObjectId(id.stringify)))
      }
      case None =>
    }
  }

  def insertFile(cf: CurationFile) ={
    CurationFileDAO.insert(cf)
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

  def getCurationFiles(cfs:List[UUID]): List[CurationFile] ={
    (for (cf <- cfs) yield CurationFileDAO.findOneById(new ObjectId(cf.stringify))).flatten.toList
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
object CurationFileDAO extends ModelCompanion[CurationFile, ObjectId] {
  val dao = current.plugin[MongoSalatPlugin] match {
    case None => throw new RuntimeException("No MongoSalatPlugin");
    case Some(x) => new SalatDAO[CurationFile, ObjectId](collection = x.collection("curationFiles")) {}
  }
}
