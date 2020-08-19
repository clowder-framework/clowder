package services.mongodb

import com.mongodb.casbah.commons.MongoDBObject
import com.novus.salat.dao.{ModelCompanion, SalatDAO}
import javax.inject.{Inject, Singleton}
import models._
import org.bson.types.ObjectId
import play.api.Play.current
import services._
import services.mongodb.MongoContext.context

/**
 * MongoDB Metadata Service Implementation
 */
@Singleton
class MongoDBMetadataGroupService @Inject() () extends MetadataGroupService {



  def save(mdGroup: MetadataGroup): Option[String] = {
    MetadataGroupDAO.insert(mdGroup).map(_.toString)
  }

  def delete(mdGroupId: UUID): Unit = ???

  def get(id: UUID): Option[MetadataGroup] = {
    val group = MetadataGroupDAO.findOneById(new ObjectId(id.stringify))
    group
  }

  def addToSpace(mdGroup: MetadataGroup, spaceId: UUID): Unit = ???

  def removeFromSpace(mdGroup: MetadataGroup, spaceId: UUID): Unit = ???

  def attachToFile(mdGroup: MetadataGroup, fileId: UUID): Unit = ???

  def attachToDatast(mdGroup: MetadataGroup, fileId: UUID): Unit = ???

  def getAttachedToFile(fileId: UUID): MetadataGroup = ???

  def getAttachedToDataset(datasetId: UUID): MetadataGroup = ???

  def list(userId: UUID): List[MetadataGroup] = {
    var mdGroups = List.empty[MetadataGroup]
    mdGroups = MetadataGroupDAO.find(MongoDBObject()).toList
    mdGroups
  }

  def listSpace(spaceId: UUID) : List[MetadataGroup] = {
    var mdGroups = List.empty[MetadataGroup]
    val filter = MongoDBObject("spaces" -> new ObjectId(spaceId.stringify))
    mdGroups = MetadataGroupDAO.find(filter).toList
    mdGroups
  }
}


object MetadataGroupDAO extends ModelCompanion[MetadataGroup, ObjectId] {
  val dao = current.plugin[MongoSalatPlugin] match {
    case None => throw new RuntimeException("No MongoSalatPlugin")
    case Some(x) => new SalatDAO[MetadataGroup, ObjectId](collection = x.collection("metadatagroup")) {}
  }
}

