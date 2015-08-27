package services.mongodb

import com.novus.salat.dao.{ModelCompanion, SalatDAO}
import MongoContext.context
import play.api.Play.current
import com.mongodb.casbah.Imports._
import play.api.Logger
import services.ExtractorsForSpaceService
import models.{ExtractorsForSpace, UUID}

/**
 * @author Inna Zharnitsky July 2015
 */
class MongoDBExtractorsForSpaceService extends ExtractorsForSpaceService { 
  
  /**
   * Deletes entry with this space id.
   */
  def delete(spaceId: UUID): Boolean = {    
    val query = MongoDBObject("spaceId" -> spaceId.stringify)
    val result = ExtractorsForSpaceDAO.remove( query )
    //if one or more deleted - return true
    val wasDeleted = result.getN >0        
    wasDeleted
  }  
  
 /**
   * If entry for this spaceId already exists, adds extractor to it.
   * Otherwise, creates a new entry with spaceId and extractor.
   */
  def addExtractor (spaceId: UUID, extractor: String) {
	  //will add extractor to the list of extractors for this space, only if it's not there.
	  val query = MongoDBObject("spaceId" -> spaceId.stringify)	  
	  ExtractorsForSpaceDAO.update(query, $addToSet("extractors" -> extractor), true, false, WriteConcern.Safe)	   
  }

  /**
   * Returns a list of extractors associated with this spaceId.
   */
  def getAllExtractors(spaceId: UUID): List[String] = {
    //Note: in models.ExtractorsForSpace, spaceId must be a String
    // if space Id is UUID, will compile but throws Box run-time error
    val query = MongoDBObject("spaceId" -> spaceId.stringify)

    val list = (for (extr <- ExtractorsForSpaceDAO.find(query)) yield extr).toList
    //get extractors' names for given space id
    val extractorList: List[String] = list.flatMap(_.extractors)
    extractorList
  }
}

object ExtractorsForSpaceDAO extends ModelCompanion[ExtractorsForSpace, ObjectId] {
  val dao = current.plugin[MongoSalatPlugin] match {
    case None => throw new RuntimeException("No MongoSalatPlugin");
    case Some(x) => new SalatDAO[ExtractorsForSpace, ObjectId](collection = x.collection("extractorsForSpace")) {}
  }
}