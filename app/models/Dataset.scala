/**
 *
 */
package models

import org.bson.types.ObjectId
import com.mongodb.casbah.Imports._
import com.novus.salat.dao.{ModelCompanion, SalatDAO}
import MongoContext.context
import play.api.Play.current
import services.MongoSalatPlugin
import java.util.Date
import com.mongodb.casbah.commons.MongoDBObject
import com.mongodb.casbah.WriteConcern
import play.api.libs.json.Json
import play.api.Logger

/**
 * A dataset is a collection of files, and streams.
 * 
 * 
 * @author Luigi Marini
 *
 */
case class Dataset (
  id: ObjectId = new ObjectId,
  name: String = "N/A",
  description: String = "N/A",
  created: Date, 
  files: List[File] = List.empty,
  streams_id: List[ObjectId] = List.empty,
  tags: List[String] = List.empty,
  metadata: Map[String, Any] = Map.empty
)

object Dataset extends ModelCompanion[Dataset, ObjectId] {
  // TODO RK handle exception for instance if we switch to other DB
  val dao = current.plugin[MongoSalatPlugin] match {
    case None    => throw new RuntimeException("No MongoSalatPlugin");
    case Some(x) =>  new SalatDAO[Dataset, ObjectId](collection = x.collection("datasets")) {}
  }
  
  def findOneByFileId(file_id: ObjectId): Option[Dataset] = {
    dao.findOne(MongoDBObject("files._id" -> file_id))
  }
  
  def findByTag(tag: String): List[Dataset] = {
    dao.find(MongoDBObject("tags" -> tag)).toList
  }
  
  def getMetadata(id: String): List[Map[String,Any]] = {
    dao.collection.findOne(MongoDBObject("_id" -> new ObjectId(id)), MongoDBObject("metadata"->1)) match {
      case None => List.empty
      case Some(x) => {
//        val metadata = x.getAs[MongoDBList]("metadata").get.toList map {m => m.asInstanceOf[Map[String,Any]]}
//        metadata map { m => m.asInstanceOf[DBObject].asInstanceOf[Map[String,Any]]}
//        x.asInstanceOf[List[Map[String,Any]]]
//        metadata
        List.empty[Map[String,Any]]
      }
    }
  }
  
  def addMetadata(id: String, json: String) {
    Logger.debug("Adding metadata to dataset " + id + " : " + json)
    val md = com.mongodb.util.JSON.parse(json).asInstanceOf[DBObject]
    dao.update(MongoDBObject("_id" -> new ObjectId(id)), $addToSet("metadata" -> md), false, false, WriteConcern.Safe)
  }
  
  def tag(id: String, tag: String) { 
    dao.collection.update(
          MongoDBObject("_id" -> new ObjectId(id)), 
          	$addToSet("tags" -> tag), false, false, WriteConcern.Safe)
  }
}
