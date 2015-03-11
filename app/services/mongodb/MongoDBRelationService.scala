package services.mongodb

import models.{ResourceType, UUID, Relation}
import org.bson.types.ObjectId
import services.RelationService

import com.novus.salat.dao.{ModelCompanion, SalatDAO}
import MongoContext.context
import play.api.Play.current
import play.Logger
import com.mongodb.casbah.commons.MongoDBObject
import com.mongodb.casbah.Imports._

/**
 * Track relationships between resources
 *
 * @author Luigi Marini
 *
 */
class MongoDBRelationService extends RelationService {

  def list(): List[Relation] = {
    RelationDAO.findAll().toList
  }

  def get(id: UUID): Option[Relation] = {
    RelationDAO.findOneById(new ObjectId(id.stringify))
  }

  def add(relation: Relation): Option[UUID] = {
    RelationDAO.insert(relation) match {
      case Some(id) =>  Some(UUID(id.toString))
      case None => None
    }
  }

  def delete(id: UUID): Unit = {
    RelationDAO.removeById(new ObjectId(id.stringify))
  }

  def findTargets(sourceId: String, sourceType: ResourceType.Value, targetType: ResourceType.Value): List[String] = {
    RelationDAO.find(
      MongoDBObject("source._id" -> sourceId,
        "source.resourceType" -> sourceType.toString, "target.resourceType" -> targetType.toString
      )).toList.map(_.target.id)
  }

  object RelationDAO extends ModelCompanion[Relation, ObjectId] {
    val dao = current.plugin[MongoSalatPlugin] match {
      case None => throw new RuntimeException("No MongoSalatPlugin");
      case Some(x) => new SalatDAO[Relation, ObjectId](collection = x.collection("relations")) {}
    }
  }
}
