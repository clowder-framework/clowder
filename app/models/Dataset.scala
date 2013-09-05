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
import scala.collection.JavaConversions._
import play.api.libs.json.JsValue
import securesocial.core.Identity
import services.Services
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
  author: Identity,
  description: String = "N/A",
  created: Date, 
  files: List[File] = List.empty,
  streams_id: List[ObjectId] = List.empty,
  tags: List[String] = List.empty,
  metadata: Map[String, Any] = Map.empty,
  userMetadata: Map[String, Any] = Map.empty,
  comments: List[Comment] = List.empty,
  collections: List[String] = List.empty
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
  
  def getMetadata(id: String): Map[String,Any] = {
    dao.collection.findOne(MongoDBObject("_id" -> new ObjectId(id)), MongoDBObject("metadata"->1)) match {
      case None => Map.empty
      case Some(x) => {
//        x.getAs[DBObject]("metadata") match {
//          case Some(map) => map.toMap.asScala.asInstanceOf[Map[String,Any]]
//          case None => Map.empty
//        }
        x.getAs[DBObject]("metadata").get.toMap.asScala.asInstanceOf[scala.collection.mutable.Map[String,Any]].toMap
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
    dao.collection.findOne(MongoDBObject("_id" -> new ObjectId(id)), MongoDBObject("metadata"->1)) match {
      case None => {
        dao.update(MongoDBObject("_id" -> new ObjectId(id)), $set("metadata" -> md), false, false, WriteConcern.Safe)
      }
      case Some(x) => {
        x.getAs[DBObject]("metadata") match {
          case Some(map) => {
            val union = map.asInstanceOf[DBObject] ++ md
            dao.update(MongoDBObject("_id" -> new ObjectId(id)), $set("metadata" -> union), false, false, WriteConcern.Safe)
          }
          case None => Map.empty
        }
      }
    }
    
    
   
  
  }

  def addUserMetadata(id: String, json: String) {
    Logger.debug("Adding/modifying user metadata to dataset " + id + " : " + json)
    val md = com.mongodb.util.JSON.parse(json).asInstanceOf[DBObject]
    dao.update(MongoDBObject("_id" -> new ObjectId(id)), $set("userMetadata" -> md), false, false, WriteConcern.Safe)
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
    return searchMetadata(id, requestedMetadataQuery.asInstanceOf[java.util.LinkedHashMap[String,Any]], getUserMetadata(id))
  }
  
  /**
   * Check recursively whether a (sub)tree of a dataset's metadata matches a requested search subtree. 
   */
  def searchMetadata(id: String, requestedMap: java.util.LinkedHashMap[String,Any], currentMap: scala.collection.mutable.Map[String,Any]): Boolean = {
      var allMatch = true
      Logger.debug("req: "+ requestedMap);
      Logger.debug("curr: "+ currentMap);
      for((reqKey, reqValue) <- requestedMap){
        var reqKeyCompare = reqKey.replaceAll("__[0-9]*$","")
        if(reqKeyCompare.equals("OR")){
          if(allMatch)
            return true          
          else
        	allMatch = true          
        }
        else{
          if(allMatch){
	        var isNot = false
	        if(reqKeyCompare.endsWith("__not")){
	          isNot = true
	          reqKeyCompare = reqKeyCompare.dropRight(5)
	        }
	        var matchFound = false
	        try{
	        	for((currKey, currValue) <- currentMap){
	        	    val currKeyCompare = currKey.replaceAll("__[0-9]*$","")
	        		if(reqKeyCompare.equals(currKeyCompare)){
	        		  //If search subtree remaining is a string (ie we have reached a leaf), then remaining subtree currently examined is bound to be a string, as the path so far was the same.
	        		  //Therefore, we do string comparison.
	        		  if(reqValue.isInstanceOf[String]){
	        			  if(reqValue.asInstanceOf[String].trim().equals("*") || reqValue.asInstanceOf[String].trim().equalsIgnoreCase(currValue.asInstanceOf[String].trim())){
	        				  matchFound = true
	        				  throw MustBreak
	        			  }
	        		  }
	        		  //If search subtree remaining is not a string (ie we haven't reached a leaf yet), then remaining subtree currently examined is bound to not be a string, as the path so far was the same.
	        		  //Therefore, we do maps (actually subtrees) comparison.
	        		  else{
	        		      val currValueMap = currValue.asInstanceOf[com.mongodb.BasicDBObject].toMap().asScala.asInstanceOf[scala.collection.mutable.Map[String,Any]]
	        			  if(searchMetadata(id, reqValue.asInstanceOf[java.util.LinkedHashMap[String,Any]], currValueMap)){
	        				  matchFound = true
	        				  throw MustBreak
	        			  }
	        		  }	
	        		}
	        	}
	        } catch {case MustBreak => }
	        if(isNot)
	          matchFound = !matchFound
	        if(! matchFound)
	          allMatch = false  
	    }      
      }     
    }
    return allMatch              
  }
  
//  def get(id: String): Option[Dataset] = {
//    dao.findOneById(new ObjectId(id)) match {
//      case Some(dataset) => {
//        val files = FileDAO.findByFileId(file.id)
//        val sectionsWithPreviews = sections.map { s =>
//          val p = PreviewDAO.findOne(MongoDBObject("section_id"->s.id))
//          s.copy(preview = p)
//        }
//        Some(file.copy(sections = sectionsWithPreviews, previews = previews))
//      }
//      case None => None
//    }
//  }
  
      /**
   * List all datasets inside a collection.
   */
  def listInsideCollection(collectionId: String) : List[Dataset] =  { 
      Collection.findOneById(new ObjectId(collectionId)) match{
        case Some(collection) => {
          val list = for (dataset <- Services.datasets.listDatasetsChronoReverse; if(isInCollection(dataset,collection))) yield dataset
          return list
        }
        case None =>{
          return List.empty	 	  
        } 
      }
  } 
  def isInCollection(dataset: Dataset, collection: Collection): Boolean = {
    for(collDataset <- collection.datasets){
      if(collDataset.id == dataset.id)
        return true
    }
    return false
  }
  
  def addFile(datasetId:String, file: File){   
    Dataset.update(MongoDBObject("_id" -> new ObjectId(datasetId)), $addToSet("files" ->  FileDAO.toDBObject(file)), false, false, WriteConcern.Safe)   
  }
  
  def addCollection(datasetId:String, collectionId: String){   
    Dataset.update(MongoDBObject("_id" -> new ObjectId(datasetId)), $addToSet("collections" ->  collectionId), false, false, WriteConcern.Safe)   
  }
  def removeCollection(datasetId:String, collectionId: String){   
    Dataset.update(MongoDBObject("_id" -> new ObjectId(datasetId)), $pull("collections" ->  collectionId), false, false, WriteConcern.Safe)   
  }
  
}
