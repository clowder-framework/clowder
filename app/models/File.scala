package models

import java.util.Date
import api.{WithPermission, Permission}
import securesocial.core.Identity


/**
 * Uploaded files.
 *
 * @author Luigi Marini
 *
 */
case class File(
  id: UUID = UUID.generate,
  path: Option[String] = None,
  filename: String,
  author: Identity,
  uploadDate: Date,
  contentType: String,
  length: Long = 0,
  showPreviews: String = "DatasetLevel",
  sections: List[Section] = List.empty,
  previews: List[Preview] = List.empty,
  tags: List[Tag] = List.empty,
  metadata: List[Map[String, Any]] = List.empty,
  thumbnail_id: Option[String] = None,
  isIntermediate: Option[Boolean] = None,
  userMetadata: Map[String, Any] = Map.empty,
  xmlMetadata: Map[String, Any] = Map.empty,
  userMetadataWasModified: Option[Boolean] = None,
  licenseData: LicenseData = new LicenseData(),
  notesHTML: Option[String] = None ) {
    
  /**
   * Utility method to check a given file and a given identity for permissions from the license 
   * to allow the raw bytes to be downloaded. 
   * 
   * @param anIdentity An Option, possibly containing the securesocial information for a user
   * 
   * @return A boolean, true if the license allows the bytes to be downloaded, false otherwise
   *   
   */
  def checkLicenseForDownload(anIdentity: Option[Identity]): Boolean = {
    licenseData.isDownloadAllowed || (anIdentity match {
      case Some(x) => WithPermission(Permission.DownloadFiles).isAuthorized(x) || licenseData.isRightsOwner(x.fullName)
      case None => false
    })
  }
}
  
case class Versus(
  fileId: UUID,
  descriptors: Map[String,Any]= Map.empty
)

