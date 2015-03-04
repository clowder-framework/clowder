package services

import models.{UUID, ProjectSpace}
import services.core.CRUDService
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
   * Service access to retrieve the datasets that are contained by a specific space.
   * 
   * @param spaceId The identifier for the space to be checked
   * 
   * @return A list that contains all of the datasets attached to the space.
   */
  def getDatasetsInSpace(spaceId: UUID): List[Dataset]
    
}
