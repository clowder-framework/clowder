package services

import models.UUID

/**
 * 
 * @author Inna Zharnitsky July 2015
 *
 */
trait ExtractorsForSpaceService {

	/**
	 * Adds a new document to the collection, with spaceId and extractor given.
	 * If entry for spaceId already exists, updates list of extractors.
	 */
	def addExtractor(spaceId: UUID, extractor:String)
	
	/**
	 * Gets all extractors for this space id.
	 */
    def getAllExtractors(spaceId: UUID): List[String]

  	/**
	 * Deletes an entire entry for this space id.
	 */
	def delete(spaceId: UUID): Boolean

}
