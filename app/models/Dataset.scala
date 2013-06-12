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
import scala.Mutable
import collection.JavaConverters._
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
  metadata: List[Map[String, Any]] = List.empty,
  userMetadata: Map[String, Any] = Map.empty,
  comments: List[Comment] = List.empty
)

object MustBreak extends Exception { }

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
        val returnedMetadata = x.getAs[MongoDBList]("metadata").get.toList map {m => m.asInstanceOf[Map[String,Any]]}
//        metadata map { m => m.asInstanceOf[DBObject].asInstanceOf[Map[String,Any]]}
//        x.asInstanceOf[List[Map[String,Any]]]
        returnedMetadata
//    	  List.empty[Map[String,Any]]
      }
    }
  }
  def getUserMetadata(id: String): scala.collection.mutable.Map[String,Any] = {
    dao.collection.findOne(MongoDBObject("_id" -> new ObjectId(id)), MongoDBObject("userMetadata"->1)) match {
      case None => new scala.collection.mutable.HashMap[String,Any]
      case Some(x) => {
    	val returnedMetadata = x.getAs[DBObject]("userMetadata").get.toMap.asScala.asInstanceOf[scala.collection.mutable.Map[String,Any]]
		returnedMetadata
      }
    }
  }
  
  def addMetadata(id: String, json: String) {
    Logger.debug("Adding metadata to dataset " + id + " : " + json)
    val md = com.mongodb.util.JSON.parse(json).asInstanceOf[DBObject]
    dao.update(MongoDBObject("_id" -> new ObjectId(id)), $addToSet("metadata" -> md), false, false, WriteConcern.Safe)
  }

  def addUserMetadata(id: String, json: String) {
    Logger.debug("Adding/modifying user metadata to dataset " + id + " : " + json)
    val md = com.mongodb.util.JSON.parse(json).asInstanceOf[DBObject]
    dao.update(MongoDBObject("_id" -> new ObjectId(id)), $set(Seq("userMetadata" -> md)), false, false, WriteConcern.Safe)
  }

  def tag(id: String, tag: String) { 
    dao.collection.update(MongoDBObject("_id" -> new ObjectId(id)),  $addToSet("tags" -> tag), false, false, WriteConcern.Safe)
  }

  def comment(id: String, comment: Comment) {
    dao.update(MongoDBObject("_id" -> new ObjectId(id)), $addToSet("comments" -> Comment.toDBObject(comment)), false, false, WriteConcern.Safe)
  }
  
  /**
   * Check recursively whether a dataset's user-input metadata match a requested search tree. 
   */
  def searchUserMetadata(id: String, requestedMetadataQuery: Any): Boolean = {
    return searchMetadata(id, requestedMetadataQuery.asInstanceOf[scala.collection.immutable.Map[String,Any]], getUserMetadata(id))
  }
  
  /**
   * Check recursively whether a (sub)tree of a dataset's metadata matches a requested search subtree. 
   */
  def searchMetadata(id: String, requestedMap: scala.collection.immutable.Map[String,Any], currentMap: scala.collection.mutable.Map[String,Any]): Boolean = {
      var allMatch = true
      for((reqKey, reqValue) <- requestedMap){
        val reqKeyCompare = reqKey.replaceAll("__[0-9]*","")
        var matchFound = false
        try{
        	for((currKey, currValue) <- currentMap){
        	    val currKeyCompare = currKey.replaceAll("__[0-9]*","")
        		if(reqKeyCompare.equals(currKeyCompare)){
        		  //If search subtree remaining is a string (ie we have reached a leaf), then remaining subtree currently examined is bound to be a string, as the path so far was the same.
        		  //Therefore, we do string comparison.
        		  if(reqValue.isInstanceOf[String]){
        			  if(reqValue.asInstanceOf[String].equalsIgnoreCase(currValue.asInstanceOf[String])){
        				  matchFound = true
        				  throw MustBreak
        			  }
        		  }
        		  //If search subtree remaining is not a string (ie we haven't reached a leaf yet), then remaining subtree currently examined is bound to not be a string, as the path so far was the same.
        		  //Therefore, we do maps (actually subtrees) comparison.
        		  else{
        		      val currValueMap = currValue.asInstanceOf[com.mongodb.BasicDBObject].toMap().asScala.asInstanceOf[scala.collection.mutable.Map[String,Any]]
        			  if(searchMetadata(id, reqValue.asInstanceOf[scala.collection.immutable.Map[String,Any]], currValueMap)){
        				  matchFound = true
        				  throw MustBreak
        			  }
        		  }	
        		}
        	}
        } catch {case MustBreak => }        
        if(! matchFound)
          return false        
      }     
      return true;              
  }
  
  
  
}
