package services.mongodb

import com.mongodb.casbah.Imports._
import models.{SectionIndexInfo, UUID}
import play.api.Logger
import salat.dao.{ModelCompanion, SalatDAO}
import services.mongodb.MongoContext.context
import services.{DI, SectionIndexInfoService}

/**
 * USe Mongodb to store section index information
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
  val COLLECTION = "sectionIndexInfo"
  val mongos: MongoStartup = DI.injector.getInstance(classOf[MongoStartup])
  val dao = new SalatDAO[SectionIndexInfo, ObjectId](collection = mongos.collection(COLLECTION)) {}
}