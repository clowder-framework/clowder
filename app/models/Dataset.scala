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
import jsonutils.JsonUtil
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
  tags: List[Tag] = List.empty,
  metadata: Map[String, Any] = Map.empty,
  userMetadata: Map[String, Any] = Map.empty,
  collections: List[String] = List.empty,
  thumbnail_id: Option[String] = None,
  datasetXmlMetadata: List[DatasetXMLMetadata] = List.empty
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
  
  def findByFileId(file_id: ObjectId): List[Dataset] = {
    dao.find(MongoDBObject("files._id" -> file_id)).toList
  }
  
  def findNotContainingFile(file_id: ObjectId): List[Dataset] = {
        val listContaining = findByFileId(file_id)
        (for (dataset <- Dataset.find(MongoDBObject())) yield dataset).toList.filterNot(listContaining.toSet)
  }
  
//  def executeRawQuery(theQuery: String): List[Dataset] = {
//    val thePlugin = current.plugin[MongoSalatPlugin]
//    if(thePlugin.isEmpty){
//      throw new RuntimeException("No MongoSalatPlugin")
//     }
//    val executionResult = thePlugin.get.source("medici")
//    
//    
//  }
  
  
  def findByTag(tag: String): List[Dataset] = {
    dao.find(MongoDBObject("tags.name" -> tag)).toList
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
  
  def getUserMetadataJSON(id: String): String = {
    dao.collection.findOneByID(new ObjectId(id)) match {
      case None => "{}"
      case Some(x) => {
        x.getAs[DBObject]("userMetadata") match{
          case Some(y)=>{
	    	val returnedMetadata = com.mongodb.util.JSON.serialize(x.getAs[DBObject]("userMetadata").get)
	    		    	Logger.debug("retmd: "+ returnedMetadata)
			returnedMetadata
          }
          case None => "{}"
		}
      }
    }
  }
  
  def getTechnicalMetadataJSON(id: String): String = {
    dao.collection.findOneByID(new ObjectId(id)) match {
      case None => "{}"
      case Some(x) => {
        x.getAs[DBObject]("metadata") match{
          case Some(y)=>{
	    	val returnedMetadata = com.mongodb.util.JSON.serialize(x.getAs[DBObject]("metadata").get)
	    		    	Logger.debug("retmd: "+ returnedMetadata)
			returnedMetadata
          }
          case None => "{}"
		}
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
  
  def addXMLMetadata(id: String, fileId: String, json: String){
     Logger.debug("Adding XML metadata to dataset " + id + " from file " + fileId + ": " + json)
     val md = JsonUtil.parseJSON(json).asInstanceOf[java.util.LinkedHashMap[String, Any]].toMap     
     dao.collection.update(MongoDBObject("_id" -> new ObjectId(id)), $addToSet("datasetXmlMetadata" ->  DatasetXMLMetadata.toDBObject(DatasetXMLMetadata(md,fileId))), false, false, WriteConcern.Safe)		      
   } 

  def addUserMetadata(id: String, json: String) {
    Logger.debug("Adding/modifying user metadata to dataset " + id + " : " + json)
    val md = com.mongodb.util.JSON.parse(json).asInstanceOf[DBObject]
    dao.update(MongoDBObject("_id" -> new ObjectId(id)), $set("userMetadata" -> md), false, false, WriteConcern.Safe)
  }
 
  def tag(id: String, tag: Tag) { 
    //Need to check for the owner of the dataset before adding tag
    dao.collection.update(MongoDBObject("_id" -> new ObjectId(id)),  $addToSet("tags" ->  Tag.toDBObject(tag)), false, false, WriteConcern.Safe)
  }
  
  def removeTag(id: String, tagId: String) { 
	 Logger.debug("Removing tag " + tagId )
     val result = dao.collection.update(MongoDBObject("_id" -> new ObjectId(id)), $pull("tags" -> MongoDBObject("_id" -> new ObjectId(tagId))), false, false, WriteConcern.Safe)
  }
  
  /**
   * Check recursively whether a dataset's user-input metadata match a requested search tree. 
   */
  def searchUserMetadata(id: String, requestedMetadataQuery: Any): Boolean = {
    return searchMetadata(id, requestedMetadataQuery.asInstanceOf[java.util.LinkedHashMap[String,Any]], getUserMetadata(id))
  }
  
  
  def searchAllMetadataFormulateQuery(requestedMetadataQuery: Any): List[Dataset] = {
    Logger.debug("top: "+ requestedMetadataQuery.asInstanceOf[java.util.LinkedHashMap[String,Any]].toString()  )
    
    var theQuery =  searchMetadataFormulateQuery(requestedMetadataQuery.asInstanceOf[java.util.LinkedHashMap[String,Any]], "all")
    Logger.debug("thequery: "+theQuery.toString)    
    var dsList = dao.find(theQuery).toList
             
    return dsList
  }
  
  def searchUserMetadataFormulateQuery(requestedMetadataQuery: Any): List[Dataset] = {
    Logger.debug("top: "+ requestedMetadataQuery.asInstanceOf[java.util.LinkedHashMap[String,Any]].toString()  )
    var theQuery =  searchMetadataFormulateQuery(requestedMetadataQuery.asInstanceOf[java.util.LinkedHashMap[String,Any]], "userMetadata")
    Logger.debug("thequery: "+theQuery.toString)
    
    val dsList = dao.find(theQuery).toList
    return dsList
  }
  
def searchMetadataFormulateQuery(requestedMap: java.util.LinkedHashMap[String,Any], root: String): MongoDBObject = {
    Logger.debug("req: "+ requestedMap)
    var queryMap = MongoDBList()
    var builder = MongoDBList()
    var orFound = false
    for((reqKey, reqValue) <- requestedMap){
      val keyTrimmed = reqKey.replaceAll("__[0-9]+$","")
      
      if(keyTrimmed.equals("OR")){
          queryMap.add(MongoDBObject("$and" ->  builder))
          builder = MongoDBList()
          orFound = true
        }
      else{
        var actualKey = keyTrimmed
        if(keyTrimmed.endsWith("__not")){
        	  actualKey = actualKey.substring(0, actualKey.length()-5) 
          }
        
        if(!root.equals("all")){
        
	        if(!root.equals(""))
	        	actualKey = root + "." + actualKey 
	        
	        if(reqValue.isInstanceOf[String]){ 
	            val currValue = reqValue.asInstanceOf[String]            
	            if(keyTrimmed.endsWith("__not")){
	            	builder += MongoDBObject(actualKey -> MongoDBObject("$ne" ->  currValue))
	            }
	            else{
	            	builder += MongoDBObject(actualKey -> currValue)
	            }           
	        }else{
	          //recursive	          
	          if(root.equals("userMetadata")){
	            val currValue =  searchMetadataFormulateQuery(reqValue.asInstanceOf[java.util.LinkedHashMap[String,Any]], "")
	            val elemMatch = actualKey $elemMatch currValue
	            builder.add(elemMatch)
	          }
	          else{
	            val currValue =  searchMetadataFormulateQuery(reqValue.asInstanceOf[java.util.LinkedHashMap[String,Any]], actualKey)
	        	builder += currValue  
	          }	          
	        }
        }else{          
          var objectForEach = MongoDBList()
          val allRoots = Map(1 -> "userMetadata", 2 -> "metadata", 3 -> "datasetXmlMetadata.xmlMetadata")
          allRoots.keys.foreach{ i =>
            var tempActualKey = allRoots(i) + "." + actualKey
            
            if(reqValue.isInstanceOf[String]){ 
	            val currValue = reqValue.asInstanceOf[String]            
	            if(keyTrimmed.endsWith("__not")){
	            	objectForEach += MongoDBObject(tempActualKey -> MongoDBObject("$ne" ->  currValue))
	            }
	            else{
	            	objectForEach += MongoDBObject(tempActualKey -> currValue)
	            }           
	        }else{
	          //recursive	            	            
	            if(allRoots(i).equals("userMetadata")){
	                val currValue =  searchMetadataFormulateQuery(reqValue.asInstanceOf[java.util.LinkedHashMap[String,Any]], "")
	            	val elemMatch = tempActualKey $elemMatch currValue
	            	objectForEach.add(elemMatch)
	            }
	            else{
	                val currValue =  searchMetadataFormulateQuery(reqValue.asInstanceOf[java.util.LinkedHashMap[String,Any]], tempActualKey)
	            	objectForEach += currValue 
	            }	
	        }            
          }
          
          builder.add(MongoDBObject("$or" ->  objectForEach))
          
        }
      }
    }
    queryMap.add(builder.result)
    
    if(orFound){
    	return MongoDBObject("$or" ->  queryMap)
    }
    else{
      return MongoDBObject("$and" ->  builder)
    }
  }
  

  
  /**
   * Check recursively whether a (sub)tree of a dataset's metadata matches a requested search subtree. 
   */
  def searchMetadata(id: String, requestedMap: java.util.LinkedHashMap[String,Any], currentMap: scala.collection.mutable.Map[String,Any]): Boolean = {
      var allMatch = true
      Logger.debug("req: "+ requestedMap);
      Logger.debug("curr: "+ currentMap);
      for((reqKey, reqValue) <- requestedMap){
        var reqKeyCompare = reqKey
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
	        	    val currKeyCompare = currKey
	        		if(reqKeyCompare.equals(currKeyCompare)){
	        		  //If search subtree remaining is a string (ie we have reached a leaf), then remaining subtree currently examined is bound to be a string, as the path so far was the same.
	        		  //Therefore, we do string comparison.
	        		  if(reqValue.isInstanceOf[String]){        		    
	        		    if(currValue.isInstanceOf[com.mongodb.BasicDBList]){
	        		      for(itemInCurrValue <- currValue.asInstanceOf[com.mongodb.BasicDBList]){
	        		        if(reqValue.asInstanceOf[String].trim().equals("*") || reqValue.asInstanceOf[String].trim().equalsIgnoreCase(itemInCurrValue.asInstanceOf[String].trim())){
	        				  matchFound = true
	        				  throw MustBreak
	        		        }
	        		      }
	        		    }
	        		    else{
	        		      if(reqValue.asInstanceOf[String].trim().equals("*") || reqValue.asInstanceOf[String].trim().equalsIgnoreCase(currValue.asInstanceOf[String].trim())){
	        				  matchFound = true
	        			  }
	        		    } 
	        		  }
	        		  //If search subtree remaining is not a string (ie we haven't reached a leaf yet), then remaining subtree currently examined is bound to not be a string, as the path so far was the same.
	        		  //Therefore, we do maps (actually subtrees) comparison.
	        		  else{
	        		    if(currValue.isInstanceOf[com.mongodb.BasicDBList]){
	        		      for(itemInCurrValue <- currValue.asInstanceOf[com.mongodb.BasicDBList]){
	        		        val currValueMap = itemInCurrValue.asInstanceOf[com.mongodb.BasicDBObject].toMap().asScala.asInstanceOf[scala.collection.mutable.Map[String,Any]]
	        			    if(searchMetadata(id, reqValue.asInstanceOf[java.util.LinkedHashMap[String,Any]], currValueMap)){
	        				  matchFound = true
	        				  throw MustBreak
	        			    }
	        		      }
	        		    }
	        		    else{
	        		      val currValueMap = currValue.asInstanceOf[com.mongodb.BasicDBObject].toMap().asScala.asInstanceOf[scala.collection.mutable.Map[String,Any]]
	        			  if(searchMetadata(id, reqValue.asInstanceOf[java.util.LinkedHashMap[String,Any]], currValueMap)){
	        				  matchFound = true
	        			  }
	        		    } 
	        		  }
	        		  
	        		  throw MustBreak
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
  def removeFile(datasetId:String, fileId: String){   
    Dataset.update(MongoDBObject("_id" -> new ObjectId(datasetId)), $pull("files" -> MongoDBObject("_id" ->  new ObjectId(fileId))), false, false, WriteConcern.Safe)   
  }
  
  def newThumbnail(datasetId:String){
    dao.findOneById(new ObjectId(datasetId)) match{
	    case Some(dataset) => {
	    		val files = dataset.files map { f =>{
	    			FileDAO.get(f.id.toString).getOrElse{None}
	    		}}
			    for(file <- files){
			      if(file.isInstanceOf[models.File]){
			          val theFile = file.asInstanceOf[models.File]
				      if(!theFile.thumbnail_id.isEmpty){
				        Dataset.update(MongoDBObject("_id" -> new ObjectId(datasetId)), $set("thumbnail_id" -> theFile.thumbnail_id.get), false, false, WriteConcern.Safe)
				        return
				      }
			      }
			    }
			    Dataset.update(MongoDBObject("_id" -> new ObjectId(datasetId)), $set("thumbnail_id" -> None), false, false, WriteConcern.Safe)
	    }
	    case None =>
    }  
  }
  
  def removeDataset(id: String){
    dao.findOneById(new ObjectId(id)) match{
      case Some(dataset) => {
        for(collection <- Collection.listInsideDataset(id))
          Collection.removeDataset(collection.id.toString, dataset)
        for(comment <- Comment.findCommentsByDatasetId(id)){
        	Comment.removeComment(comment)
        }  
	    for(f <- dataset.files){
	      var notTheDataset = for(currDataset<-findByFileId(f.id) if !dataset.id.toString().equals(currDataset.id.toString())) yield currDataset
	      if(notTheDataset.size == 0)
	    	FileDAO.removeFile(f.id.toString)
	    }
        Dataset.remove(MongoDBObject("_id" -> dataset.id))        
      }
      case None => 
    }      
  }
  
}
