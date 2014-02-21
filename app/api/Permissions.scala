package api

import securesocial.core.Authorization
import securesocial.core.Identity
import play.api.mvc.WrappedRequest
import play.api.mvc.Request
import models.AppConfiguration

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
		DeleteDatasets,
		ListDatasets,
		ShowDataset,
		SearchDatasets,
		AddDatasetsMetadata,
		ShowDatasetsMetadata,
		ShowTags,
		CreateTags,
		DeleteTags,
		CreateComments,
		AddSections,
		GetSections,
		CreateFiles,
		DeleteFiles,
		ListFiles,
		AddFilesMetadata,
		ShowFilesMetadata,
		ShowFile,
		SearchFiles,
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
		AddThumbnail,
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
		  case (_, SearchFiles)	         => true
		  case (_, GetSections)          => true
		  case (_, ListFiles)            => true
		  case (_, ShowFile)             => true
		  case (_, ShowFilesMetadata)    => true
		  case (_, ShowDatasetsMetadata) => true
		  case (_, SearchStreams)        => true
		  case (_, ListSensors)          => true
		  case (_, GetSensors)           => true
		  case (_, SearchSensors)        => true
		  case (_, ShowTags)        	 => true
		  
		  // FIXME: required by ShowDataset if preview uses original file
		  // FIXME:  Needs to be here, as plugins called by browsers for previewers (Java, Acrobat, Quicktime for QTVR) cannot for now use cookies to authenticate as users.
		  case (_, DownloadFiles)        => true
		  
		  // all other permissions require authenticated user
		  case (null, _)                 => false
		  case(_, Permission.Admin) =>{
		    if(!user.email.isEmpty)
		    	if(AppConfiguration.adminExists(user.email.get))
		    	  true
		    	else
		    	  false  
		    else	  
		    	false	  
		  }
		  case (_, _)                    => true
		}
	}
}

