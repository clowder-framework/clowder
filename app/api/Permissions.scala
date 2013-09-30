package api

import securesocial.core.Authorization
import securesocial.core.Identity
import play.api.mvc.WrappedRequest
import play.api.mvc.Request

 /**
  * A request that adds the User for the current call
  */
case class RequestWithUser[A](user: Option[Identity], request: Request[A]) extends WrappedRequest(request)

/**
 * List of all permissions available in Medici
 * 
 * @author Rob Kooper
 */
object Permission extends Enumeration {
	type Permission = Value
	val Public,					// Page is public accessible, i.e. no login needed 
		Admin,
		CreateCollections,
		DeleteCollections,
		ListCollections,
		ShowCollection,
		CreateDatasets,
		ListDatasets,
		ShowDataset,
		SearchDatasets,
		AddDatasetsMetadata,		
		CreateTags,
		RemoveTags,
		CreateComments,
		AddSections,
		GetSections,
		CreateFiles,
		ListFiles,
		ShowFile,
		CreateStreams,
		AddDataPoints,
		SearchStreams,
		AddZoomTile,
		Add3DTexture,
		AddIndex,
		CreateSensors,
		ListSensors,
		GetSensors,
		SearchSensors,
		RemoveSensors,
		DownloadFiles = Value
}

import api.Permission._

/**
 * Specific implementation of an Authorization
 * 
 * @author Rob Kooper
 */
case class WithPermission(permission: Permission) extends Authorization {

	def isAuthorized(user: Identity): Boolean = {
		// order is important
		(user, permission) match {
		  // anybody can list/show
		  case (_, Public)               => true
		  case (_, ListCollections)      => true
		  case (_, ShowCollection)       => true
		  case (_, ListDatasets)         => true
		  case (_, ShowDataset)          => true
		  case (_, SearchDatasets)       => true
		  case (_, GetSections)          => true
		  case (_, ListFiles)            => true
		  case (_, ShowFile)             => true
		  case (_, SearchStreams)        => true
		  case (_, ListSensors)          => true
		  case (_, GetSensors)           => true
		  case (_, SearchSensors)        => true
		  
		  // FIXME: required by ShowDataset if preview uses original file
		  // FIXME:  Needs to be here, as plugins called by browsers for previewers (Java, Acrobat, Quicktime for QTVR) cannot for now use cookies to authenticate as users.
		  case (_, DownloadFiles)        => true
		  
		  // all other permissions require authenticated user
		  case (null, _)                 => false
		  case (_, _)                    => true
		}
	}
}

