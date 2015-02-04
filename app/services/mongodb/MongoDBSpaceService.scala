package services.mongodb

import javax.inject.{Inject, Singleton}

import com.mongodb.casbah.Imports._
import com.mongodb.casbah.commons.MongoDBObject
import com.mongodb.util.JSON;
import com.mongodb.DBObject;
import com.novus.salat.dao.{SalatDAO, ModelCompanion}
import models.{UserSpace, ProjectSpace, UUID}
import play.api.Play._
import services._
import MongoContext.context
import util.Direction._

/**
 * Store Spaces in MongoDB.
 *
 * @author Luigi Marini
 *
 */
@Singleton
class MongoDBSpaceService @Inject() (
  collections: CollectionService,
  files: FileService,
  datasets: DatasetService) extends SpaceService {

  def get(id: UUID): Option[ProjectSpace] = {
    ProjectSpaceDAO.findOneById(new ObjectId(id.stringify))
  }

  def insert(dataset: ProjectSpace): Option[String] = {
    ProjectSpaceDAO.insert(dataset).map(_.toString)
  }

  def update(space: ProjectSpace): Unit = {
    ProjectSpaceDAO.save(space)
  }

  def delete(id: UUID): Unit = {
    ProjectSpaceDAO.removeById(new ObjectId(id.stringify))
  }

  def list(): List[ProjectSpace] = {
    (for (space <- ProjectSpaceDAO.find(MongoDBObject())) yield space).toList
  }

  /**
   * The number of objects that are available based on the filter
   */
  override def count(filter: Option[String]): Long = {
    val filterBy = filter.fold(MongoDBObject())(JSON.parse(_).asInstanceOf[DBObject])
    ProjectSpaceDAO.count(filterBy)
  }

  /**
   * Return a list objects that are available based on the filter as well as the other options.
   *
   * @param order the key to use to order the data, default is natural ordering of underlying implementation
   * @param direction the direction to order the data in
   * @param start the first element that should be returned based on the order key
   * @param limit the maximum number of elements to return
   * @param filter is a json representation of the filter to be applied
   */
  override def list(order: Option[String], direction: Direction, start: Option[String], limit: Integer,
                    filter: Option[String]): List[ProjectSpace] = {
    val startAt = (order, start) match {
      case (Some(o), Some(s)) => {
        direction match {
          case ASC => (o $gte s)
          case DESC => (o $lte s)
        }
      }
      case (_, _) => MongoDBObject()
    }
    // what happens if we sort by user, and a user has uploaded 100 items?
    // how do we know that we need to show page 3 of that user?
    // TODO always sort by date ascending, start is based on user/start combo
    val filterBy = filter.fold(MongoDBObject())(JSON.parse(_).asInstanceOf[DBObject])
    val raw = ProjectSpaceDAO.find(startAt ++ filterBy)
    val orderedBy = order match {
      case Some(o) => {
        direction match {
          case ASC => raw.sort(MongoDBObject(o -> 1))
          case DESC => raw.sort(MongoDBObject(o -> -1))
        }
      }
      case None => raw
    }
    orderedBy.limit(limit).toList
  }

  /**
   * Salat ProjectSpace model companion.
   */
  object ProjectSpaceDAO extends ModelCompanion[ProjectSpace, ObjectId] {
    val dao = current.plugin[MongoSalatPlugin] match {
      case None => throw new RuntimeException("No MongoSalatPlugin");
      case Some(x) => new SalatDAO[ProjectSpace, ObjectId](collection = x.collection("spaces.projects")) {}
    }
  }

  /**
   * Salat UserSpace model companion.
   */
  object UserSpaceDAO extends ModelCompanion[UserSpace, ObjectId] {
    val dao = current.plugin[MongoSalatPlugin] match {
      case None => throw new RuntimeException("No MongoSalatPlugin");
      case Some(x) => new SalatDAO[UserSpace, ObjectId](collection = x.collection("spaces.users")) {}
    }
  }
}

