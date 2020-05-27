package api

import api.Permission.getUserByIdentity
import models.{UUID, ResourceRef, User, UserStatus}
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

  if( play.Play.application().configuration().getBoolean("allowAnonymousDownload")) {
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
  lazy val vocabularies: VocabularyService = DI.injector.getInstance(classOf[VocabularyService])
  lazy val vocabularyterms: VocabularyTermService = DI.injector.getInstance(classOf[VocabularyTermService])

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
      case ResourceRef(ResourceRef.file, id) => files.get(id).exists(x => users.findById(x.author.id).exists(_.id == user.id))
      case ResourceRef(ResourceRef.collection, id) => collections.get(id).exists(x => users.findById(x.author.id).exists(_.id == user.id))
      case ResourceRef(ResourceRef.dataset, id) => datasets.get(id).exists(x => users.findById(x.author.id).exists(_.id == user.id))
      case ResourceRef(ResourceRef.space, id) => spaces.get(id).exists(_.creator == user.id)
      case ResourceRef(ResourceRef.comment, id) => comments.get(id).exists(x => users.findById(x.author.id).exists(_.id == user.id))
      case ResourceRef(ResourceRef.curationObject, id) => curations.get(id).exists(x => users.findById(x.author.id).exists(_.id == user.id))
      case ResourceRef(ResourceRef.curationFile, id) => curations.getCurationFiles(List(id)).exists(x => users.findById(x.author.id).exists(_.id == user.id))
      case ResourceRef(ResourceRef.metadata, id) => metadatas.getMetadataById(id).exists(_.creator.id == user.id)
      case ResourceRef(ResourceRef.vocabulary, id) => vocabularies.get(id).exists(x => users.findByIdentity(x.author.get).exists(_.id == user.id))
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

    files.get(resourceRefs.filter(_.resourceType == ResourceRef.file).map(_.id)).found.foreach(x => {
      results += (ResourceRef('file, x.id) -> (x.author.id == user.id))
    })

    datasets.get(resourceRefs.filter(_.resourceType == ResourceRef.dataset).map(_.id)).found.foreach(x => {
      results += (ResourceRef('dataset, x.id) -> (x.author.id == user.id))
    })

    collections.get(resourceRefs.filter(_.resourceType == ResourceRef.collection).map(_.id)).found.foreach(x => {
      results += (ResourceRef('collection, x.id) -> (x.author.id == user.id))
    })

    spaces.get(resourceRefs.filter(_.resourceType == ResourceRef.space).map(_.id)).found.foreach(x => {
      results += (ResourceRef('space, x.id) -> (x.creator == user.id))
    })

    comments.get(resourceRefs.filter(_.resourceType == ResourceRef.comment).map(_.id)).found.foreach(x => {
      results += (ResourceRef('comment, x.id) -> (x.author.id == user.id))
    })

    resourceRefs.foreach(r => {
      if (!results.get(r).isDefined) {
        results += (r -> (r match {
          case ResourceRef(ResourceRef.curationObject, id) => curations.get(id).exists(x => users.findById(x.author.id).exists(_.id == user.id))
          case ResourceRef(ResourceRef.curationFile, id) => curations.getCurationFiles(List(id)).exists(x => users.findById(x.author.id).exists(_.id == user.id))
          case ResourceRef(ResourceRef.metadata, id) => metadatas.getMetadataById(id).exists(_.creator.id == user.id)
          case ResourceRef(ResourceRef.vocabulary, id) => vocabularies.get(id).exists(x => users.findByIdentity(x.author.get).exists(_.id == user.id))
          case ResourceRef(_, _) => false
        }))
      }
    })

    results
  }

  def checkPermission(permission: Permission)(implicit user: Option[User]): Boolean = {
    checkPermission(user, permission, None)
  }

  def checkPermission(permission: Permission, resourceRef: ResourceRef)(implicit user: Option[User]): Boolean = {
    checkPermission(user, permission, Some(resourceRef))
  }

  def checkPermissions(permission: Permission, resourceRefs: List[ResourceRef])(implicit user: Option[User]): PermissionsList = {
    checkPermissions(user, permission, resourceRefs)
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
        if (READONLY.contains(permission))
          true
        else
          checkPermission(u, permission, r)
      }
      case (Some(u), "private", Some(r)) => {
        if (configuration(play.api.Play.current).getBoolean("makeGeostreamsPublicReadable").getOrElse(true) && permission == Permission.ViewGeoStream)
          true
        else
          checkPermission(u, permission, r)
      }
      case (Some(_), _, None) => true
      case (None, "private", Some(res)) => checkAnonymousPrivatePermissions(permission, res)
      case (None, "public", _) => READONLY.contains(permission)
      case (None, "private", None) => {
        if (configuration(play.api.Play.current).getBoolean("makeGeostreamsPublicReadable").getOrElse(true) && permission == Permission.ViewGeoStream)
          true
        else {
          Logger.debug(s"Private, no user, no resourceRef, permission=${permission}", new Exception())
          false
        }
      }
      case (_, p, _) => {
        Logger.error(s"Invalid permission scheme ${p} [user=${user}, permission=${permission}, resourceRef=${resourceRef}]")
        false
      }
    }
  }

  def checkPermissions(user: Option[User], permission: Permission, resourceRefs: List[ResourceRef]): PermissionsList = {
    (user, configuration(play.api.Play.current).getString("permissions").getOrElse("public"), resourceRefs) match {
      case (Some(u), "public", r) => {
        if (READONLY.contains(permission))
          generatePermissionsList(r, true)
        else
          checkPermissions(u, permission, r)
      }
      case (Some(u), "private", r) => {
        if (configuration(play.api.Play.current).getBoolean("makeGeostreamsPublicReadable").getOrElse(true) && permission == Permission.ViewGeoStream)
          generatePermissionsList(r, true)
        else
          checkPermissions(u, permission, r)
      }
      case (None, "private", r) => checkAnonymousPrivatePermissionsList(permission, r)
      case (None, "public", r) => generatePermissionsList(r, READONLY.contains(permission))
      case (_, p, r) => {
        Logger.error(s"Invalid permission scheme ${p} [user=${user}, permission=${permission}, resourceRefs=${resourceRefs}]")
        generatePermissionsList(r, false)
      }
    }
  }

  //check the permisssion when permission = private & user is anonymous.
  def checkAnonymousPrivatePermissions(permission: Permission, resourceRef: ResourceRef): Boolean = {
    // if not readonly, don't let user in
    if (!READONLY.contains(permission)) return false
    // check specific resource
    resourceRef match {
      case ResourceRef(ResourceRef.file, id) => {
        (datasets.get(folders.findByFileId(id).map(_.parentDatasetId)).found ++ datasets.findByFileIdDirectlyContain(id)) match {
          case dataset :: _ => dataset.isPublic || (dataset.isDefault && dataset.spaces.find(sId => spaces.get(sId).exists(_.isPublic)).nonEmpty)
          case Nil => false
        }
      }
      case ResourceRef(ResourceRef.dataset, id) => datasets.get(id).exists(dataset => dataset.isPublic || (dataset.isDefault && dataset.spaces.find(sId => spaces.get(sId).exists(_.isPublic)).nonEmpty)) // TODO check if dataset is public datasets.get(r.id).isPublic()
      case ResourceRef(ResourceRef.collection, id) =>  collections.get(id).exists(collection => collection.spaces.find(sId => spaces.get(sId).exists(_.isPublic)).nonEmpty)
      case ResourceRef(ResourceRef.space, id) => spaces.get(id).exists(s => s.isPublic || datasets.listSpaceStatus(1,s.id.stringify,"public").nonEmpty)
      case ResourceRef(ResourceRef.comment, id) => {
        comments.get(id) match {
          case Some(comment) => {
            (comment.dataset_id, comment.file_id) match {
              case (Some(d_id), None) => checkAnonymousPrivatePermissions(permission, ResourceRef(ResourceRef.dataset, d_id))
              case (None, Some(f_id)) => checkAnonymousPrivatePermissions(permission, ResourceRef(ResourceRef.file, f_id))
              case (_, _) => false
            }
          }
          case None => false
        }
      }
      case ResourceRef(ResourceRef.section, id) => {
        sections.get(id) match {
          case Some(s) => {
            checkAnonymousPrivatePermissions(permission, ResourceRef(ResourceRef.file, s.file_id))
          }
          case None => false
        }
      }
      case ResourceRef(ResourceRef.preview, id) => {
        previews.get(id) match {
          case Some(preview) => {
            (preview.file_id, preview.dataset_id, preview.collection_id, preview.section_id) match {
              case (Some(f_id), None, None, None) => checkAnonymousPrivatePermissions(permission, ResourceRef(ResourceRef.file, f_id))
              case (None, Some(d_id), None, None) => checkAnonymousPrivatePermissions(permission, ResourceRef(ResourceRef.dataset, d_id))
              case (None, None, Some(c_id), None) => checkAnonymousPrivatePermissions(permission, ResourceRef(ResourceRef.collection, c_id))
              case (None, None, None, Some(p_id)) => checkAnonymousPrivatePermissions(permission, ResourceRef(ResourceRef.section, p_id))
              case (_, _, _, _) => false
            }
          }
          case None => false
        }
      }
      case ResourceRef(ResourceRef.thumbnail, id) => true
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
      // create mappings of resourceref to permission
      files.get(resourceRefs.filter(_.resourceType == ResourceRef.file).map(_.id)).found.foreach(fi => {
        results += (ResourceRef('file, fi.id) -> (datasets.get(folders.findByFileId(fi.id).map(_.parentDatasetId)).found ++ datasets.findByFileIdDirectlyContain(fi.id))
          .exists(dataset => dataset.isPublic || (dataset.isDefault && spaces.get(dataset.spaces).found.exists(_.isPublic))))
      })

      datasets.get(resourceRefs.filter(_.resourceType == ResourceRef.dataset).map(_.id)).found.foreach(dataset => {
        results += (ResourceRef('dataset, dataset.id) -> (dataset.isPublic || (dataset.isDefault && spaces.get(dataset.spaces).found.exists(_.isPublic))))
      })

      collections.get(resourceRefs.filter(_.resourceType == ResourceRef.collection).map(_.id)).found.foreach(collection => {
        results += (ResourceRef('collection, collection.id) -> spaces.get(collection.spaces).found.exists(_.isPublic))
      })

      spaces.get(resourceRefs.filter(_.resourceType == ResourceRef.space).map(_.id)).found.foreach(space => {
        results += (ResourceRef('collection, space.id) -> (space.isPublic || datasets.listSpaceStatus(1,space.id.stringify,"public").nonEmpty))
      })

      comments.get(resourceRefs.filter(_.resourceType == ResourceRef.comment).map(_.id)).found.foreach(comment => {
        results += (ResourceRef('comment, comment.id) -> ((comment.dataset_id, comment.file_id) match {
          case (Some(d_id), None) => checkAnonymousPrivatePermissions(permission, ResourceRef(ResourceRef.dataset, d_id))
          case (None, Some(f_id)) => checkAnonymousPrivatePermissions(permission, ResourceRef(ResourceRef.file, f_id))
          case (_, _) => false
        }))
      })

      sections.get(resourceRefs.filter(_.resourceType == ResourceRef.section).map(_.id)).found.foreach(s => {
        results += (ResourceRef('section, s.id) -> checkAnonymousPrivatePermissions(permission, ResourceRef(ResourceRef.file, s.file_id)))
      })

      previews.get(resourceRefs.filter(_.resourceType == ResourceRef.preview).map(_.id)).found.foreach(preview => {
        results += (ResourceRef('preview, preview.id) -> ((preview.file_id, preview.dataset_id, preview.collection_id, preview.section_id) match {
          case (Some(f_id), None, None, None) => checkAnonymousPrivatePermissions(permission, ResourceRef(ResourceRef.file, f_id))
          case (None, Some(d_id), None, None) => checkAnonymousPrivatePermissions(permission, ResourceRef(ResourceRef.dataset, d_id))
          case (None, None, Some(c_id), None) => checkAnonymousPrivatePermissions(permission, ResourceRef(ResourceRef.collection, c_id))
          case (None, None, None, Some(p_id)) => checkAnonymousPrivatePermissions(permission, ResourceRef(ResourceRef.section, p_id))
          case (_, _, _, _) => false
        }))
      })

      resourceRefs.filter(_.resourceType == ResourceRef.thumbnail).foreach(r => {
        results += (r-> true)
      })

      resourceRefs.foreach(r => {
        if (!results.get(r).isDefined) {
          Logger.error("Unrecognized resource type " + r.resourceType)
          results += (r -> false)
        }
      })

      generatePermissionsList(results)
    }
  }

  def checkPermission(user: User, permission: Permission, resourceRef: ResourceRef): Boolean = {
    // check if user is owner, in that case they can do what they want.
    if (user.superAdminMode) return true
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
            }  else  {
              true
            }
          }
          case None => false
        }
      }
      case ResourceRef(ResourceRef.section, id) => {
        sections.get(id) match {
          case Some(s) => checkPermission(user, permission, ResourceRef(ResourceRef.file, s.file_id))
          case None => false
        }
      }
      case ResourceRef(ResourceRef.relation, id) => {
        relations.get(id) match {
          case Some(r) => r.source.id == id.stringify || r.target.id == id.stringify
          case None => false
        }
      }
      case ResourceRef(ResourceRef.file, id) => {
        for (clowderUser <- getUserByIdentity(user)) {
          datasets.findByFileIdDirectlyContain(id).foreach { dataset =>
            if ((dataset.isPublic || (dataset.isDefault && dataset.spaces.find(sid => spaces.get(sid).exists(_.isPublic)).nonEmpty))
              && READONLY.contains(permission)) return true
            dataset.spaces.map {
              spaceId => for(role <- users.getUserRoleInSpace(clowderUser.id, spaceId)) {
                if(role.permissions.contains(permission.toString))
                  return true
              }
            }
          }
          datasets.get(folders.findByFileId(id).map{_.parentDatasetId}).found.foreach { dataset =>
            if ((dataset.isPublic || (dataset.isDefault && dataset.spaces.find(sid => spaces.get(sid).exists(_.isPublic)).nonEmpty))
              && READONLY.contains(permission)) return true
            dataset.spaces.map {
              spaceId => for(role <- users.getUserRoleInSpace(clowderUser.id, spaceId)) {
                if(role.permissions.contains(permission.toString))
                  return true
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
            if (dataset.trash){
              if (permission.equals(Permission.DeleteDataset)){
                for (clowderUser <- getUserByIdentity(user)) {
                  dataset.spaces.map {
                    spaceId => for (role <- users.getUserRoleInSpace(clowderUser.id, spaceId)) {
                      if (role.permissions.contains(permission.toString))
                        return true
                    }
                  }
                }
              }
            } else {
              if ((dataset.isPublic || (dataset.isDefault && dataset.spaces.find(sid => spaces.get(sid).exists(_.isPublic)).nonEmpty))
                && READONLY.contains(permission)) return true
              for (clowderUser <- getUserByIdentity(user)) {
                dataset.spaces.map {
                  spaceId => for (role <- users.getUserRoleInSpace(clowderUser.id, spaceId)) {
                    if (role.permissions.contains(permission.toString))
                      return true
                  }
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
            if (collection.trash){
              if (permission.equals(Permission.DeleteCollection)){
                for (clowderUser <- getUserByIdentity(user)) {
                  collection.spaces.map {
                    spaceId =>
                      for (role <- users.getUserRoleInSpace(clowderUser.id, spaceId)) {
                        if (role.permissions.contains(permission.toString))
                          return true
                      }
                  }
                }
              }
            } else {
              if ((collection.spaces.find(sid => spaces.get(sid).exists(_.isPublic)).nonEmpty)
                && READONLY.contains(permission)) return true
              for (clowderUser <- getUserByIdentity(user)) {
                collection.spaces.map {
                  spaceId => for (role <- users.getUserRoleInSpace(clowderUser.id, spaceId)) {
                    if (role.permissions.contains(permission.toString))
                      return true
                  }
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
            if (space.isPublic && READONLY.contains(permission)) return true
            val hasPermission: Option[Boolean] = for {clowderUser <- getUserByIdentity(user)
                                                      role <- users.getUserRoleInSpace(clowderUser.id, space.id)
                                                      if role.permissions.contains(permission.toString)
            } yield true
            hasPermission getOrElse(false)
          }
        }
      }
      case ResourceRef(ResourceRef.vocabulary, id) => {
        vocabularies.get(id) match {
          case None => false
          case Some(vocab) => {
            for (clowderUser <- getUserByIdentity(user)) {
              vocab.spaces.map {
                vocabId => for (role <- users.getUserRoleInSpace(clowderUser.id, vocabId)) {
                  if (role.permissions.contains(permission.toString))
                    return true
                }
              }
            }
            false
          }
        }
      }
      case ResourceRef(ResourceRef.vocabularyterm, id) => {
        vocabularyterms.get(id) match {
          case None => false
          case Some(vocabterm) => {
            for (clowderUser <- getUserByIdentity(user)) {
              vocabterm.spaces.map {
                vocabTermId => for (role <- users.getUserRoleInSpace(clowderUser.id, vocabTermId)) {
                  if (role.permissions.contains(permission.toString))
                    return true
                }
              }
            }
            false
          }
        }
      }
      case ResourceRef(ResourceRef.comment, id) => {
        comments.get(id) match {
          case Some(comment) => {
            (comment.dataset_id, comment.file_id) match {
              case (Some(d_id), None) => checkPermission(user, permission, ResourceRef(ResourceRef.dataset, d_id))
              case (None, Some(f_id)) => checkPermission(user, permission, ResourceRef(ResourceRef.file, f_id))
              case (_, _) => false
            }
          }
          case None => false
        }
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

      case ResourceRef(ResourceRef.user, id) => {
        users.get(id) match {
          case Some(u) => {
            if (id == user.id) {
              true
            } else {
              var returnValue = false
              u.spaceandrole.map { space_role =>
                if (space_role.role.permissions.contains(permission.toString)) {
                  returnValue =  true
                }
              }
              returnValue
            }
          }
          case None => false
        }
      }

      case ResourceRef(ResourceRef.thumbnail, id) => true

      case ResourceRef(resType, id) => {
        Logger.error("Resource type not recognized " + resType)
        false
      }
    }
  }

  def checkPermissions(user: User, permission: Permission, resourceRefs: List[ResourceRef]): PermissionsList = {
    // check if user is owner, in that case they can do what they want.
    if (user.superAdminMode)
      generatePermissionsList(resourceRefs, true)

    var results = checkOwners(users.findByIdentity(user), resourceRefs)
    val leftover_results = results.filter(_._2 == false).map(_._1).toList
    if (leftover_results.length == 0)
      return generatePermissionsList(results)

    val ident = getUserByIdentity(user)

    leftover_results.filter(_.resourceType == ResourceRef.file).foreach(x => {
      var result = false

      ident match {
        case Some(i) => {
          // Check permissions of dataset & its space
          datasets.findByFileIdDirectlyContain(x.id).foreach(dataset => {
            if ((dataset.isPublic || (dataset.isDefault && spaces.get(dataset.spaces).found.exists(_.isPublic)))
              && READONLY.contains(permission))
              result = true
            if (!result)
              dataset.spaces.map(spaceId => {
                for (role <- users.getUserRoleInSpace(i.id, spaceId)) {
                  if (role.permissions.contains(permission.toString))
                    result = true
                }
              })
          })
          // Check permissions of folder's dataset & its space
          if (!result)
            datasets.get(folders.findByFileId(x.id).map{_.parentDatasetId}).found.foreach(dataset => {
              if ((dataset.isPublic || (dataset.isDefault && spaces.get(dataset.spaces).found.exists(_.isPublic)))
                && READONLY.contains(permission))
                result = true
              if (!result)
                dataset.spaces.map(spaceId => {
                  for(role <- users.getUserRoleInSpace(i.id, spaceId)) {
                    if(role.permissions.contains(permission.toString))
                      result = true
                  }
                })
            })
        }
        case None => {}
      }

      results += (x -> result)
    })

    datasets.get(leftover_results.filter(_.resourceType == ResourceRef.dataset).map(_.id)).found.foreach(x => {
      var result = false

      if (x.trash){
        if (permission.equals(Permission.DeleteDataset)){
          ident match {
            case Some(i) => {
              x.spaces.map {
                spaceId => for (role <- users.getUserRoleInSpace(i.id, spaceId)) {
                  if (role.permissions.contains(permission.toString))
                    result = true
                }
              }
            }
            case None => {}
          }
        }
      } else {
        if ((x.isPublic || (x.isDefault && spaces.get(x.spaces).found.exists(_.isPublic)))
          && READONLY.contains(permission))
          result = true
        ident match {
          case Some(i) => {
            x.spaces.map {
              spaceId => for (role <- users.getUserRoleInSpace(i.id, spaceId)) {
                if (role.permissions.contains(permission.toString))
                  result = true
              }
            }
          }
          case None => {}
        }
      }

      results += (ResourceRef('dataset, x.id) -> result)
    })

    collections.get(leftover_results.filter(_.resourceType == ResourceRef.collection).map(_.id)).found.foreach(collection => {
      var result = false

      if (collection.trash){
        if (permission.equals(Permission.DeleteCollection)){
          ident match {
            case Some(i) => {
              collection.spaces.map {
                spaceId =>
                  for (role <- users.getUserRoleInSpace(i.id, spaceId)) {
                    if (role.permissions.contains(permission.toString))
                      result = true
                  }
              }
            }
            case None => {}
          }
        }
      } else {
        if (spaces.get(collection.spaces).found.exists(_.isPublic) && READONLY.contains(permission))
          result = true
        ident match {
          case Some(i) => {
            collection.spaces.map {
              spaceId => for (role <- users.getUserRoleInSpace(i.id, spaceId)) {
                if (role.permissions.contains(permission.toString))
                  result = true
              }
            }
          }
          case None => {}
        }
      }

      results += (ResourceRef('collection, collection.id) -> result)
    })

    spaces.get(leftover_results.filter(_.resourceType == ResourceRef.space).map(_.id)).found.foreach(space => {
      var result = false

      if (space.isPublic && READONLY.contains(permission))
        result = true

      ident match {
        case Some(i) => {
          users.getUserRoleInSpace(i.id, space.id).foreach(role => {
            if (role.permissions.contains(permission.toString))
              result = true
          })
        }
      }

      results += (ResourceRef('space, space.id) -> result)
    })

    comments.get(leftover_results.filter(_.resourceType == ResourceRef.comment).map(_.id)).found.foreach(comment => {
      val result = (comment.dataset_id, comment.file_id) match {
        case (Some(d_id), None) => checkPermission(user, permission, ResourceRef(ResourceRef.dataset, d_id))
        case (None, Some(f_id)) => checkPermission(user, permission, ResourceRef(ResourceRef.file, f_id))
        case (_, _) => false
      }

      results += (ResourceRef('comment, comment.id) -> result)
    })

    previews.get(leftover_results.filter(_.resourceType == ResourceRef.preview).map(_.id)).found.foreach(p => {
      var result = if (p.file_id.isDefined) {
        checkPermission(user, permission, ResourceRef(ResourceRef.file, p.file_id.get))
      } else if (p.section_id.isDefined) {
        checkPermission(user, permission, ResourceRef(ResourceRef.section, p.section_id.get))
      } else if (p.dataset_id.isDefined) {
        checkPermission(user, permission, ResourceRef(ResourceRef.dataset, p.dataset_id.get))
      } else if (p.collection_id.isDefined) {
        checkPermission(user, permission, ResourceRef(ResourceRef.collection, p.collection_id.get))
      }  else  {
        true
      }

      results += (ResourceRef('preview, p.id) -> result)
    })

    sections.get(leftover_results.filter(_.resourceType == ResourceRef.section).map(_.id)).found.foreach(s => {
      results += (ResourceRef('section, s.id) -> checkPermission(user, permission, ResourceRef(ResourceRef.file, s.file_id)))
    })

    leftover_results.foreach(r => {
      if (!results.get(r).isDefined) {
        var result = false

        r match {
          case ResourceRef(ResourceRef.relation, id) => {
            relations.get(id) match {
              case Some(r) => {
                result = (r.source.id == id.stringify || r.target.id == id.stringify)
              }
              case None => {}
            }
          }
          case ResourceRef(ResourceRef.vocabulary, id) => {
            vocabularies.get(id) match {
              case Some(vocab) => {
                ident match {
                  case Some(i) => {
                    vocab.spaces.map {
                      vocabId => for (role <- users.getUserRoleInSpace(i.id, vocabId)) {
                        if (role.permissions.contains(permission.toString))
                          result = true
                      }
                    }
                  }
                  case None => {}
                }
              }
              case None => {}
            }
          }
          case ResourceRef(ResourceRef.vocabularyterm, id) => {
            vocabularyterms.get(id) match {
              case Some(vocabterm) => {
                ident match {
                  case Some(i) => {
                    vocabterm.spaces.map {
                      vocabTermId => for (role <- users.getUserRoleInSpace(i.id, vocabTermId)) {
                        if (role.permissions.contains(permission.toString))
                          result = true
                      }
                    }
                  }
                  case None => {}
                }
              }
              case None => {}
            }
          }
          case ResourceRef(ResourceRef.curationObject, id) => {
            curations.get(id) match {
              case Some(curation) => result = checkPermission(user, permission, ResourceRef(ResourceRef.space, curation.space))
              case None => {}
            }
          }
          case ResourceRef(ResourceRef.curationFile, id) => {
            curations.getCurationByCurationFile(id) match {
              case Some(curation) => result = checkPermission(user, permission, ResourceRef(ResourceRef.space, curation.space))
              case None => {}
            }
          }
          // for DeleteMetadata, the creator of this metadata or user with permission to delete this resource can delete metadata
          case ResourceRef(ResourceRef.metadata, id) => {
            metadatas.getMetadataById(id) match {
              case Some(m) => result = checkPermission(user, permission, m.attachedTo)
              case None => {}
            }
          }
          case ResourceRef(ResourceRef.user, id) => {
            users.get(id) match {
              case Some(u) => {
                if (id == user.id) {
                  result = true
                } else {
                  u.spaceandrole.map { space_role =>
                    if (space_role.role.permissions.contains(permission.toString)) {
                      result = true
                    }
                  }
                }
              }
              case None => {}
            }
          }
          case ResourceRef(ResourceRef.thumbnail, id) => result = true
          case ResourceRef(resType, id) => {
            Logger.error("Resource type not recognized " + resType)
            result = false
          }
        }
      }
    })

    generatePermissionsList(results)
  }

  def getUserByIdentity(identity: User): Option[User] = users.findByIdentity(identity)

  /** on a private server this will return true iff user logged in, on public server this will always be true */
  def checkPrivateServer(user: Option[User]): Boolean = {
    configuration(play.api.Play.current).getString("permissions").getOrElse("public") == "public" || user.isDefined
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
