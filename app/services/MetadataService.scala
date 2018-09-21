package services

import api.UserRequest
import play.api.libs.Files
import play.api.libs.json.JsValue
import models.{MetadataDefinition, ResourceRef, UUID, Metadata, User}
import play.api.mvc.MultipartFormData

/**
 * MetadataService for add and query metadata
 */
trait MetadataService {
  
  /** Add metadata to the metadata collection and attach to a section /file/dataset/collection */
  def addMetadata(metadata: Metadata) : UUID
  
  /** Get Metadata By Id*/
  def getMetadataById(id : UUID) : Option[Metadata]
  
  /** Get Metadata based on Id of an element (section/file/dataset/collection) */
  def getMetadataByAttachTo(resourceRef: ResourceRef): List[Metadata]

  /** Get Extractor metadata by attachTo, from a specific extractor if given */
  def getExtractedMetadataByAttachTo(resourceRef: ResourceRef, extractor: String): List[Metadata]

  /** Get metadata based on type i.e. user generated metadata or technical metadata  */
  def getMetadataByCreator(resourceRef: ResourceRef, typeofAgent:String): List[Metadata]

  /** Remove metadata */
  def removeMetadata(metadataId: UUID)

  /** Remove metadata by attachTo*/
  def removeMetadataByAttachTo(resourceRef: ResourceRef, host: String): List[UUID]

  /** Remove metadata by attachTo from a specific extractor */
  def removeMetadataByAttachToAndExtractor(resourceRef: ResourceRef, extractorName: String, host: String): List[UUID]
  
  /** Get metadata context if available */
  def getMetadataContext(metadataId: UUID): Option[JsValue]

  /** Update Metadata */
  def updateMetadata(metadataId: UUID, json: JsValue)

  /** Vocabulary definitions for user fields **/
  def getDefinitions(spaceId: Option[UUID] = None): List[MetadataDefinition]

  /** Vocabulary definitions with distinct names **/
  def getDefinitionsDistinctName(user: Option[User] = None): List[MetadataDefinition]

  /** Get vocabulary based on id **/
  def getDefinition(id: UUID): Option[MetadataDefinition]

  /** Get vocabulary based on uri **/
  def getDefinitionByUri(uri:String):Option[MetadataDefinition]

  /** Get vocabulary based on uri and space **/
  def getDefinitionByUriAndSpace(uri: String, spaceId: Option[String]): Option[MetadataDefinition]

  /** Remove all metadata definitions related to a space**/
  def removeDefinitionsBySpace(spaceId: UUID)

  /** Add vocabulary definitions, leaving it unchanged if the update argument is set to false, defaults to update **/
  def addDefinition(definition: MetadataDefinition, update: Boolean = true)

  /** Edit vocabulary definitions**/
  def editDefinition(id:UUID, json: JsValue)

  /** Delete vocabulary definitions**/
  def deleteDefinition(id: UUID)

  /** Search for metadata that have a key in a dataset **/
  def searchbyKeyInDataset(key: String, datasetId: UUID): List[Metadata]

  /** Update author full name**/
  def updateAuthorFullName(userId: UUID, fullName: String)
}
