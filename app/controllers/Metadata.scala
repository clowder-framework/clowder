package controllers

import javax.inject.Inject
import play.api.libs.json.Json._
import play.Logger
import api.Permission
import api.Permission.Permission
import models.{ResourceRef, UUID}
import services._


/**
 * View JSON-LD metadata for all resources.
 */
class Metadata @Inject() (
  files: FileService,
  datasets: DatasetService,
  spaces: SpaceService,
  metadata: MetadataService,
  contextLDService: ContextLDService) extends SecuredController {

  def view(id: UUID) = PermissionAction(Permission.ViewMetadata) { implicit request =>
    implicit val user = request.user
    metadata.getMetadataById(id) match {
      case Some(m) => Ok(views.html.metadatald.view(List(m), true))
      case None => NotFound
    }
  }

  def file(file_id: UUID) = PermissionAction(Permission.ViewMetadata) { implicit request =>
    implicit val user = request.user
    files.get(file_id) match {
      case Some(file) => {
        val mds = metadata.getMetadataByAttachTo(ResourceRef(ResourceRef.file, file_id))
        // TODO use to provide contextual definitions directly in the GUI
        val contexts = (for (md <- mds;
                             cId <- md.contextId;
                             c <- contextLDService.getContextById(cId))
          yield cId -> c).toMap

        Ok(views.html.metadatald.viewFile(file, mds))
      }
      case None => NotFound
    }
  }

  def dataset(dataset_id: UUID) = PermissionAction(Permission.ViewMetadata, Some(ResourceRef(ResourceRef.dataset, dataset_id))) { implicit request =>
    implicit val user = request.user
    datasets.get(dataset_id) match {
      case Some(dataset) => {
        val m = metadata.getMetadataByAttachTo(ResourceRef(ResourceRef.dataset, dataset_id))
        Ok(views.html.metadatald.viewDataset(dataset, m))
      }
      case None => NotFound
    }
  }

  def search() = PermissionAction(Permission.ViewMetadata) { implicit request =>
    implicit val user = request.user
    // We should set some of these listAccess parameters to sensible defaults...
    val userAccessibleSpaces = spaces.listAccess(0, Set[Permission](Permission.ViewSpace), request.user, request.user.fold(false)(_.superAdminMode), true, false, false)
    val spaceid = request.request.getQueryString("spaceid")
    spaceid match {
      case Some(sid) => {
        if (sid == "") {
          Ok(views.html.metadatald.search(userAccessibleSpaces))
        } else {
          Ok(views.html.metadatald.search(userAccessibleSpaces, spaces.get(UUID(sid))))
        }
      }
      case None => {
        Ok(views.html.metadatald.search(userAccessibleSpaces))
      }
    }
  }

  def getMetadataBySpace(id: UUID) = PermissionAction(Permission.EditSpace, Some(ResourceRef(ResourceRef.space, id))) { implicit request =>
    implicit val user = request.user
    spaces.get(id) match {
      case Some(space) => {
        val metadataResults = metadata.getDefinitions(Some(id))
        Ok(views.html.manageMetadataDefinitions(metadataResults.toList, Some(id), Some(space.name)))
      }
      case None => BadRequest("The requested space does not exist. Space Id: " + id)
    }

  }
}
