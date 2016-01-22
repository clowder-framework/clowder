package services

import models.{TypedID, UUID, Folder}
/**
 * Generic Folder Service
 */
trait FolderService {

  /**
   * Get Folder
   */
  def get(id: UUID): Option[Folder]

  /**
   * Create a Folder
   */
  def insert(folder: Folder): Option[String]

  /**
   * Delete folder and any reference of it.
   */
  def delete(folderId: UUID)

  /**
   * Update a Folder
   */
  def update(folder: Folder)

  /**
   * Add File to Folder
   */
  def addFile(folderId: UUID, fileId: UUID)

  /**
   * Add Subfolder to folder
   */
  def addSubFolder(folderId: UUID, subFolderId: UUID)

  /**
   * Update parent of a folder
   */
  def updateParent(folderId: UUID, parent: TypedID)
}
