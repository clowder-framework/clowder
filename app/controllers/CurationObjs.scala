package controllers

import java.net.URL
import java.util.Date
import javax.inject.Inject

import api.Permission
import models._
import play.api.Logger
import play.api.data.{Forms, Form}
import play.api.data.Forms._
import services._
import util.RequiredFieldsConfig

import scala.text

class CurationObjs @Inject()( curations: CurationService,
                           datasets: DatasetService,
                             collections: CollectionService,
                             spaces: SpaceService

                              ) extends SecuredController {

  def newCO(spaceId:UUID) = PermissionAction(Permission.EditSpace, Some(ResourceRef(ResourceRef.space, spaceId))) { implicit request =>
    implicit val user = request.user
    val collectionsInSpace = spaces.getCollectionsInSpace(Some(spaceId.stringify))
    val datasetsInSpace = spaces.getDatasetsInSpace(Some(spaceId.stringify))
    Ok(views.html.curations.newCuration(spaceId, datasetsInSpace, collectionsInSpace, RequiredFieldsConfig.isNameRequired, RequiredFieldsConfig.isDescriptionRequired
    ))
  }

  /**
   * Controller flow to create a new collection. Takes two parameters, name, a String, and description, a String. On success,
   * the browser is redirected to the space page since I haven't merge staging area. On error, it is redirected back space
   * page since I haven't merge staging area.
   */
  def submit(spaceId:UUID) = PermissionAction(Permission.EditSpace, Some(ResourceRef(ResourceRef.space, spaceId))) (parse.multipartFormData) { implicit request =>

    var COName = request.body.asFormUrlEncoded.getOrElse("name", null)
    var CODesc = request.body.asFormUrlEncoded.getOrElse("description", null)
    var CODataset = request.body.asFormUrlEncoded.getOrElse("datasets",  List.empty)
    var COCollection = request.body.asFormUrlEncoded.getOrElse("collections",  List.empty)

    implicit val user = request.user
    user match {
      case Some(identity) => {
        //TODO:check COName is null
        val stringCollections = COCollection(0).split(",").toList
        val stringDatasets = CODataset(0).split(",").toList

        val COCollectionIDs: List[UUID] = stringCollections.map(aCollection => if(aCollection != "") UUID(aCollection) else None).filter(_ != None).asInstanceOf[List[UUID]]
        var COCollections = COCollectionIDs.map( collectionid => collections.get(collectionid).getOrElse(null)).filter(_ != null)


        val CODatasetIDs: List[UUID] = stringDatasets.map(aDataset => if(aDataset != "") UUID(aDataset) else None).filter(_ != None).asInstanceOf[List[UUID]]
        var CODatasets = CODatasetIDs.map( datasetid => datasets.get(datasetid).getOrElse(null)).filter(_ != null)


        Logger.debug("------- in CUrations.submit with " + CODatasets.length + " datasets and "+ COCollections.length +" collections ---------")

        val newCuration = CurationObj(
        name = COName(0),
        author = identity,
        description = CODesc(0),
        created = new Date,
        space = spaceId,
        datasets = CODatasets,
        collections = COCollections
        )

        // insert curation
        Logger.debug("create Co: " + newCuration.id)
        curations.insert(newCuration)

       Redirect(routes.Spaces.getSpace(spaceId))
     }
      case None => Redirect(routes.Spaces.getSpace(spaceId))
    }
  }

}



