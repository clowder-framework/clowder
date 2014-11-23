package api

import securesocial.core.Authorization
import securesocial.core.Identity
import play.api.mvc.WrappedRequest
import play.api.mvc.Request
import play.api.Play.configuration
import services.AppConfigurationService

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
		EditCollection,
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
		UpdateDatasetInformation,
		UpdateLicense,
		CreateComments,
		RemoveComments,
		EditComments,
		CreateNotes,
		AddSections,
		GetSections,
		CreateFiles,
		DeleteFiles,
		ListFiles,
		ExtractMetadata,
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
		DownloadFiles,
    GetUser,
    UserAdmin = Value        // Permission to work with users (list/add/remove/register)
}

import api.Permission._

/**
 * Specific implementation of an Authorization
 * 
 * @author Rob Kooper
 */
case class WithPermission(permission: Permission) extends Authorization {
	
  val appConfiguration: AppConfigurationService = services.DI.injector.getInstance(classOf[AppConfigurationService])
	
  def isAuthorized(user: Identity): Boolean = {
    // always check for useradmin, user needs to be in admin list no ifs ands or buts.
    if (permission == UserAdmin) {
      checkUserAdmin(user)
    } else {
      // based on scheme pick right setup
      configuration(play.api.Play.current).getString("permissions").getOrElse("public") match {
        case "public"  => publicPermission(user, permission)
        case "private" => privatePermission(user, permission)
        case "admin"   => adminPermission(user, permission)
        case _         => publicPermission(user, permission)
      }
    }
  }

  /**
   * All read-only actions are public, writes and admin require a login. This is the most
   * open configuration.
   */
  def publicPermission(user: Identity, permission: Permission): Boolean = {
    // order is important
    (user, permission) match {
      // anybody can list/show
      case (_, Public)               => true
      case (_, ListCollections)      => true
      case (_, ShowCollection)       => true
      case (_, ListDatasets)         => true
      case (_, ShowDataset)          => true
      case (_, SearchDatasets)       => true
      case (_, SearchFiles)          => true
      case (_, GetSections)          => true
      case (_, ListFiles)            => true
      case (_, ShowFile)             => true
      case (_, ShowFilesMetadata)    => true
      case (_, ShowDatasetsMetadata) => true
      case (_, SearchStreams)        => true
      case (_, ListSensors)          => true
      case (_, GetSensors)           => true
      case (_, SearchSensors)        => true
      case (_, ExtractMetadata)      => true
      case (_, ShowTags)             => true

      // FIXME: required by ShowDataset if preview uses original file
      // FIXME:  Needs to be here, as plugins called by browsers for previewers (Java, Acrobat, Quicktime for QTVR) cannot for now use cookies to authenticate as users.
      case (_, DownloadFiles)        => true
      
      // all other permissions require authenticated user
      case (null, _)                 => false
      case (_, _)                    => true
    }
  }

  /**
   * All actions require a login, once logged in all users have all permission.
   */
  def privatePermission(user: Identity, permission: Permission): Boolean = {
    (user, permission) match {
      // return true if user is signed in
      case (null, _)                 => false
      case (_, _)                    => true
    }
  }

  /**
   * All actions require a login, admin actions require user to be in admin list.
   */
  def adminPermission(user: Identity, permission: Permission): Boolean = {
    (user, permission) match {
      // check to see if user has admin rights
      case (_, Permission.Admin)     => checkUserAdmin(user)

      // return true if user is signed in
      case (null, _)                 => false
      case (_, _)                    => true
    }
  }

  def checkUserAdmin(user: Identity) = {
     (user != null) && user.email.nonEmpty && appConfiguration.adminExists(user.email.get)
  }
}

