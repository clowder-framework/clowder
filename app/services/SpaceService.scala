package services

import models.{UUID, ProjectSpace}
import services.core.CRUDService
import models.Collection
import models.Dataset

/**
 * Service to manipulate spaces.
 *
 * @author Luigi Marini
 *
 */
trait SpaceService extends CRUDService[ProjectSpace] {

  def addCollection(collection: UUID, space: UUID)

  def addDataset(dataset: UUID, space: UUID)
  
  /**
   * Determine if time to live for resources is enabled for a specific space.
   * 
   * @param space The identifier for the space that is being checked
   * 
   * @return A flag that denotes if time to live is enabled on this space. 
   */
  def isTimeToLiveEnabled(space: UUID): Boolean
  
  /**
   * Obtain the time to live for resources that are assigned to a specific space.
   * 
   * @param space The identifier for the space to be queried
   *  
   * @return The length of time, in milliseconds, that resources are allowed to persist in this space.
   *  
   */
  def getTimeToLive(space: UUID): Long
  
  /**
   * Service call to tell a space to clean up resources that are expired relative to the 
   * specified time to live.
   * 
   * @param space The identifier for the space that will be purged
   * 
   */
  def purgeExpiredResources(space: UUID)

  /**
   * Service access to retrieve the collections that are contained by a specific space.
   * 
   * @param spaceId The identifier for the space to be checked
   * 
   * @return A list that contains all of the collections attached to the space.
   */
  def getCollectionsInSpace(spaceId: UUID): List[Collection]

  /**
   * Service access to retrieve the datasets that are contained by a specific space.
   * 
   * @param spaceId The identifier for the space to be checked
   * 
   * @return A list that contains all of the datasets attached to the space.
   */
  def getDatasetsInSpace(spaceId: UUID): List[Dataset]    
  
  /**
   * Service call to update the information and configuration that are part of a space.
   * 
   * @param spaceId The identifier for the space to be updated
   * @param name The updated name information, HTMLEncoded since it is free text
   * @param description The updated description information, HTMLEncoded since it is free text
   * @param timeToLIve The updated amount of time, in milliseconds, that resources should be preserved in the space
   * @param expireEnabled The updated flag, indicating whether or not the space should allow resources to expire
   * 
   */
  def updateSpaceConfiguration(spaceId: UUID, name: String, description: String, timeToLive: Long, expireEnabled: Boolean)
  
}
