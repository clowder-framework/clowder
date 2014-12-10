package api

import securesocial.core.Authorization
import securesocial.core.Identity
import play.api.mvc.WrappedRequest
import play.api.mvc.Request
import services.AppConfigurationService
import models.UUID
import services.FileService
import services.DatasetService
import services.CollectionService
import play.api.Play.configuration
import play.api.Logger

 /**
  * A request that adds the User for the current call
  */
case class RequestWithUser[A](user: Option[Identity], request: Request[A]) extends WrappedRequest(request)

/**
 * List of all permissions available in Medici
 * 
 * @author Rob Kooperp
 *
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
		CreateTagsDatasets,
		DeleteTagsDatasets,
		ShowTags,
		UpdateDatasetInformation,
		UpdateLicense,
		CreateComments,
		RemoveComments,
		EditComments,
		CreateNotes,
		AddSections,
		GetSections,
		CreateTagsSections,
		DeleteTagsSections,
		CreateFiles,
		DeleteFiles,
		ListFiles,
		ExtractMetadata,
		AddFilesMetadata,
		ShowFilesMetadata,
		ShowFile,
		SearchFiles,
		CreateTagsFiles,
		DeleteTagsFiles,
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
 * @author Constantinos Sophocleous
 * @author Luigi Marini
 */
case class WithPermission(permission: Permission) extends Authorization {

  val appConfiguration: AppConfigurationService = services.DI.injector.getInstance(classOf[AppConfigurationService])
  val files: FileService = services.DI.injector.getInstance(classOf[FileService])
  val datasets: DatasetService = services.DI.injector.getInstance(classOf[DatasetService])
  val collections: CollectionService = services.DI.injector.getInstance(classOf[CollectionService])

	def isAuthorized(user: Identity): Boolean = {
		isAuthorized(user, None)
	}

	def isAuthorized(user: Identity, resource: Option[UUID] = None): Boolean = {

    // always check for useradmin, user needs to be in admin list no ifs ands or buts.
    if (permission == Public) {
      true
    } else if (permission == UserAdmin) {
      checkUserAdmin(user)
    } else {
      // based on scheme pick right setup
      configuration(play.api.Play.current).getString("permissions").getOrElse("public") match {
        case "public"  => publicPermission(user, permission, resource)
        case "private" => privatePermission(user, permission, resource)
        case "admin"   => adminPermission(user, permission, resource)
        case _         => publicPermission(user, permission, resource)
      }
    }
  }

  /**
   * All read-only actions are public, writes and admin require a login. This is the most
   * open configuration.
   */
  def publicPermission(user: Identity, permission: Permission, resource: Option[UUID]): Boolean = {
    // order is important
    (user, permission, resource) match {
      // anybody can list/show
      case (_, Public, _)               => true
      case (_, ListCollections, _)      => true
      case (_, ShowCollection, _)       => true
      case (_, ListDatasets, _)         => true
      case (_, ShowDataset, _)          => true
      case (_, SearchDatasets, _)       => true
      case (_, SearchFiles, _)          => true
      case (_, GetSections, _)          => true
      case (_, ListFiles, _)            => true
      case (_, ShowFile, _)             => true
      case (_, ShowFilesMetadata, _)    => true
      case (_, ShowDatasetsMetadata, _) => true
      case (_, SearchStreams, _)        => true
      case (_, ListSensors, _)          => true
      case (_, GetSensors, _)           => true
      case (_, SearchSensors, _)        => true
      case (_, ExtractMetadata, _)      => true
      case (_, ShowTags, _)             => true

      // FIXME: required by ShowDataset if preview uses original file
      // FIXME:  Needs to be here, as plugins called by browsers for previewers (Java, Acrobat, Quicktime for QTVR) cannot for now use cookies to authenticate as users.
      case (_, DownloadFiles, _)        => true

			// check resource ownership
			case (_, CreateFiles | DeleteFiles | AddFilesMetadata, Some(resource)) => checkFileOwnership(user, permission, resource)
			case (_, CreateDatasets | DeleteDatasets | AddDatasetsMetadata, Some(resource)) => checkDatasetOwnership(user, permission, resource)
			case (_, CreateCollections | DeleteCollections, Some(resource)) => checkCollectionOwnership(user, permission, resource)

      // all other permissions require authenticated user
      case (null, _, _)                 => false
      case (_, _, _)                    => true
    }
  }

  /**
   * All actions require a login, once logged in all users have all permission.
   */
  def privatePermission(user: Identity, permission: Permission, resource: Option[UUID]): Boolean = {
		Logger.debug("Private permissions " + user + " " + permission + " " + resource)
    (user, permission, resource) match {

			// check resource ownership
			case (_, CreateFiles | DeleteFiles | AddFilesMetadata, Some(resource)) => checkFileOwnership(user, permission, resource)
			case (_, CreateDatasets | DeleteDatasets | AddDatasetsMetadata, Some(resource)) => checkDatasetOwnership(user, permission, resource)
			case (_, CreateCollections | DeleteCollections, Some(resource)) => checkCollectionOwnership(user, permission, resource)

			// all other permissions require authenticated user
			case (null, _, _)                 => false
			case (_, _, _)                    => true
    }
  }

  /**
   * All actions require a login, admin actions require user to be in admin list.
   */
  def adminPermission(user: Identity, permission: Permission, resource: Option[UUID]): Boolean = {
    (user, permission, resource) match {

      // check to see if user has admin rights
      case (_, Permission.Admin, _)     => checkUserAdmin(user)

			// check resource ownership
			case (_, CreateFiles | DeleteFiles | AddFilesMetadata, Some(resource)) => checkFileOwnership(user, permission, resource)
			case (_, CreateDatasets | DeleteDatasets | AddDatasetsMetadata, Some(resource)) => checkDatasetOwnership(user, permission, resource)
			case (_, CreateCollections | DeleteCollections, Some(resource)) => checkCollectionOwnership(user, permission, resource)

			// all other permissions require authenticated user
			case (null, _, _)                 => false
			case (_, _, _)                    => true
    }
  }

  def checkUserAdmin(user: Identity) = {
     (user != null) && user.email.nonEmpty && appConfiguration.adminExists(user.email.get)
  }

	def checkFileOwnership(user: Identity, permission: Permission, resource: UUID): Boolean = {
		files.get(resource) match{
			case Some(file)=> {
				if(file.author.identityId.userId.equals(user.identityId.userId))
					true
				else
					checkUserAdmin(user)
			}
			case _ => {
				Logger.error("File requested to be accessed not found. Denying request.")
				false
			}
		}
	}

	def checkDatasetOwnership(user: Identity, permission: Permission, resource: UUID): Boolean = {
		datasets.get(resource) match{
			case Some(dataset)=> {
				if(dataset.author.identityId.userId.equals(user.identityId.userId))
					true
				else
					checkUserAdmin(user)
			}
			case _ => {
				Logger.error("Dataset requested to be accessed not found. Denying request.")
				false
			}
		}
	}

	def checkCollectionOwnership(user: Identity, permission: Permission, resource: UUID): Boolean = {
		collections.get(resource) match{
			case Some(collection) => {
				collection.author match{
					case Some(collectionAuthor) => {
						if(collectionAuthor.identityId.userId.equals(user.identityId.userId))
							true
						else
							checkUserAdmin(user)
					}
					//Anonymous collections are free-for-all
					// FIXME should we allow collections created by anonymous?
					case None => {
						Logger.info("Requested collection is anonymous, anyone can modify, granting modification request.")
						true
					}
				}
			}
			case _ => {
				Logger.error("Collection requested to be accessed not found. Denying request.")
				false
			}
		}
	}

}
