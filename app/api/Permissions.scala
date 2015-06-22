package api

import models.{ResourceRef, User, UUID}
import play.api.Logger
import securesocial.core._
import play.api.mvc._
import play.api.Play.configuration
import services._

import scala.concurrent.Future

/**
 * List of all permissions used by the system to authorize users.
 */
object Permission extends Enumeration {
	val anonymous = new SocialUser(new IdentityId("anonymous", ""), "Anonymous", "User", "Anonymous User", None, None, AuthenticationMethod.UserPassword)

	type Permission = Value

	// spaces
	val ViewSpace,
    CreateSpace,
    DeleteSpace,
    EditSpace,
    
    // datasets
    ViewDataset,
    CreateDataset,
    DeleteDataset,
    EditDataset,
    
    // collections
    ViewCollection,
    CreateCollection,
    DeleteCollection,
    EditCollection,
    
    // files
    AddFile,
    DeleteFile,
    ViewFile,
    DownloadFiles,
    EditLicense,
    CreatePreview,    // Used by extractors
    MultimediaIndexDocument,
    CreateNote,
    
    // sections
    CreateSection,
    ViewSection,
    DeleteSection,   // FIXME: Unused right now
    EditSection,     // FIXME: Unused right now
    
    // metadata
    AddMetadata,
    ViewMetadata,
    DeleteMetadata, // FIXME: Unused right now
    EditMetadata,   // FIXME: Unused right now
    
    // social annotation
    AddTag,
    DeleteTag,
    ViewTags,
    AddComment,
    ViewComments,   // FIXME: Unused right now
    DeleteComment,
    EditComment,
    
    // geostreaming api
	  AddGeoStream,
	  ViewGeoStream,
	  DeleteGeoStream,
	  AddDatapoints,

    // users
    ViewUser,
    EditUser = Value

	def checkServerAdmin(user: Option[Identity]) = {
		user.exists(u => u.email.nonEmpty && AppConfiguration.checkAdmin(u.email.get))
	}

	def checkPermission(user: Option[Identity], permission: Permission, resourceRef: Option[ResourceRef] = None) = {
    configuration(play.api.Play.current).getString("permissions").getOrElse("public") match {
      case "public"  => true
      case "private" => checkServerAdmin(user)
      case "admin"   => checkServerAdmin(user)
      case _         => checkServerAdmin(user)
    }
	}
}

/**
 * A request that adds the User for the current call
 */
case class UserRequest[A](user: Option[Identity], mediciUser: Option[User], superAdmin: Boolean = false, request: Request[A]) extends WrappedRequest[A](request)
//
//
//import api.Permission._
//
//
///**
// * Specific implementation of an Authorization
// */
//case class WithPermission(permission: Permission) extends Authorization {
//
//  val files: FileService = services.DI.injector.getInstance(classOf[FileService])
//  val datasets: DatasetService = services.DI.injector.getInstance(classOf[DatasetService])
//	val collections: CollectionService = services.DI.injector.getInstance(classOf[CollectionService])
//  val sections: SectionService = services.DI.injector.getInstance(classOf[SectionService])
//	val spaces: SpaceService = services.DI.injector.getInstance(classOf[SpaceService])
//
//	def isAuthorized(user: Identity): Boolean = {
//		isAuthorized(if (user == null) None else Some(user), None)
//	}
//
//	def isAuthorized(user: Identity, resource: Option[UUID]): Boolean = {
//		isAuthorized(if (user == null) None else Some(user), resource)
//	}
//
//	def isAuthorized(user: Option[Identity], resource: Option[UUID] = None): Boolean = {
//    if (permission == Public) {
//			// public pages are always visible
//      true
//    } else if (permission == Admin) {
//			// user admin always requires admin privileges
//			checkUserAdmin(user)
//		} else {
//			// first check to see if user has the right permission for the resource
//			checkResourceOwnership(user, permission, resource) match {
//				case Some(b) => b
//				case None => {
//					// based on permissions pick right check,
//					configuration(play.api.Play.current).getString("permissions").getOrElse("public") match {
//						case "public"  => publicPermission(user, permission, resource)
//						case "private" => privatePermission(user, permission, resource)
//						case "admin"   => adminPermission(user, permission, resource)
//						case _         => adminPermission(user, permission, resource)
//					}
//				}
//			}
//    }
//  }
//
//  /**
//   * All read-only actions are public, writes and admin require a login. This is the most
//   * open configuration.
//   */
//  def publicPermission(user: Option[Identity], permission: Permission, resource: Option[UUID]): Boolean = {
//    // order is important
//    (user, permission, resource) match {
//      // anybody can list/show
//      case (_, ViewSpace, _)        => true
//      case (_, ViewCollection, _)   => true
//      case (_, Public, _)           => true
//      case (_, ViewDataset, _)      => true
//      case (_, ViewDataset, _)      => true
//      case (_, ViewFile, _)         => true
//      case (_, ViewSection, _)      => true
//      case (_, Public, _)           => true
//      case (_, ViewFile, _)         => true
//      case (_, ViewMetadata, _)     => true
//      case (_, ViewMetadata, _)     => true
//      case (_, ViewSpace, _)        => true
//      case (_, GSViewDatapoints, _) => true
//      case (_, GSViewSensor, _)     => true
//      case (_, GSViewSensor, _)     => true
//      case (_, GSViewSensor, _)     => true
//      case (_, ViewMetadata, _)     => true
//      case (_, ViewTags, _)         => true
//
//      // FIXME: required by ShowDataset if preview uses original file
//      // FIXME:  Needs to be here, as plugins called by browsers for previewers (Java, Acrobat, Quicktime for QTVR) cannot for now use cookies to authenticate as users.
//      case (_, DownloadFiles, _)    => true
//
//			// all other permissions require authenticated user
//			case (Some(u), _, _)          => true
//
//			// not logged in results in permission denied
//			case (None, _, _)             => false
//    }
//  }
//
//  /**
//   * All actions require a login, once logged in all users have all permission.
//   */
//  def privatePermission(user: Option[Identity], permission: Permission, resource: Option[UUID]): Boolean = {
//		Logger.debug("Private permissions " + user + " " + permission + " " + resource)
//    (user, permission, resource) match {
//			// all permissions require authenticated user
//			case (Some(u), _, _) => true
//
//			// not logged in results in permission denied
//			case (None, _, _)  => false
//    }
//  }
//
//  /**
//   * All actions require a login, admin actions require user to be in admin list.
//   */
//  def adminPermission(user: Option[Identity], permission: Permission, resource: Option[UUID]): Boolean = {
//    (user, permission, resource) match {
//			// check to see if user has admin rights
//			case (_, Permission.Admin, _) => checkUserAdmin(user)
//
//			// all permissions require authenticated user
//			case (Some(u), _, _) => true
//
//			// not logged in results in permission denied
//			case (None, _, _)  => false
//    }
//  }
//
//	/**
//	 * Check to see if the user is logged in and is an admin.
//	 */
//	def checkUserAdmin(user: Option[Identity]): Boolean = {
//		user match {
//			case Some(u) => u.email.nonEmpty && AppConfiguration.checkAdmin(u.email.get)
//			case None => false
//		}
//	}
//
//	/**
//	 * If only owners can modify an object, check to see if the user logged in
//	 * is the owner of the resource. This will return None if no check was done
//	 * or true/false if the user can or can not access the resource.
//	 */
//	def checkResourceOwnership(user: Option[Identity], permission: Permission, resource: Option[UUID]): Option[Boolean] = {
//		if (resource.isDefined && configuration(play.api.Play.current).getBoolean("ownerOnly").getOrElse(false)) {
//			// only check if resource is defined and we want owner only permissions
//			permission match {
//				case AddFile => Some(checkFileOwnership(user, resource.get))
//				case DeleteFile => Some(checkFileOwnership(user, resource.get))
//				case AddMetadata => Some(checkFileOwnership(user, resource.get))
//
//				case CreateDataset => Some(checkDatasetOwnership(user, resource.get))
//				case DeleteDataset => Some(checkDatasetOwnership(user, resource.get))
//				case AddMetadata => Some(checkDatasetOwnership(user, resource.get))
//
//				case CreateCollection => Some(checkCollectionOwnership(user, resource.get))
//				case DeleteCollection => Some(checkCollectionOwnership(user, resource.get))
//
//				case CreateSpace => Some(checkSpaceOwnership(user, resource.get))
//				case DeleteSpace => Some(checkSpaceOwnership(user, resource.get))
//
//				case CreateSection => Some(checkSectionOwnership(user, resource.get))
//
//				case _ => None
//			}
//		} else {
//			None
//		}
//	}
//
//	/**
//	 * Check to see if the user is the owner of the section pointed to by the resource.
//	 * Works by checking the ownership of the file the section belongs to.
//	 */
//	def checkSectionOwnership(user: Option[Identity], resource: UUID): Boolean = {
//		sections.get(resource) match {
//			case Some(section) =>{
//			  checkFileOwnership(user, section.file_id)
//			}
//			case None => {
//				Logger.error("Section requested to be accessed not found. Denying request.")
//				false
//			}
//		}
//	}
//
//	/**
//	 * Check to see if the user is the owner of the file pointed to by the resource.
//	 */
//	def checkFileOwnership(user: Option[Identity], resource: UUID): Boolean = {
//		(files.get(resource), user) match {
//			case (Some(file), Some(u)) => file.author.identityId == u.identityId || checkUserAdmin(user)
//			case (Some(file), None) => false
//			case (None, _) => {
//				Logger.error("File requested to be accessed not found. Denying request.")
//				false
//			}
//		}
//	}
//
//	/**
//	 * Check to see if the user is the owner of the dataset pointed to by the resource.
//	 */
//	def checkDatasetOwnership(user: Option[Identity], resource: UUID): Boolean = {
//		(datasets.get(resource), user) match {
//			case (Some(dataset), Some(u)) => dataset.author.identityId == u.identityId || checkUserAdmin(user)
//			case (Some(dataset), None) => false
//			case (None, _) => {
//				Logger.error("Dataset requested to be accessed not found. Denying request.")
//				false
//			}
//		}
//	}
//
//	/**
//	 * Check to see if the user is the owner of the collection pointed to by the resource.
//	 */
//	def checkCollectionOwnership(user: Option[Identity], resource: UUID): Boolean = {
//		collections.get(resource) match {
//			case Some(collection) => {
//				(collection.author, user) match {
//					case (Some(a), Some(u)) => a.identityId == u.identityId || checkUserAdmin(user)
//					case (Some(_), None) => false
//					case (None, _) => {
//						// FIXME should we allow collections created by anonymous?
//						Logger.info("Requested collection is anonymous, anyone can modify, granting modification request.")
//						true
//					}
//				}
//			}
//			case None => {
//				Logger.error("Collection requested to be accessed not found. Denying request.")
//				false
//			}
//		}
//	}
//
//	/**
//	 * Check to see if the user is the owner of the space pointed to by the resource.
//	 */
//	def checkSpaceOwnership(user: Option[Identity], resource: UUID): Boolean = {
//		(spaces.get(resource), user) match {
//			case (Some(space), Some(u)) => true
//			case (Some(space), None) => false
//			case (None, _) => {
//				Logger.error("Space requested to be accessed not found. Denying request.")
//				false
//			}
//		}
//	}
//}
//
//case class PermissionAccess(permission: Permission) extends Authorization {
//	val spaces: SpaceService = services.DI.injector.getInstance(classOf[SpaceService])
//	val collections: CollectionService = services.DI.injector.getInstance(classOf[CollectionService])
//	val datasets: DatasetService = services.DI.injector.getInstance(classOf[DatasetService])
//	val files: FileService = services.DI.injector.getInstance(classOf[FileService])
//	val sections: SectionService = services.DI.injector.getInstance(classOf[SectionService])
//
//	def isAuthorized(user: Identity): Boolean = {
//		isAuthorized(Option(user), None)
//	}
//
//	def isAuthorized(user: Identity, resourceRef: Option[ResourceRef]): Boolean = {
//		isAuthorized(Option(user), resourceRef)
//	}
//
//	def isAuthorized(user: Option[Identity], resourceRef: Option[ResourceRef] = None): Boolean = {
//		checkResourceOwnership(user, resourceRef) || checkResourcePermission(user, permission, resourceRef)
//	}
//
//	/**
//	 * See if user is owner of the object
//	 */
//	def checkResourceOwnership(user: Option[Identity], resourceRef: Option[ResourceRef]): Boolean = {
//		user match {
//			case Some(u) => {
//				resourceRef match {
//					case Some(ResourceRef(ResourceRef.collection, id)) => {
//						collections.get(id).exists(x => x.author.exists(_.identityId == u.identityId))
//					}
//					case Some(ResourceRef(ResourceRef.dataset, id)) => {
//						datasets.get(id).exists(_.author.identityId == u.identityId)
//					}
//					case Some(ResourceRef(ResourceRef.file, id)) => {
//						files.get(id).exists(_.author.identityId == u.identityId)
//					}
//					case Some(ResourceRef(resType, id)) => {
//						Logger.warn(s"Do not know how to handle $resType")
//						false
//					}
//					case None => false
//				}
//			}
//			case None => false
//		}
//	}
//
//	def checkResourcePermission(user: Option[Identity], permission: Permission, resourceRef: Option[ResourceRef]): Boolean = {
//		false
//	}
//}
//
//case class PublicAccess() extends Authorization {
//	def isAuthorized(user: Identity): Boolean = true
//}
//
//case class ServerAccess() extends Authorization {
//	def isAuthorized(user: Identity): Boolean = {
//		if (configuration(play.api.Play.current).getString("permissions").exists(_ == "public")) {
//			true
//		} else {
//			user != null
//		}
//	}
//}
//
//case class ServerAdminAccess() extends Authorization {
//	def isAuthorized(user: Identity): Boolean = {
//		if (user == null) {
//			false
//		} else {
//			user.email.exists(AppConfiguration.checkAdmin)
//		}
//	}
//}
//
