package controllers

import javax.inject.{Inject, Singleton}

import play.api.{Logger, Routes}
import play.api.mvc.Action
import services._
import models.{User, Event}
import play.api.Logger

/**
 * Main application controller.
 */
@Singleton
class Application @Inject() (files: FileService, collections: CollectionService, datasets: DatasetService,
                             spaces: SpaceService, events: EventService, users: UserService) extends SecuredController {
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
  def index = UserAction { implicit request =>
  	implicit val user = request.user
  	val latestFiles = files.latest(5)
    val datasetsCount = datasets.count()
    val datasetsCountAccess = datasets.countAccess(user, request.superAdmin)
    val filesCount = files.count()
    val collectionsCount = collections.count()
    val collectionsCountAccess = collections.countAccess(user, request.superAdmin)
    val spacesCount = spaces.count()
    val spacesCountAccess = spaces.countAccess(user, request.superAdmin)
    val usersCount = users.count();
    //newsfeedEvents is the combination of followedEntities and requestevents, then take the most recent 20 of them.
    var newsfeedEvents = user.fold(List.empty[Event])(u => events.getEvents(u.followedEntities, Some(20)).sorted(Ordering.by((_: Event).created).reverse))
    newsfeedEvents =  (newsfeedEvents ::: events.getRequestEvents(user, Some(20)))
          .sorted(Ordering.by((_: Event).created).reverse).take(20)
        Ok(views.html.index(latestFiles, datasetsCount, datasetsCountAccess, filesCount, collectionsCount, collectionsCountAccess,
          spacesCount, spacesCountAccess, usersCount, AppConfiguration.getDisplayName, AppConfiguration.getWelcomeMessage, newsfeedEvents))
  }
  
  def options(path:String) = UserAction { implicit request =>
    Logger.info("---controller: PreFlight Information---")
    Ok("")
   }

  /**
   * Bookmarklet
   */
  def bookmarklet() = AuthenticatedAction { implicit request =>
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
        routes.javascript.Spaces.getSpace,
        routes.javascript.Admin.removeRole,
        routes.javascript.Admin.editRole,
        routes.javascript.Files.file,
        routes.javascript.Datasets.dataset,
        routes.javascript.Geostreams.list,
        routes.javascript.Collections.collection,
        routes.javascript.RedirectUtility.authenticationRequiredMessage,
        api.routes.javascript.Admin.removeAdmin,        
        api.routes.javascript.Comments.comment,
        api.routes.javascript.Comments.removeComment,
        api.routes.javascript.Comments.editComment,
        api.routes.javascript.Datasets.comment,
        api.routes.javascript.Datasets.createEmptyDataset,
        api.routes.javascript.Datasets.attachExistingFile,
        api.routes.javascript.Datasets.attachMultipleFiles,
        api.routes.javascript.Datasets.deleteDataset,
        api.routes.javascript.Datasets.detachAndDeleteDataset,
        api.routes.javascript.Datasets.getTags,
        api.routes.javascript.Datasets.addTags,
        api.routes.javascript.Datasets.removeTag,
        api.routes.javascript.Datasets.removeTags,
        api.routes.javascript.Datasets.removeAllTags,
        api.routes.javascript.Datasets.updateInformation,
        api.routes.javascript.Datasets.updateName,
        api.routes.javascript.Datasets.updateDescription,
        api.routes.javascript.Datasets.updateLicense,
        api.routes.javascript.Datasets.follow,
        api.routes.javascript.Datasets.unfollow,
        api.routes.javascript.Datasets.detachFile,
        api.routes.javascript.Files.comment,
        api.routes.javascript.Files.getTags,
        api.routes.javascript.Files.addTags,
        api.routes.javascript.Files.removeTags,
        api.routes.javascript.Files.removeAllTags,
        api.routes.javascript.Files.updateLicense,
        api.routes.javascript.Files.extract,
        api.routes.javascript.Files.removeFile,
        api.routes.javascript.Files.follow,
        api.routes.javascript.Files.unfollow,
        api.routes.javascript.Files.getTechnicalMetadataJSON,
        api.routes.javascript.Files.filePreviewsList,
        api.routes.javascript.Files.updateMetadata,
        api.routes.javascript.Files.addMetadata,
        api.routes.javascript.Previews.upload,
        api.routes.javascript.Previews.uploadMetadata,
        api.routes.javascript.Previews.download,
        api.routes.javascript.Previews.getMetadata,
        api.routes.javascript.Sections.add,
        api.routes.javascript.Sections.comment,
        api.routes.javascript.Sections.getTags,
        api.routes.javascript.Sections.addTags,
        api.routes.javascript.Sections.removeTags,
        api.routes.javascript.Sections.removeAllTags,
        api.routes.javascript.Geostreams.searchSensors,
        api.routes.javascript.Geostreams.searchStreams,
        api.routes.javascript.Geostreams.getSensorStreams,
        api.routes.javascript.Geostreams.searchDatapoints,
        api.routes.javascript.Geostreams.deleteSensor,
        api.routes.javascript.Geostreams.updateSensorMetadata,
        api.routes.javascript.Geostreams.patchStreamMetadata,
        api.routes.javascript.Collections.attachPreview,
        api.routes.javascript.Collections.attachDataset,
        api.routes.javascript.Collections.removeDataset,
        api.routes.javascript.Collections.removeCollection,
        api.routes.javascript.Spaces.get,
        api.routes.javascript.Spaces.removeSpace,
        api.routes.javascript.Spaces.list,
        api.routes.javascript.Spaces.listSpacesCanAdd,
        api.routes.javascript.Spaces.addCollection,
        api.routes.javascript.Spaces.addDataset,
        api.routes.javascript.Spaces.updateSpace,
        api.routes.javascript.Spaces.updateUsers,
        api.routes.javascript.Spaces.removeUser,
        api.routes.javascript.Spaces.follow,
        api.routes.javascript.Spaces.unfollow,
        api.routes.javascript.Collections.follow,
        api.routes.javascript.Collections.unfollow,
        api.routes.javascript.Collections.updateCollectionName,
        api.routes.javascript.Collections.updateCollectionDescription,
        api.routes.javascript.Users.follow,
        api.routes.javascript.Users.unfollow,
        api.routes.javascript.Relations.findTargets,
        api.routes.javascript.Relations.add,
        api.routes.javascript.Projects.addproject,
        api.routes.javascript.Institutions.addinstitution,
        api.routes.javascript.Users.getUser,
        api.routes.javascript.Spaces.addDatasetToSpaces,
        api.routes.javascript.Spaces.addCollectionToSpaces,
        api.routes.javascript.CurationObjects.findMatchmakingRepositories,
        api.routes.javascript.CurationObjects.retractCurationObject,
        controllers.routes.javascript.Profile.viewProfileUUID,
        controllers.routes.javascript.Files.file,
        controllers.routes.javascript.Datasets.dataset,
        controllers.routes.javascript.Datasets.newDataset,
        controllers.routes.javascript.Collections.collection,
        controllers.routes.javascript.Collections.newCollection,
        controllers.routes.javascript.Spaces.acceptRequest,
        controllers.routes.javascript.Spaces.rejectRequest,
        controllers.routes.javascript.Spaces.stagingArea,
        controllers.routes.javascript.CurationObjects.submit,
        controllers.routes.javascript.CurationObjects.getCurationObject,
        controllers.routes.javascript.CurationObjects.findMatchingRepositories,
        controllers.routes.javascript.CurationObjects.sendToRepository,
        controllers.routes.javascript.CurationObjects.compareToRepository,
        controllers.routes.javascript.CurationObjects.deleteCuration,
        controllers.routes.javascript.CurationObjects.savePublishedObject,
        controllers.routes.javascript.CurationObjects.getStatusFromRepository
      )
    ).as(JSON) 
  }
  
}
