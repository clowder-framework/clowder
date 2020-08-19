package services

import models.{MetadataGroup, UUID}

trait MetadataGroupService {

  def save(mdGroup: MetadataGroup) : Option[String]

  def delete(mdGroupId: UUID)

  def get(id: UUID) : Option[MetadataGroup]

  def list(userId: UUID): List[MetadataGroup]

  def listSpace(spaceId: UUID) : List[MetadataGroup]

  def addToSpace(mdGroup: MetadataGroup, spaceId: UUID)

  def removeFromSpace(mdGroup: MetadataGroup, spaceId: UUID)

  def attachToFile(mdGroup: MetadataGroup, fileId: UUID)

  def attachToDatast(mdGroup: MetadataGroup, fileId: UUID)

  def getAttachedToFile(fileId: UUID) : MetadataGroup

  def getAttachedToDataset(datasetId: UUID) : MetadataGroup
}
