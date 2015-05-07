package controllers

import api.{Permission, WithPermission}
import play.api.Routes
import javax.inject.{Singleton, Inject}
import play.api.mvc.Action
import services.{DatasetService, CollectionService, AppConfiguration, FileService}
import play.api.Logger

/**
 * Main application controller.
 * 
 * @author Luigi Marini
 */
@Singleton
class Application @Inject() (files: FileService, collections: CollectionService, datasets: DatasetService) extends SecuredController {
  /**
   * Redirect any url's that have a trailing /
   * @param path the path minus the slash
   * @return moved permanently to path without /
   */
  def untrail(path: String) = Action {
    MovedPermanently("/" + path)
  }

  /**
   * Main page.
   */
  def index = SecuredAction(authorization = WithPermission(Permission.Public)) { request =>
  	implicit val user = request.user
  	val latestFiles = files.latest(5)
    val datasetsCount = datasets.count()
    val filesCount = files.count()
    val collectionCount = collections.count()
    Ok(views.html.index(latestFiles, datasetsCount, filesCount, collectionCount,
      AppConfiguration.getDisplayName, AppConfiguration.getWelcomeMessage))
  }
  
  def options(path:String) = SecuredAction() { implicit request =>
    Logger.info("---controller: PreFlight Information---")
    Ok("")
   }

  /**
   * Bookmarklet
   */
  def bookmarklet() = SecuredAction(authorization = WithPermission(Permission.Public)) { implicit request =>
    val protocol = Utils.protocol(request)
    Ok(views.html.bookmarklet(request.host, protocol)).as("application/javascript")
  }
  
  /**
   *  Javascript routing.
   */
  def javascriptRoutes = Action { implicit request =>
    Ok(
      Routes.javascriptRouter("jsRoutes")(
        routes.javascript.Admin.test,
        routes.javascript.Admin.secureTest,
        routes.javascript.Admin.reindexFiles,
        routes.javascript.Admin.createIndex,
        routes.javascript.Admin.buildIndex,
        routes.javascript.Admin.deleteIndex,
        routes.javascript.Admin.deleteAllIndexes,
        routes.javascript.Admin.getIndexes,
        routes.javascript.Tags.search,
        routes.javascript.Admin.setTheme,
        routes.javascript.Admin.getAdapters,
        routes.javascript.Admin.getExtractors,
        routes.javascript.Admin.getMeasures,
        routes.javascript.Admin.getIndexers,
        routes.javascript.Datasets.dataset,
        routes.javascript.Collections.collection,
        api.routes.javascript.Admin.removeAdmin,        
        api.routes.javascript.Comments.comment,
        api.routes.javascript.Comments.removeComment,
        api.routes.javascript.Comments.editComment,
        api.routes.javascript.Datasets.comment,
        api.routes.javascript.Datasets.createEmptyDataset,
        api.routes.javascript.Datasets.attachMultipleFiles,
        api.routes.javascript.Datasets.deleteDataset,
        api.routes.javascript.Datasets.detachAndDeleteDataset,
        api.routes.javascript.Datasets.getTags,
        api.routes.javascript.Datasets.addTags,
        api.routes.javascript.Datasets.removeTag,
        api.routes.javascript.Datasets.removeTags,
        api.routes.javascript.Datasets.removeAllTags,
        api.routes.javascript.Datasets.updateInformation,
        api.routes.javascript.Datasets.updateLicense,
        api.routes.javascript.Files.comment,
        api.routes.javascript.Files.getTags,
        api.routes.javascript.Files.addTags,
        api.routes.javascript.Files.removeTags,
        api.routes.javascript.Files.removeAllTags,
        api.routes.javascript.Files.updateLicense,
        api.routes.javascript.Files.extract,
        api.routes.javascript.Files.removeFile,
        api.routes.javascript.Previews.upload,
        api.routes.javascript.Previews.uploadMetadata,
        api.routes.javascript.Sections.add,
        api.routes.javascript.Sections.comment,
        api.routes.javascript.Sections.getTags,
        api.routes.javascript.Sections.addTags,
        api.routes.javascript.Sections.removeTags,
        api.routes.javascript.Sections.removeAllTags,
        api.routes.javascript.Geostreams.searchSensors,
        api.routes.javascript.Geostreams.getSensorStreams,
        api.routes.javascript.Geostreams.searchDatapoints,
        api.routes.javascript.Collections.attachPreview,
        api.routes.javascript.Collections.attachDataset,
        api.routes.javascript.Collections.removeDataset,
        api.routes.javascript.Collections.removeCollection,
        api.routes.javascript.Projects.addproject,
        api.routes.javascript.Institutions.addinstitution,
        api.routes.javascript.Users.getUser
      )
    ).as(JSON) 
  }
  
}
