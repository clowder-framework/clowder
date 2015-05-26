package api

import models.{User, UUID}
import play.Logger
import securesocial.core.Authorization
import securesocial.core.Identity
import play.api.mvc.WrappedRequest
import play.api.mvc.Request
import play.api.Play.configuration
<<<<<<< HEAD
import services._
=======
import services.{CollectionService, DatasetService, FileService, SectionService, AppConfiguration}
>>>>>>> origin/develop

 /**
  * A request that adds the User for the current call
  */
case class RequestWithUser[A](user: Option[Identity], mediciUser: Option[User], request: Request[A]) extends WrappedRequest(request)

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
		CreateSpaces,
		UpdateSpaces,
		DeleteSpaces,
		EditSpace,
		ListSpaces,
		ShowSpace,
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
    AddProject,
    AddInstitution,
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

  val files: FileService = services.DI.injector.getInstance(classOf[FileService])
  val datasets: DatasetService = services.DI.injector.getInstance(classOf[DatasetService])
	val collections: CollectionService = services.DI.injector.getInstance(classOf[CollectionService])
  val collections: CollectionService = services.DI.injector.getInstance(classOf[CollectionService])
  val sections: SectionService = services.DI.injector.getInstance(classOf[SectionService])
	val spaces: SpaceService = services.DI.injector.getInstance(classOf[SpaceService])

	def isAuthorized(user: Identity): Boolean = {
		isAuthorized(if (user == null) None else Some(user), None)
	}

	def isAuthorized(user: Identity, resource: Option[UUID]): Boolean = {
		isAuthorized(if (user == null) None else Some(user), resource)
	}

	def isAuthorized(user: Option[Identity], resource: Option[UUID] = None): Boolean = {
    if (permission == Public) {
			// public pages are always visible
      true
    } else if (permission == UserAdmin) {
			// user admin always requires admin privileges
			checkUserAdmin(user)
		} else {
			// first check to see if user has the right permission for the resource
			checkResourceOwnership(user, permission, resource) match {
				case Some(b) => b
				case None => {
					// based on permissions pick right check,
					configuration(play.api.Play.current).getString("permissions").getOrElse("public") match {
						case "public"  => publicPermission(user, permission, resource)
						case "private" => privatePermission(user, permission, resource)
						case "admin"   => adminPermission(user, permission, resource)
						case _         => adminPermission(user, permission, resource)
					}
				}
			}
    }
  }

  /**
   * All read-only actions are public, writes and admin require a login. This is the most
   * open configuration.
   */
  def publicPermission(user: Option[Identity], permission: Permission, resource: Option[UUID]): Boolean = {
    // order is important
    (user, permission, resource) match {
      // anybody can list/show
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
      case (_, ListSpaces, _)           => true
      case (_, ShowSpace, _)            => true
      case (_, SearchStreams, _)        => true
      case (_, ListSensors, _)          => true
      case (_, GetSensors, _)           => true
      case (_, SearchSensors, _)        => true
      case (_, ExtractMetadata, _)      => true
      case (_, ShowTags, _)             => true

      // FIXME: required by ShowDataset if preview uses original file
      // FIXME:  Needs to be here, as plugins called by browsers for previewers (Java, Acrobat, Quicktime for QTVR) cannot for now use cookies to authenticate as users.
      case (_, DownloadFiles, _)        => true

			// all other permissions require authenticated user
			case (Some(u), _, _) => true

			// not logged in results in permission denied
			case (None, _, _)  => false
    }
  }

  /**
   * All actions require a login, once logged in all users have all permission.
   */
  def privatePermission(user: Option[Identity], permission: Permission, resource: Option[UUID]): Boolean = {
		Logger.debug("Private permissions " + user + " " + permission + " " + resource)
    (user, permission, resource) match {
			// all permissions require authenticated user
			case (Some(u), _, _) => true

			// not logged in results in permission denied
			case (None, _, _)  => false
    }
  }

  /**
   * All actions require a login, admin actions require user to be in admin list.
   */
  def adminPermission(user: Option[Identity], permission: Permission, resource: Option[UUID]): Boolean = {
    (user, permission, resource) match {
			// check to see if user has admin rights
			case (_, Permission.Admin, _) => checkUserAdmin(user)

			// all permissions require authenticated user
			case (Some(u), _, _) => true

			// not logged in results in permission denied
			case (None, _, _)  => false
    }
  }

	/**
	 * Check to see if the user is logged in and is an admin.
	 */
	def checkUserAdmin(user: Option[Identity]) = {
		user match {
			case Some(u) => u.email.nonEmpty && AppConfiguration.checkAdmin(u.email.get)
			case None => false
		}
	}

	/**
	 * If only owners can modify an object, check to see if the user logged in
	 * is the owner of the resource. This will return None if no check was done
	 * or true/false if the user can or can not access the resource.
	 */
	def checkResourceOwnership(user: Option[Identity], permission: Permission, resource: Option[UUID]): Option[Boolean] = {
		if (resource.isDefined && configuration(play.api.Play.current).getBoolean("ownerOnly").getOrElse(false)) {
			// only check if resource is defined and we want owner only permissions
			permission match {
				case CreateFiles => Some(checkFileOwnership(user, resource.get))
				case DeleteFiles => Some(checkFileOwnership(user, resource.get))
				case AddFilesMetadata => Some(checkFileOwnership(user, resource.get))

				case CreateDatasets => Some(checkDatasetOwnership(user, resource.get))
				case DeleteDatasets => Some(checkDatasetOwnership(user, resource.get))
				case AddDatasetsMetadata => Some(checkDatasetOwnership(user, resource.get))

				case CreateCollections => Some(checkCollectionOwnership(user, resource.get))
				case DeleteCollections => Some(checkCollectionOwnership(user, resource.get))

				case CreateSpaces => Some(checkSpaceOwnership(user, resource.get))
				case DeleteSpaces => Some(checkSpaceOwnership(user, resource.get))

				case AddSections => Some(checkSectionOwnership(user, resource.get))
				
				case _ => None
			}
		} else {
			None
		}
	}
	
	/**
	 * Check to see if the user is the owner of the section pointed to by the resource.
	 * Works by checking the ownership of the file the section belongs to.
	 */
	def checkSectionOwnership(user: Option[Identity], resource: UUID): Boolean = {
		sections.get(resource) match {
			case Some(section) =>{
			  checkFileOwnership(user, section.file_id)
			}
			case None => {
				Logger.error("Section requested to be accessed not found. Denying request.")
				false
			}
		}
	}

	/**
	 * Check to see if the user is the owner of the file pointed to by the resource.
	 */
	def checkFileOwnership(user: Option[Identity], resource: UUID): Boolean = {
		(files.get(resource), user) match {
			case (Some(file), Some(u)) => file.author.identityId == u.identityId || checkUserAdmin(user)
			case (Some(file), None) => false
			case (None, _) => {
				Logger.error("File requested to be accessed not found. Denying request.")
				false
			}
		}
	}

	/**
	 * Check to see if the user is the owner of the dataset pointed to by the resource.
	 */
	def checkDatasetOwnership(user: Option[Identity], resource: UUID): Boolean = {
		(datasets.get(resource), user) match {
			case (Some(dataset), Some(u)) => dataset.author.identityId == u.identityId || checkUserAdmin(user)
			case (Some(dataset), None) => false
			case (None, _) => {
				Logger.error("Dataset requested to be accessed not found. Denying request.")
				false
			}
		}
	}

	/**
	 * Check to see if the user is the owner of the collection pointed to by the resource.
	 */
	def checkCollectionOwnership(user: Option[Identity], resource: UUID): Boolean = {
		collections.get(resource) match {
			case Some(collection) => {
				(collection.author, user) match {
					case (Some(a), Some(u)) => a.identityId == u.identityId || checkUserAdmin(user)
					case (Some(_), None) => false
					case (None, _) => {
						// FIXME should we allow collections created by anonymous?
						Logger.info("Requested collection is anonymous, anyone can modify, granting modification request.")
						true
					}
				}
			}
			case None => {
				Logger.error("Collection requested to be accessed not found. Denying request.")
				false
			}
		}
	}

	/**
	 * Check to see if the user is the owner of the space pointed to by the resource.
	 */
	def checkSpaceOwnership(user: Option[Identity], resource: UUID): Boolean = {
		(spaces.get(resource), user) match {
			case (Some(space), Some(u)) => true
			case (Some(space), None) => false
			case (None, _) => {
				Logger.error("Space requested to be accessed not found. Denying request.")
				false
			}
		}
	}
}
