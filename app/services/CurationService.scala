package services

import java.net.URI

import models._


/**
 * Service to manipulate curation objects.
 */
trait CurationService {
  /**
   * insert a new curation object.
   */
  def insert(curation: CurationObject)

  /**
   * Get curation object.
   */
  def get(id: UUID): Option[CurationObject]

  def getAbstract(id: UUID): Option[UUID]

  /**
   * Update status of a curation object.
   */
  def updateStatus(id: UUID, status: String)

  /**
   * Set the curation object as submitted and set the submitted date to current date
   */
  def setSubmitted(id: UUID)

  /**
   * Set the curation object as Published and set the publish date to current date.
   */
  def setPublished(id:UUID)

  /**
   * remove curation object, also delete it from staging area.
   */
  def remove(id: UUID): Unit

  /**
   * insert a new curation object file.
   */
  def insertFile(curationFile : CurationFile)

  /**
   * insert a new curation object folder.
   */
  def insertFolder(curationFolder : CurationFolder)

  /**
   * Update the repository selected
   */
  def updateRepository(curationId: UUID, repository: String)

  /**
   * Save external Identifier received from repository
   */
  def updateExternalIdentifier(curationId: UUID, externalIdentifier: URI)

  /**
   * List curation and published objects a dataset is related to.
   */
  def getCurationObjectByDatasetId(datasetId: UUID): List[CurationObject]

  /**
   * List curation files of a curation obeject
   */
  def getCurationFiles(curationFileIds:List[UUID]): List[CurationFile]

  /**
   * List curation folders of a curation obeject
   */
  def getCurationFolders(curationFolderIds:List[UUID]): List[CurationFolder]

  /**
   * List curation file ids of a curation object and its folders
   */
  def getAllCurationFileIds(id:UUID): List[UUID]

  /**
    * List curation folder ids of a curation object and its folders
    */
  def getAllCurationFolderIds(id:UUID): List[UUID]

  /**
   * get the curation contains this curation file
   */
  def getCurationByCurationFile(curationFileId: UUID): Option[CurationObject]

  /**
   * get the curation folder
   */
  def getCurationFolder(curationFolderId: UUID): Option[CurationFolder]

  /**
   * add a curation file to curationObject or curation folder.
   */
  def addCurationFile(parentType: String, parentId: UUID, curationFileId: UUID)

  /**
   * remove a curation folder from curationObject or curation folder.
   */
  def removeCurationFile(parentType: String, parentId: UUID, curationFileId: UUID)

  /**
   * add a curation folder to curationObject or curation folder.
   */
  def addCurationFolder(parentType: String, parentId: UUID, subCurationFolderId: UUID)

  /**
   * remove a curation folder to curationObject or curation folder.
   */
  def removeCurationFolder(parentType: String, parentId: UUID, subCurationFolderId: UUID)

  /**
   * Delete a curation file, and remove its metadata.
   */
  def deleteCurationFile(curationFileId: UUID)

  /**
   * Delete a curation folder and all its subfolders and files.
   */
  def deleteCurationFolder(id: UUID): Unit

  /**
   * Update curation object's name, description, space.
   */
  def updateInformation(id: UUID, description: String, name: String, oldSpace: UUID, newSpace:UUID)
}
