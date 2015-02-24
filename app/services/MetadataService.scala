package services

import play.api.libs.json.JsValue
import models.UUID
import models.Metadata

trait MetadataService {
  
  /** Add metadata to the metadata collection and attach to a section /file/dataset/collection */
  def addMetadata(metadata: Metadata) : UUID
  
  /** Get Metadata based on Id of an element (section/file/dataset/collection) */
  def getMetadataByAttachTo(elementId: UUID): List[Metadata]

  /** Get metadata based on type i.e. user generated metadata or technical metadata  */
  def getMetadataByCreator(elementId: UUID, typeofAgent:String): List[Option[Metadata]]

  /** Get metadata context if available */
  def getMetadataContext(metadataId: UUID): Option[JsValue]

  /** Remove metadata */
  def removeMetadata(metadataId: UUID)

  /** update Metadata */  
  def updateMetadata(metadataId: UUID, json: JsValue)
  
 
}