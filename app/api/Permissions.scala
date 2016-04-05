package api

import models.{ResourceRef, User}
import play.api.Logger
import play.api.mvc._
import play.api.Play.configuration
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
    AddResourceToSpace,
    EditStagingArea,

    // datasets
    ViewDataset,
    CreateDataset,
    DeleteDataset,
    EditDataset,
    AddResourceToDataset,
    ExecuteOnDataset,

    // collections
    ViewCollection,
    CreateCollection,
    DeleteCollection,
    EditCollection,
    AddResourceToCollection,

    // files
    AddFile,
    EditFile,
    DeleteFile,
    ViewFile,
    DownloadFiles,
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

    // users
    ViewUser,
    EditUser = Value

  val READONLY = Set[Permission](ViewCollection, ViewComments, ViewDataset, ViewFile, ViewGeoStream, ViewMetadata,
    ViewSection, ViewSpace, ViewTags, ViewUser)

  lazy val files: FileService = DI.injector.getInstance(classOf[FileService])
  lazy val previews: PreviewService = DI.injector.getInstance(classOf[PreviewService])
  lazy val relations: RelationService = DI.injector.getInstance(classOf[RelationService])
  lazy val collections: CollectionService = DI.injector.getInstance(classOf[CollectionService])
  lazy val datasets: DatasetService = DI.injector.getInstance(classOf[DatasetService])
  lazy val spaces: SpaceService = DI.injector.getInstance(classOf[SpaceService])
  lazy val folders: FolderService = DI.injector.getInstance(classOf[FolderService])
  lazy val users: services.UserService = DI.injector.getInstance(classOf[services.UserService])
  lazy val comments: services.CommentService = DI.injector.getInstance(classOf[services.CommentService])
  lazy val curations: services.CurationService = DI.injector.getInstance(classOf[services.CurationService])
  lazy val sections: SectionService = DI.injector.getInstance(classOf[SectionService])
  lazy val metadatas: MetadataService = DI.injector.getInstance(classOf[MetadataService])

  /** Returns true if the user is listed as a server admin */
	def checkServerAdmin(user: Option[User]): Boolean = {
		user.exists(u => u.active && u.admin)
	}

  /** Returns true if the user is the owner of the resource, this function is used in the code for checkPermission as well. */
  def checkOwner(user: Option[User], resourceRef: ResourceRef): Boolean = {
    user.exists(checkOwner(_, resourceRef))
  }

  /** Returns true if the user is the owner of the resource, this function is used in the code for checkPermission as well. */
  def checkOwner(user: User, resourceRef: ResourceRef): Boolean = {
    resourceRef match {
      case ResourceRef(ResourceRef.file, id) => files.get(id).exists(x => users.findById(x.author.id).exists(_.id == user.id))
      case ResourceRef(ResourceRef.collection, id) => collections.get(id).exists(x => users.findById(x.author.id).exists(_.id == user.id))
      case ResourceRef(ResourceRef.dataset, id) => datasets.get(id).exists(x => users.findById(x.author.id).exists(_.id == user.id))
      case ResourceRef(ResourceRef.space, id) => spaces.get(id).exists(_.creator == user.id)
      case ResourceRef(ResourceRef.comment, id) => comments.get(id).exists(x => users.findById(x.author.id).exists(_.id == user.id))
      case ResourceRef(ResourceRef.curationObject, id) => curations.get(id).exists(x => users.findById(x.author.id).exists(_.id == user.id))
      case ResourceRef(ResourceRef.curationFile, id) => curations.getCurationFiles(List(id)).exists(x => users.findById(x.author.id).exists(_.id == user.id))
      case ResourceRef(ResourceRef.metadata, id) => metadatas.getMetadataById(id).exists(_.creator.id == user.id)
      case ResourceRef(_, _) => false
    }
  }

  def checkPermission(permission: Permission)(implicit user: Option[User]): Boolean = {
    checkPermission(user, permission, None)
  }

  def checkPermission(permission: Permission, resourceRef: ResourceRef)(implicit user: Option[User]): Boolean = {
    checkPermission(user, permission, Some(resourceRef))
  }

  // TODO: figure out how to set type A and deal with the collision with checkPermission(implicit user: Option[User])
//  def checkPermission[A](permission: Permission)(implicit request: UserRequest[A]): Boolean = {
//    checkPermission(request.user, permission, None)
//  }
//  def checkPermission[A](permission: Permission, resourceRef: ResourceRef)(implicit request: UserRequest[A]): Boolean = {
//    checkPermission(request.user, permission, Some(resourceRef))
//  }

  def checkPermission(user: Option[User], permission: Permission, resourceRef: ResourceRef): Boolean = {
    checkPermission(user, permission, Some(resourceRef))
  }

  def checkPermission(user: Option[User], permission: Permission, resourceRef: Option[ResourceRef] = None): Boolean = {
    (user, configuration(play.api.Play.current).getString("permissions").getOrElse("public"), resourceRef) match {
      case (Some(u), "public", Some(r)) => {
        if (READONLY.contains(permission)) return true
        else checkPermission(u, permission, r)
      }
      case (Some(u), "private", Some(r)) => checkPermission(u, permission, r)
      case (Some(_), _, None) => true
      case (None, "private", Some(res)) => checkPrivatePermissions(permission, res)
      case (None, "public", _) => READONLY.contains(permission)
      case (_, p, _) => {
        Logger.error("Invalid permission scheme " + p)
        false
      }
    }
  }

  def checkPrivatePermissions(permission: Permission, resourceRef: ResourceRef): Boolean = {
    // if not readonly, don't let user in
    if (!READONLY.contains(permission)) return false
    // check specific resource
    resourceRef match {
      case ResourceRef(ResourceRef.file, id) => false
      case ResourceRef(ResourceRef.dataset, id) => false // TODO check if dataset is public datasets.get(r.id).isPublic()
      case ResourceRef(ResourceRef.collection, id) => false
      case ResourceRef(ResourceRef.space, id) => false
      case ResourceRef(ResourceRef.comment, id) => false
      case ResourceRef(ResourceRef.section, id) => false
      case ResourceRef(ResourceRef.preview, id) => false
      case ResourceRef(resType, id) => {
        Logger.error("Unrecognized resource type " + resType)
        false
      }
    }
  }

  def checkPermission(user: User, permission: Permission, resourceRef: ResourceRef): Boolean = {
    // check if user is owner, in that case they can do what they want.
    if (checkOwner(users.findByIdentity(user), resourceRef)) return true

    resourceRef match {
      case ResourceRef(ResourceRef.preview, id) => {
        previews.get(id) match {
          case Some(p) => {
            if (p.file_id.isDefined) {
              checkPermission(user, permission, ResourceRef(ResourceRef.file, p.file_id.get))
            } else if (p.section_id.isDefined) {
              checkPermission(user, permission, ResourceRef(ResourceRef.section, p.section_id.get))
            } else if (p.dataset_id.isDefined) {
              checkPermission(user, permission, ResourceRef(ResourceRef.dataset, p.dataset_id.get))
            } else if (p.collection_id.isDefined) {
              checkPermission(user, permission, ResourceRef(ResourceRef.collection, p.collection_id.get))
            } else {
              true
            }
          }
          case None => false
        }
      }
      case ResourceRef(ResourceRef.section, id) => {
        sections.get(id) match {
          case Some(s) => {
            checkPermission(user, permission, ResourceRef(ResourceRef.file, s.file_id))
          }
          case None => false
        }
      }
      case ResourceRef(ResourceRef.relation, id) => {
        relations.get(id) match {
          case Some(r) => {
            r.source.id == id.stringify || r.target.id == id.stringify
          }
          case None => false
        }
      }
      case ResourceRef(ResourceRef.file, id) => {
        for (clowderUser <- getUserByIdentity(user)) {
          datasets.findByFileId(id).foreach { dataset =>
            dataset.spaces.map{
              spaceId => for(role <- users.getUserRoleInSpace(clowderUser.id, spaceId)) {
                if(role.permissions.contains(permission.toString))
                  return true
              }
            }
          }
          folders.findByFileId(id).foreach { folder =>
            datasets.get(folder.parentDatasetId).foreach { dataset =>
              dataset.spaces.map {
                spaceId => for(role <- users.getUserRoleInSpace(clowderUser.id, spaceId)) {
                  if(role.permissions.contains(permission.toString))
                    return true
                }
              }
            }
          }
        }
        false
      }
      case ResourceRef(ResourceRef.dataset, id) => {
        datasets.get(id) match {
          case None => false
          case Some(dataset) => {
            for (clowderUser <- getUserByIdentity(user)) {
              dataset.spaces.map {
                spaceId => for (role <- users.getUserRoleInSpace(clowderUser.id, spaceId)) {
                  if (role.permissions.contains(permission.toString))
                    return true
                }
              }
            }
            false
          }
        }
      }
      case ResourceRef(ResourceRef.collection, id) => {
        collections.get(id) match {
          case None => false
          case Some(collection) => {
            for (clowderUser <- getUserByIdentity(user)) {
              collection.spaces.map {
                spaceId => for (role <- users.getUserRoleInSpace(clowderUser.id, spaceId)) {
                  if (role.permissions.contains(permission.toString))
                    return true
                }
              }
            }
            false
          }
        }
      }
      case ResourceRef(ResourceRef.space, id) => {
        spaces.get(id) match {
          case None => false
          case Some(space) => {
            val hasPermission: Option[Boolean] = for {clowderUser <- getUserByIdentity(user)
                                                      role <- users.getUserRoleInSpace(clowderUser.id, space.id)
                                                      if role.permissions.contains(permission.toString)
            } yield true
            hasPermission getOrElse(false)
          }
        }
      }
      case ResourceRef(ResourceRef.comment, id) => {
        val comment = comments.get(id)
        if(comment.get.dataset_id.isDefined) {
          for (clowderUser <- getUserByIdentity(user)) {
            for (dataset <- datasets.get(comment.get.dataset_id.get)) {
              dataset.spaces.map {
                spaceId => for (role <- users.getUserRoleInSpace(clowderUser.id, spaceId)) {
                  if (role.permissions.contains(permission.toString)) {
                    return true
                  }
                }
              }
            }
          }
        }
        else if(comment.get.file_id.isDefined) {
          val datasetList = datasets.findByFileId(comment.get.file_id.get)
          for (clowderUser <- getUserByIdentity(user)) {
            datasetList.flatMap(_.spaces).map {
              spaceId => for(role <- users.getUserRoleInSpace(clowderUser.id, spaceId)) {
                  if(role.permissions.contains(permission.toString)) {
                    return true
                  }
                }

            }
          }
        }
        false
      }

      case ResourceRef(ResourceRef.curationObject, id) => {
        curations.get(id) match {
          case Some(curation) => checkPermission(user, permission, ResourceRef(ResourceRef.space, curation.space))

          case None => false
        }
      }

      case ResourceRef(ResourceRef.curationFile, id) => {
        curations.getCurationByCurationFile(id) match {
          case Some(curation) => checkPermission(user, permission, ResourceRef(ResourceRef.space, curation.space))
          case None => false
        }
      }
      // for DeleteMetadata, the creator of this metadata or user with permission to delete this resource can delete metadata
      case ResourceRef(ResourceRef.metadata, id) => {
        metadatas.getMetadataById(id) match {
          case Some(m) => checkPermission(user, permission, m.attachedTo)
          case None => false
        }
      }

      case ResourceRef(resType, id) => {
        Logger.error("Resource type not recognized " + resType)
        false
      }
    }
  }

  def getUserByIdentity(identity: User): Option[User] = users.findByIdentity(identity)

  def checkPrivateServer(user: Option[User]): Boolean = {
    configuration(play.api.Play.current).getString("permissions").getOrElse("public") == "public" || user.isDefined
  }
}

/**
 * A request that adds the User for the current call
 */
case class UserRequest[A](user: Option[User], superAdmin: Boolean = false, request: Request[A]) extends WrappedRequest[A](request)
