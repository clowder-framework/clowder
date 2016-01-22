package controllers

import models._
import api.Permission


/**
 *
 */
class Folders extends SecuredController{

  def newFolder(parent: TypedID, datasetParent:UUID) = PermissionAction(Permission.AddResourceToDataset, Some(ResourceRef(ResourceRef.dataset, datasetParent))) { implicit request =>
    implicit val user = request.user
    Ok(views.html.datasets.createFolder(datasetParent, parent))

  }

}
