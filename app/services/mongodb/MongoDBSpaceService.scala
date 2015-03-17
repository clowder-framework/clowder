package services.mongodb

import java.text.SimpleDateFormat
import java.util.Date
import javax.inject.{Inject, Singleton}

import com.mongodb.casbah.Imports._
import com.mongodb.casbah.commons.MongoDBObject
import com.mongodb.util.JSON;
import com.mongodb.DBObject;
import com.novus.salat.dao.{SalatDAO, ModelCompanion}
import models.{UserSpace, ProjectSpace, UUID}
import play.{Logger => log}
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
    val sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss")

    // Create sort object
    val d = if (direction == ASC) 1 else -1
    val o = order.getOrElse("created")
    val orderedBy = if (o == "created") {
      MongoDBObject(o -> d)
    } else {
      MongoDBObject(o -> d) ++ MongoDBObject("created" -> 1)
    }

    // set start and filter the data
    (start, filter) match {
      case (Some(d), Some(f)) => {
        val since = "created" $gte sdf.parse(d)
        val filter = JSON.parse(f).asInstanceOf[DBObject]
        ProjectSpaceDAO.find(since ++ filter).sort(orderedBy).limit(limit).toList
      }
      case (Some(d), None) => {
        val since = "created" $gte sdf.parse(d)
        ProjectSpaceDAO.find(since).sort(orderedBy).limit(limit).toList
      }
      case (None, Some(f)) => {
        val filter = JSON.parse(f).asInstanceOf[DBObject]
        ProjectSpaceDAO.find(filter).sort(orderedBy).limit(limit).toList
      }
      case (None, None) => {
        ProjectSpaceDAO.findAll().sort(orderedBy).limit(limit).toList
      }
    }
  }

  override def getNext(order: Option[String], direction: Direction, start: Date, limit: Integer,
                       filter: Option[String]): Option[String] = {
    val sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss")

    // Create sort object
    val d = if (direction == ASC) 1 else -1
    val o = order.getOrElse("created")
    val orderedBy = if (o == "created") {
      MongoDBObject(o -> d)
    } else {
      MongoDBObject(o -> d) ++ MongoDBObject("created" -> 1)
    }

    // set start and filter the data
    val since = "created" $gt start
    val x = filter match {
      case Some(f) => {
        val filter = JSON.parse(f).asInstanceOf[DBObject]
        ProjectSpaceDAO.find(since ++ filter).sort(orderedBy).limit(1).toList
      }
      case None => {
        ProjectSpaceDAO.find(since).sort(orderedBy).limit(1).toList
      }
    }

    x.headOption.map(x => sdf.format(x.created))
  }

  override def getPrev(order: Option[String], direction: Direction, start: Date, limit: Integer,
                       filter: Option[String]): Option[String] = {
    val sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss")

    // Create sort object
    val d = if (direction == ASC) -1 else 1
    val o = order.getOrElse("created")
    val orderedBy = if (o == "created") {
      MongoDBObject(o -> d)
    } else {
      MongoDBObject(o -> d) ++ MongoDBObject("created" -> -1)
    }

    // set start and filter the data
    val since = "created" $lt start
    val x = filter match {
      case Some(f) => {
        val filter = JSON.parse(f).asInstanceOf[DBObject]
        ProjectSpaceDAO.find(since ++ filter).sort(orderedBy).limit(limit).toList
      }
      case None => {
        ProjectSpaceDAO.find(since).sort(orderedBy).limit(limit).toList
      }
    }

    x.lastOption.map(x => sdf.format(x.created))
  }

  /**
   * Associate a collection with a space
   *
   * @param collection collection id
   * @param space space id
   */
  def addCollection(collection: UUID, space: UUID): Unit = {
    log.debug(s"Adding $collection to $space")
    collections.addToSpace(collection, space)
  }

  /**
   * Associate a dataset with a space
   *
   * @param collection dataset id
   * @param space space id
   */
  def addDataset(dataset: UUID, space: UUID): Unit = {
    log.debug(s"Adding $dataset to $space")
    datasets.addToSpace(dataset, space)
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

