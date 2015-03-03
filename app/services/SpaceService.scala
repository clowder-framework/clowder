package services

import models.{UUID, ProjectSpace}
import services.core.CRUDService

/**
 * Service to manipulate spaces.
 *
 * @author Luigi Marini
 *
 */
trait SpaceService extends CRUDService[ProjectSpace] {

  def addCollection(collection: UUID, space: UUID)

  def addDataset(dataset: UUID, space: UUID)
  
  def isTimeToLiveEnabled(space: UUID): Boolean
  
  def getTimeToLive(space: UUID): Integer
  
  def purgeExpiredResources(space: UUID)
}
