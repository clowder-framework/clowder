package services.mongodb

import com.novus.salat.dao.{ModelCompanion, SalatDAO}
import MongoContext.context
import play.api.Play.current
import com.mongodb.casbah.Imports._
import play.api.Logger
import services.SectionIndexInfoService
import models.{SectionIndexInfo, UUID}

/**
 *
 */
class MongoDBSectionIndexInfoService extends SectionIndexInfoService {  
  
	/**
   * Check if index with this id is already in mongo collection. If it is - update type. If it is not -
   * add a new entry with index id and index type.
   */
  def insertType (indexId: UUID, indexType: String) {
	  val query = MongoDBObject("indexId" -> indexId.stringify)
	  //set "true" for upsert
	  SectionIndexInfoDAO.update(query,  $set("indexType" -> indexType), true, false, WriteConcern.Safe)	  
  }
  
  /**
   *Insert name of an index into mongo db.
   *Check if index with this id is already in mongo collection. If it is - update name. If not -
   * add a new entry with index id and index name.
   * Input:
   * 	@param indexId - id of the index
   * 	@param indexName - name  of the index 
   * 
   */  
  def insertName (indexId: UUID, indexName: String) {
    val query = MongoDBObject("indexId" -> indexId.stringify)
    //set boolean parameter to "true" for upsert, i.e. update/insert
	  SectionIndexInfoDAO.update(query,  $set("indexName" -> indexName), true, false, WriteConcern.Safe)  
  }
   
  /**
   * Returns name of this index if found, otherwise returns none.
   */
  def getName(indexId: UUID): Option[String] = {
    val query = MongoDBObject("indexId" -> indexId.stringify)
    SectionIndexInfoDAO.findOne(query).flatMap(_.indexName)
  }
  
  /**
   * Returns type string if found, otherwise returns none.
   */
  def getType(indexId: UUID): Option[String] = {    
      SectionIndexInfoDAO.findOne(MongoDBObject("indexId" -> indexId.stringify)).flatMap(_.indexType)   
  }
   
  /**
   * Returns an array of all distinct values of the field indexType
   */
  def getDistinctTypes(): List[String] = {
      SectionIndexInfoDAO.find(MongoDBObject()).flatMap(_.indexType).toList.distinct
	}
  
 /**
  * Returns true is entry with this indexId is found.
  */
  def isFound(indexId: UUID): Boolean = { 
      SectionIndexInfoDAO.findOne(MongoDBObject("indexId" -> indexId)).isDefined		
  }
  
  /**
   * Removes collection from mongo db
   */
  def deleteAll() {
    SectionIndexInfoDAO.remove(MongoDBObject())
  }  
        
  /**
   * Removes one index from collection
   */
  def delete(indexId: UUID): Boolean = {    
    Logger.debug("MongoDBSectionIndexInfoService - top of delete for indexId = " + indexId)
    val query = MongoDBObject("indexId" -> indexId.stringify)
    val result = SectionIndexInfoDAO.remove( query )
    Logger.debug("MongoDBSectionIndexInfoService - delete: Number removed: = result.getN = " + result.getN)
    //if one or more deleted - return true
    val wasDeleted = result.getN >0        
    wasDeleted
  }
}

object SectionIndexInfoDAO extends ModelCompanion[SectionIndexInfo, ObjectId] {
  val dao = current.plugin[MongoSalatPlugin] match {
    case None => throw new RuntimeException("No MongoSalatPlugin");
    case Some(x) => new SalatDAO[SectionIndexInfo, ObjectId](collection = x.collection("sectionIndexInfo")) {}
  }
}