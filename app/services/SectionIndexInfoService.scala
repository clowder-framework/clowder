package services

import models.UUID
import scala.util.Try

/**
 * Census service.
 * 
 * Helper to store information about Census INdexes. Census index should be indexed separately and searched separately.
 * Census Index's id will be stored in CensusIndex db table.
 *
 *
 */
trait SectionIndexInfoService {

	/**
	 * Add a new document to the collection, with index Id and type given.
	 */
	def insertType(indexId: UUID, indexType:String)
  	
	/**
	 * Add a new document to the collection, with index Id and name given.
	 */
	def insertName(indexId:UUID, indexName:String)
	
	/**
	 * Check if given index is in the collection
	 */
	def isFound(indexId: UUID): Boolean 
	
	/**
	 * Return type of the sections for the id given
	 */
	def getType(indexId:UUID):Option[String]
	
	/**
	 * Returns an array of all distinct values of the field indexType
	 */
	def getDistinctTypes:List[String]
	 
	/**
	 * Return name of the sections for the id given
	 */
	def getName(indexId:UUID):Option[String]
		
	/**
	 * Delete all documents from collection
	 */
	def deleteAll()	
	
	/**
	 * Deletes index with the given id. Returns true is index was found and deleted. Returns false if index was not found.
	 * In any case, after completion of the method, index will not be in the collection.
	 */
	def delete(id:UUID):Boolean
	
}
