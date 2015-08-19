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
import play.api.Play.current


import scala.text

class CurationObjects @Inject()( curations: CurationService,
                           datasets: DatasetService,
                             collections: CollectionService,
                             spaces: SpaceService,
                               files: FileService
                              ) extends SecuredController {

  def newCO(spaceId:UUID) = PermissionAction(Permission.EditStagingArea, Some(ResourceRef(ResourceRef.space, spaceId))) { implicit request =>
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
  def submit(spaceId:UUID) = PermissionAction(Permission.EditStagingArea, Some(ResourceRef(ResourceRef.space, spaceId))) (parse.multipartFormData) { implicit request =>

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

        val newCuration = CurationObject(
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
        spaces.addCurationObject(spaceId, newCuration.id)
       Redirect(routes.Spaces.getSpace(spaceId))
     }
      case None => Redirect(routes.Spaces.getSpace(spaceId))
    }
  }

  def getCurationObject(spaceId: UUID, curationId: UUID) = PermissionAction(Permission.EditStagingArea, Some(ResourceRef(ResourceRef.space, spaceId))) {
    implicit request =>
      implicit val user = request.user
      spaces.get(spaceId) match {
        case Some(s) => {
          curations.get(curationId) match {
            case Some(c) => {
              val ds: Dataset = c.datasets(0)
              val dsmetadata = datasets.getMetadata(ds.id)
              val dsUsrMetadata = datasets.getUserMetadata(ds.id)
              val isRDFExportEnabled = current.plugin[RDFExportService].isDefined
              val filesUsrMetadata: Map[String, scala.collection.mutable.Map[String,Any]] = ds.files.map(file=> file.id.stringify -> files.getUserMetadata(file.id)).toMap.asInstanceOf[Map[String, scala.collection.mutable.Map[String,Any]]]
              Ok(views.html.spaces.curationObject(s, c, dsmetadata, dsUsrMetadata, filesUsrMetadata, isRDFExportEnabled))
            }
            case None => InternalServerError("Curation Object Not found")
          }
        }
        case None => InternalServerError("Space not found")

      }

  }

  def findMatchingRepositories(spaceId: UUID, curationId: UUID) = PermissionAction(Permission.EditStagingArea, Some(ResourceRef(ResourceRef.space, spaceId))) {
    implicit request =>
      implicit val user = request.user
      spaces.get(spaceId) match {
        case Some(s) => {
          curations.get(curationId) match {
            case Some(c) => {
              //TODO: Make some sort of call to the matckmaker
              val propertiesMap: Map[String, List[String]] = Map("Content Types" -> List("Images", "Video"),
              "Dissemination Control" -> List("Restricted Use", "Ability to Embargo"),"License" -> List("Creative Commons", "GPL") ,
              "Organizational Affiliation" -> List("UMich", "IU", "UIUC"))

              Ok(views.html.spaces.matchmakerResult(s, c, propertiesMap))
            }
            case None => InternalServerError("Curation Object not found")
          }
        }
        case None => InternalServerError("Space not found")
      }

  }

  def compareToRepository(spaceId: UUID, curationId: UUID) = PermissionAction(Permission.EditStagingArea, Some(ResourceRef(ResourceRef.space, spaceId))) {
    implicit request =>
      implicit val user = request.user
      spaces.get(spaceId) match {
        case Some(s) => {
         curations.get(curationId) match {
           case Some(c) => {
             //TODO: Make some call to C3-PR?
           //  Ok(views.html.spaces.matchmakerReport())
             val propertiesMap: Map[String, List[String]] = Map("Content Types" -> List("Images", "Video"),
               "Dissemination Control" -> List("Restricted Use", "Ability to Embargo"),"License" -> List("Creative Commons", "GPL") ,
               "Organizational Affiliation" -> List("UMich", "IU", "UIUC"))

             Ok(views.html.spaces.curationDetailReport(s, c, propertiesMap))
           }
           case None => InternalServerError("Curation Object not found")

         }
        }
        case None => InternalServerError("Space not found")
      }
  }

  def sendToRepository(spaceId: UUID, curationId: UUID) = PermissionAction(Permission.EditStagingArea, Some(ResourceRef(ResourceRef.space, spaceId))) {
    implicit request =>
      implicit val user = request.user
      spaces.get(spaceId) match {
        case Some(s) => {
          curations.get(curationId) match {
            case Some(c) =>
              //TODO : Submit the curationId to the repository. This probably needs the repository as input

              val propertiesMap: Map[String, List[String]] = Map("Content Types" -> List("Images", "Video"),
                "Dissemination Control" -> List("Restricted Use", "Ability to Embargo"),"License" -> List("Creative Commons", "GPL") ,
                "Organizational Affiliation" -> List("UMich", "IU", "UIUC"))

              Ok(views.html.spaces.curationSubmitted(s, c, "IDEALS"))
          }
        }
        case None => InternalServerError("Space Not found")
      }
  }

}



