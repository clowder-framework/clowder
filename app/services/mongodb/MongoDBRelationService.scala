package services.mongodb

import com.mongodb.casbah.commons.MongoDBObject
import models.{Relation, ResourceType, UUID}
import org.bson.types.ObjectId
import salat.dao.{ModelCompanion, SalatDAO}
import services.mongodb.MongoContext.context
import services.{DI, RelationService}

/**
 * Track relationships between resources
 */
class MongoDBRelationService extends RelationService {

  def list(): List[Relation] = {
    RelationDAO.findAll().toList
  }

  def get(id: UUID): Option[Relation] = {
    RelationDAO.findOneById(new ObjectId(id.stringify))
  }

  def add(relation: Relation): Option[UUID] = {

    // check that one doesn't exist already
    val existing = RelationDAO.find(
      MongoDBObject(
        "source._id" -> relation.source.id,
        "source.resourceType" -> relation.source.resourceType.toString,
        "target._id" -> relation.target.id,
        "target.resourceType" -> relation.target.resourceType.toString
      )).toList

    if (existing.size == 0) {
      RelationDAO.insert(relation) match {
        case Some(id) => Some(UUID(id.toString))
        case None => None
      }
    } else None
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

  def findRelationships(sourceId: String, sourceType: ResourceType.Value, targetType: ResourceType.Value): List[Relation] = {
    RelationDAO.find(
      MongoDBObject("source._id" -> sourceId,
        "source.resourceType" -> sourceType.toString, "target.resourceType" -> targetType.toString
      )).toList
  }

  object RelationDAO extends ModelCompanion[Relation, ObjectId] {
    val COLLECTION = "relations"
    val mongos: MongoStartup = DI.injector.getInstance(classOf[MongoStartup])
    val dao = new SalatDAO[Relation, ObjectId](collection = mongos.collection(COLLECTION)) {}
  }
}
