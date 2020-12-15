package api

import models.{UUID, ResourceRef, User, UserStatus}
import play.api.{Logger, Configuration}
import play.api.mvc._
import services._

/**
 * List of all permissions used by the system to authorize users.
 */
object Permission extends Enumeration {
  type Permission = Value

	// spaces
	val ViewSpace,
    CreateSpace,
    DeleteSpace,
    EditSpace,
    PublicSpace,
    AddResourceToSpace,
    RemoveResourceFromSpace,
    EditStagingArea,

    // datasets
    ViewDataset,
    CreateDataset,
    DeleteDataset,
    EditDataset,
    PublicDataset,
    AddResourceToDataset,
    RemoveResourceFromDataset,
    ExecuteOnDataset,

    // collections
    ViewCollection,
    CreateCollection,
    DeleteCollection,
    EditCollection,
    AddResourceToCollection,
    RemoveResourceFromCollection,

    // files
    AddFile,
    EditFile,
    DeleteFile,
    ViewFile,
    DownloadFiles,
    ArchiveFile,
    EditLicense,
    CreatePreview,    // Used by extractors
    MultimediaIndexDocument,

    // sections
    CreateSection,
    ViewSection,
    DeleteSection,   // FIXME: Unused right now
    EditSection,     // FIXME: Unused right now

    // metadata
    AddMetadata,
    ViewMetadata,
    DeleteMetadata,
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
    CreateSensor,
    ViewSensor,
    DeleteSensor,
	  AddGeoStream,
	  ViewGeoStream,
	  DeleteGeoStream,
	  AddDatapoints,

    // relations
    CreateRelation,
    ViewRelation,
    DeleteRelation,

    //vocabularies
    ViewVocabulary,
    CreateVocabulary,
    DeleteVocabulary,
    EditVocabulary,

    //VocabularyTerms
    ViewVocabularyTerm,
    CreateVocabularyTerm,
    DeleteVocabularyTerm,
    EditVocabularyTerm,

    // users
    ViewUser,
    EditUser = Value

  var READONLY = Set[Permission](ViewCollection, ViewComments, ViewDataset, ViewFile, ViewSensor, ViewGeoStream,
    ViewMetadata, ViewSection, ViewSpace, ViewTags, ViewUser , ViewVocabulary, ViewVocabularyTerm)

  if(configuration.get[Boolean]("allowAnonymousDownload")) {
     READONLY += DownloadFiles
  }

  val EDITOR_PERMISSIONS = READONLY ++ Set[Permission](CreateSpace, AddResourceToSpace, RemoveResourceFromSpace,
    CreateDataset, EditDataset, AddResourceToDataset, RemoveResourceFromDataset, ExecuteOnDataset, PublicDataset,
    CreateCollection, EditCollection, AddResourceToCollection, RemoveResourceFromCollection,
    AddFile, EditFile, DownloadFiles, EditLicense, CreatePreview, MultimediaIndexDocument,
    CreateSection, EditSection, DeleteSection,
    AddMetadata, EditMetadata, DeleteMetadata,
    AddTag, DeleteTag, AddComment, DeleteComment, EditComment,
    CreateSensor, DeleteSensor, AddGeoStream, DeleteGeoStream, AddDatapoints,
    CreateRelation, ViewRelation, DeleteRelation,
    CreateVocabulary, DeleteVocabulary, EditVocabulary,
    CreateVocabularyTerm, DeleteVocabularyTerm, EditVocabularyTerm
  )

  lazy val configuration: Configuration = DI.injector.getInstance(classOf[Configuration])

  /** Returns true if the user is listed as a server admin */
	def checkServerAdmin(user: Option[User]): Boolean = {
		user.exists(u => u.status==UserStatus.Admin)
	}

  /** Returns true if the user is the owner of the resource, this function is used in the code for checkPermission as well. */
  def checkOwner(user: Option[User], resourceRef: ResourceRef): Boolean = {
    user.exists(u => u.superAdminMode || checkOwner(u, resourceRef))
  }

  /** Returns true if the user is the owner of the resource, this function is used in the code for checkPermission as well. */
  def checkOwner(user: User, resourceRef: ResourceRef): Boolean = {
    resourceRef match {
      case ResourceRef(_, _) => false
    }
  }

  /** Returns true if the user is the owner of the resource, this function is used in the code for checkPermission as well. */
  def checkOwners(user: Option[User], resourceRefs: List[ResourceRef]): Map[ResourceRef,Boolean] = {
    user match {
      case Some(u) => {
        if (u.superAdminMode) {
          var results = Map.empty[ResourceRef,Boolean]
          resourceRefs.foreach(rr => results += (rr -> true))
          results
        } else {
          checkOwners(u, resourceRefs)
        }
      }
      case None => {
        var results = Map.empty[ResourceRef,Boolean]
        resourceRefs.foreach(rr => results += (rr -> false))
        results
      }
    }
  }

  /** Returns true if the user is the owner of the resource, this function is used in the code for checkPermission as well. */
  def checkOwners(user: User, resourceRefs: List[ResourceRef]): Map[ResourceRef,Boolean] = {
    var results = Map.empty[ResourceRef,Boolean]

    resourceRefs.foreach(r => {
      if (!results.get(r).isDefined) {
        results += (r -> (r match {
          case ResourceRef(_, _) => false
        }))
      }
    })

    results
  }


  // TODO: figure out how to set type A and deal with the collision with checkPermission(implicit user: Option[User])
//  def checkPermission[A](permission: Permission)(implicit request: UserRequest[A]): Boolean = {
//    checkPermission(request.user, permission, None)
//  }
//  def checkPermission[A](permission: Permission, resourceRef: ResourceRef)(implicit request: UserRequest[A]): Boolean = {
//    checkPermission(request.user, permission, Some(resourceRef))
//  }

  def checkPermission(user: Option[User], permission: Permission, resourceRef: ResourceRef): Boolean = {
    true
  }


  //check the permisssion when permission = private & user is anonymous.
  def checkAnonymousPrivatePermissions(permission: Permission, resourceRef: ResourceRef): Boolean = {
    // if not readonly, don't let user in
    if (!READONLY.contains(permission)) return false
    // check specific resource
    resourceRef match {
      case ResourceRef(resType, id) => {
        Logger.error("Unrecognized resource type " + resType)
        false
      }
    }
  }

  // check the permisssion when permission = private & user is anonymous.
  def checkAnonymousPrivatePermissionsList(permission: Permission, resourceRefs: List[ResourceRef]): PermissionsList = {
    var results = Map.empty[ResourceRef,Boolean]
    // if not readonly, don't let user in
    if (!READONLY.contains(permission)) {
      generatePermissionsList(resourceRefs, false)
    } else {
      resourceRefs.foreach(r => {
        if (!results.get(r).isDefined) {
          Logger.error("Unrecognized resource type " + r.resourceType)
          results += (r -> false)
        }
      })

      generatePermissionsList(results)
    }
  }

  /** on a private server this will return true iff user logged in, on public server this will always be true */
  def checkPrivateServer(user: Option[User]): Boolean = {
    configuration.get[String]("permissions") == "public" || user.isDefined
  }

  // Shortcut for changing Map to a PermissionsList object
  private def generatePermissionsList(results: Map[ResourceRef, Boolean]): PermissionsList = {
    val approved = results.filter(_._2 == true).map(_._1).toList
    val denied = results.filter(_._2 == false).map(_._1).toList
    PermissionsList(approved, denied, results)
  }

  // Shortcut for setting same boolean output to all resourceRefs
  private def generatePermissionsList(resourceRefs: List[ResourceRef], result: Boolean): PermissionsList = {
    var results = Map.empty[ResourceRef, Boolean]
    resourceRefs.foreach(rr => results += (rr -> result))
    generatePermissionsList(results)
  }
}

/**
 * A request that adds the User for the current call
 */
case class UserRequest[A](user: Option[User], request: Request[A], apiKey: Option[String]) extends WrappedRequest[A](request)

// For multi-lookups, provide convenience lists of true and false permissions
case class PermissionsList(approved: List[ResourceRef], denied: List[ResourceRef], lookup: Map[ResourceRef,Boolean])
