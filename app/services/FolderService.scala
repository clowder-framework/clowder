package services

import models.{DBResult, Folder, TypedID, UUID, User}
import play.api.libs.json.JsObject
/**
 * Generic Folder Service
 */
trait FolderService {

  /**
   * Get Folder
   */
  def get(id: UUID): Option[Folder]

  def get(ids: List[UUID]): DBResult[Folder]

  /**
   * Create a Folder
   */
  def insert(folder: Folder): Option[String]

  /**
   * Delete folder and any reference of it.
   */
  def delete(folderId: UUID, host: String, apiKey: Option[String], user: Option[models.User])

  /**
   * Update a Folder
   */
  def update(folder: Folder)

  /**
   * Add File to Folder
   */
  def addFile(folderId: UUID, fileId: UUID)

  /**
   * Remove file from folder
   */
  def removeFile(folderId: UUID, fileId: UUID)

  /**
   * Add Subfolder to folder
   */
  def addSubFolder(folderId: UUID, subFolderId: UUID)

  /**
   * Remove subfolder.
   */
  def removeSubFolder(folderId: UUID, subFolderId: UUID)
  /**
   * Update parent of a folder
   */
  def updateParent(folderId: UUID, parent: TypedID)

  /**
   * Update name for a folder
   */
  def updateName(folderId: UUID, name: String, displayName: String)

  /**
    * Update author full name
    */
  def updateAuthorFullName(userId: UUID, fullName: String)

  /**
   * Find folders that contain a file by id.
   */
  def findByFileId(file_id: UUID): List[Folder]

  /**
   * Count how many folders have the same base name
   */
  def countByName(name: String, parentType: String, parentId: String): Long

  /**
   * Count how many folders have the same display name
   */
  def countByDisplayName(name: String, parentType: String, parentId: String): Long

  /**
    * Get all folders with the same base name
    */
  def findByNameInParent(name:String, parentType: String, parentId: String): List[Folder]

  /**
    * Get all folders with the same display name
    */
  def findByDisplayNameInParent(name:String, parentType:String, parentId: String): List[Folder]

  /**
    * Get all folders that are part of a dataset (doesn't matter the level)
    */
  def findByParentDatasetId(parentId: UUID): List[Folder]

  /**
    * Get all the folders in a list of parent datasettIds. It helps to identify the files that a user has access to.
    */
  def findByParentDatasetIds(parentIds: List[UUID]): List[Folder]

  /**
   * Archive all files and subfolders within this folder.
   */
  def recursiveArchive(folder: Folder, host: String, parameters: JsObject, apiKey: Option[String], user: Option[User]): Unit
}
