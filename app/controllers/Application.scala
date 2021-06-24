package controllers

import models.{Event, UUID, UserStatus}
import play.api.Play.current
import play.api.mvc.Action
import play.api.{Logger, Play, Routes}
import services._
import util.Formatters.sanitizeHTML

import java.net.URL
import javax.inject.{Inject, Singleton}
import scala.collection.immutable.List
import scala.collection.mutable.ListBuffer

/**
 * Main application controller.
 */
@Singleton
class Application @Inject() (files: FileService, collections: CollectionService, datasets: DatasetService,
                             spaces: SpaceService, events: EventService, comments: CommentService,
                             sections: SectionService, users: UserService, selections: SelectionService) extends SecuredController {
  /**
   * Redirect any url's that have a trailing /
   *
   * @param path the path minus the slash
   * @return moved permanently to path without /
   */
  def untrail(path: String) = Action { implicit request =>
    MovedPermanently(s"${Utils.baseUrl(request, false)}/${path}")
  }

  def swaggerUI = Action { implicit request =>
    val swagger = routes.Application.swagger().absoluteURL(Utils.https(request))
    Redirect("http://clowder.ncsa.illinois.edu/swagger/?url=" + swagger)
  }

    /**
    * Returns the swagger documentation customized for this site.
    */
  def swagger = Action  { implicit request =>
    Play.resource("/public/swagger.yml") match {
      case Some(resource) => {
        val https = Utils.https(request)
        val clowderurl = new URL(Utils.baseUrl(request))
        val host = if (clowderurl.getPort == -1) {
          clowderurl.getHost
        } else {
          clowderurl.getHost + ":" + clowderurl.getPort
        }
        var skipit = false
        val result = scala.io.Source.fromInputStream(resource.openStream()).getLines().flatMap(line =>
          if (line.matches("\\s*#")) {
            // comment
            line + "\n"
          } else {
            if (skipit && !(line.startsWith(" ") || line.startsWith("- "))) {
              skipit = false
            }
            if (skipit) {
              None
            } else if (line.startsWith("info:")) {
              skipit = true
              "info:\n" +
                "  title: " + AppConfiguration.getDisplayName + "\n" +
                "  description: " + AppConfiguration.getWelcomeMessage + "\n" +
                "  version: \"" + sys.props.getOrElse("build.version", default = "0.0.0").toString + "\"\n" +
                "  termsOfService: " + routes.Application.tos().absoluteURL(https) + "\n" +
                "  contact: " + "\n" +
                "    name: " + AppConfiguration.getDisplayName + "\n" +
                "    url: " + routes.Application.email().absoluteURL(https) + "\n"
            } else if (line.startsWith("servers:")) {
              skipit = true
              "servers:\n" +
                "- url: " + clowderurl + "/api" + "\n"
            } else {
              line + "\n"
            }
          }
        )
        Ok(result.mkString)
      }
      case None => NotFound("Could not find swagger.json")
    }
  }

  /**
   * Main page.
   */
  def index = UserAction(needActive = false) { implicit request =>
    val appConfig: AppConfigurationService = DI.injector.getInstance(classOf[AppConfigurationService])

  	implicit val user = request.user

    var newsfeedEvents = List.empty[Event]
    if (!play.Play.application().configuration().getBoolean("clowder.disable.events", false)) {
      newsfeedEvents = user.fold(List.empty[Event])(u => events.getEvents(u.followedEntities, Some(20)))
      newsfeedEvents =  newsfeedEvents ::: events.getRequestEvents(user, Some(20))
      if (user.isDefined) {
        newsfeedEvents = (newsfeedEvents ::: events.getEventsByUser(user.get, Some(20)))
          .sorted(Ordering.by((_: Event).created).reverse).distinct.take(20)
      }
    }

    user match {
      case Some(clowderUser) if (clowderUser.status==UserStatus.Inactive) => {
        Redirect(routes.Error.notActivated())
      }
      case Some(clowderUser) if !(clowderUser.status==UserStatus.Inactive) => {
        newsfeedEvents = newsfeedEvents ::: events.getEventsByUser(clowderUser, Some(20))
        if( play.Play.application().configuration().getBoolean("showCommentOnHomepage")) newsfeedEvents = newsfeedEvents :::events.getCommentEvent(clowderUser, Some(20))
        newsfeedEvents = newsfeedEvents.sorted(Ordering.by((_: Event).created).reverse).distinct.take(20)
        val datasetsUser = datasets.listUser(12, Some(clowderUser), request.user.fold(false)(_.superAdminMode), clowderUser)
        val collectionList = collections.listUser(12, Some(clowderUser), request.user.fold(false)(_.superAdminMode), clowderUser)
        val collectionsWithThumbnails = collectionList.map {c =>
          if (c.thumbnail_id.isDefined) {
            c
          } else {
            val collectionThumbnail = datasets.listCollection(c.id.stringify).find(_.thumbnail_id.isDefined).flatMap(_.thumbnail_id)
            c.copy(thumbnail_id = collectionThumbnail)
          }
        }

        //Modifications to decode HTML entities that were stored in an encoded fashion as part
        //of the collection's names or descriptions
        val decodedCollections = ListBuffer.empty[models.Collection]
        for (aCollection <- collectionsWithThumbnails) {
          decodedCollections += Utils.decodeCollectionElements(aCollection)
        }
        val spacesUser = spaces.listUser(12, Some(clowderUser),request.user.fold(false)(_.superAdminMode), clowderUser)
        var followers: List[(UUID, String, String, String)] = List.empty
        for (followerID <- clowderUser.followers.take(3)) {
          val userFollower = users.findById(followerID)
          userFollower match {
            case Some(uFollower) => {
              val ufEmail = uFollower.email.getOrElse("")
              followers = followers.++(List((uFollower.id, uFollower.fullName, ufEmail, uFollower.getAvatarUrl())))
            }
            case None =>
          }
        }
        var followedUsers: List[(UUID, String, String, String)] = List.empty
        var followedFiles: List[(UUID, String, String)] = List.empty
        var followedDatasets: List[(UUID, String, String)] = List.empty
        var followedCollections: List[(UUID, String, String)] = List.empty
        var followedSpaces: List[(UUID, String, String)] = List.empty
        val maxDescLength = 50
        for (tidObject <- clowderUser.followedEntities) {
          if (tidObject.objectType == "user") {
            val followedUser = users.get(tidObject.id)
            followedUser match {
              case Some(fuser) => {
                followedUsers = followedUsers.++(List((fuser.id, fuser.fullName, fuser.email.getOrElse(""), fuser.getAvatarUrl())))
              }
              case None =>
            }
          } else if (tidObject.objectType == "file") {
            // TODO: Can use file.get(list[UUID]) here if the for loop is restructured (same for dataset, collection)
            val followedFile = files.get(tidObject.id)
            followedFile match {
              case Some(ffile) => {
                followedFiles = followedFiles.++(List((ffile.id, ffile.filename, ffile.contentType)))
              }
              case None =>
            }
          } else if (tidObject.objectType == "dataset") {
            val followedDataset = datasets.get(tidObject.id)
            followedDataset match {
              case Some(fdset) => {
                followedDatasets = followedDatasets.++(List((fdset.id, fdset.name, fdset.description.substring(0, Math.min(maxDescLength, fdset.description.length())))))
              }
              case None =>
            }
          } else if (tidObject.objectType == "collection") {
            val followedCollection = collections.get(tidObject.id)
            followedCollection match {
              case Some(fcoll) => {
                followedCollections = followedCollections.++(List((fcoll.id, fcoll.name, fcoll.description.substring(0, Math.min(maxDescLength, fcoll.description.length())))))
              }
              case None =>
            }
          } else if (tidObject.objectType == "'space") {
            val followedSpace = spaces.get(tidObject.id)
            followedSpace match {
              case Some(fspace) => {
                followedSpaces = followedSpaces.++(List((fspace.id, fspace.name, fspace.description.substring(0, Math.min(maxDescLength, fspace.description.length())))))
              }
              case None => {}
            }
          }
        }
        Logger.debug("User selections" + user)
        val userSelections: List[String] =
          if(user.isDefined) selections.get(user.get.identityId.userId).map(_.id.stringify)
          else List.empty[String]
        Logger.debug("User selection " + userSelections)
        Ok(views.html.home(AppConfiguration.getDisplayName, newsfeedEvents, clowderUser, datasetsUser,
          decodedCollections.toList, spacesUser, true, followers, followedUsers.take(12), followedFiles.take(8),
          followedDatasets.take(8), followedCollections.take(8),followedSpaces.take(8), Some(true), userSelections))
      }
      case _ => {
        // Set bytes from appConfig
        val filesBytes = appConfig.getIndexCounts.numBytes

        // Set other counts from DB
        val datasetsCount = datasets.count()
        val filesCount = files.count()
        val collectionsCount = collections.count()
        val spacesCount = spaces.count()
        val usersCount = users.count()

        val sanitezedWelcomeText = sanitizeHTML(AppConfiguration.getWelcomeMessage)

        Ok(views.html.index(datasetsCount, filesCount, filesBytes,
          collectionsCount, spacesCount, usersCount,
          AppConfiguration.getDisplayName, sanitezedWelcomeText))
      }
    }
  }

  def about = UserAction(needActive = false) { implicit request =>
    implicit val user = request.user

    // Set bytes from appConfig
    val appConfig = DI.injector.getInstance(classOf[AppConfigurationService])
    val filesBytes = appConfig.getIndexCounts.numBytes

    // Set other counts from DB
    val datasetsCount = datasets.count()
    val filesCount = files.count()
    val collectionsCount = collections.count()
    val spacesCount = spaces.count()
    val usersCount = users.count()

    val sanitezedWelcomeText = sanitizeHTML(AppConfiguration.getWelcomeMessage)

    Ok(views.html.index(datasetsCount, filesCount, filesBytes, collectionsCount,
        spacesCount, usersCount, AppConfiguration.getDisplayName, sanitezedWelcomeText))
  }

  def email(subject: String, body: String) = UserAction(needActive=false) { implicit request =>
    if (request.user.isEmpty) {
      Redirect(routes.Application.index())
    } else {
      implicit val user = request.user
      Ok(views.html.emailAdmin(subject, body))
    }
  }

  /** Show the Terms of Service */
  def tos(redirect: Option[String]) = UserAction(needActive = false) { implicit request =>
    implicit val user = request.user
    Ok(views.html.tos(redirect))
  }

  def options(path:String) = UserAction(needActive = false) { implicit request =>
    Logger.debug("---controller: PreFlight Information---")
    Ok("")
   }

  def healthz() = Action { implicit request =>
    Ok("healthy")
  }

  /**
   * Bookmarklet
   */
  def bookmarklet() = AuthenticatedAction { implicit request =>
    Ok(views.html.bookmarklet(Utils.baseUrl(request))).as("application/javascript")
  }

  /**
   *  Javascript routing.
   */
  def javascriptRoutes = Action { implicit request =>
    Ok(
      Routes.javascriptRouter("jsRoutes")(
        routes.javascript.Admin.reindexFiles,
        routes.javascript.Admin.createIndex,
        routes.javascript.Admin.buildIndex,
        routes.javascript.Admin.deleteIndex,
        routes.javascript.Admin.deleteAllIndexes,
        routes.javascript.Admin.getIndexes,
        routes.javascript.Admin.getAdapters,
        routes.javascript.Admin.getExtractors,
        routes.javascript.Admin.getMeasures,
        routes.javascript.Admin.getIndexers,
        routes.javascript.Spaces.getSpace,
        routes.javascript.Admin.removeRole,
        routes.javascript.Admin.editRole,
        routes.javascript.Files.file,
        routes.javascript.Files.fileBySection,
        routes.javascript.Datasets.dataset,
        routes.javascript.Datasets.datasetBySection,
        routes.javascript.Datasets.addFiles,
        routes.javascript.Folders.addFiles,
        routes.javascript.Geostreams.list,
        routes.javascript.Collections.collection,
        routes.javascript.Error.authenticationRequiredMessage,
        routes.javascript.Collections.getUpdatedDatasets,
        routes.javascript.Collections.getUpdatedChildCollections,
        routes.javascript.Profile.viewProfileUUID,
        routes.javascript.Assets.at,
        routes.javascript.Application.swaggerUI,
        api.routes.javascript.Admin.reindex,
        api.routes.javascript.Comments.comment,
        api.routes.javascript.Comments.removeComment,
        api.routes.javascript.Comments.editComment,
        api.routes.javascript.Comments.mentionInComment,
        api.routes.javascript.Datasets.get,
        api.routes.javascript.Datasets.list,
        api.routes.javascript.Datasets.listCanEdit,
        api.routes.javascript.Datasets.listMoveFileToDataset,
        api.routes.javascript.Datasets.comment,
        api.routes.javascript.Datasets.createEmptyDataset,
        api.routes.javascript.Datasets.attachExistingFile,
        api.routes.javascript.Datasets.attachMultipleFiles,
        api.routes.javascript.Datasets.deleteDataset,
        api.routes.javascript.Datasets.detachAndDeleteDataset,
        api.routes.javascript.Datasets.datasetFilesList,
        api.routes.javascript.Datasets.datasetAllFilesList,
        api.routes.javascript.Datasets.getTechnicalMetadataJSON,
        api.routes.javascript.Datasets.listInCollection,
        api.routes.javascript.Datasets.getTags,
        api.routes.javascript.Datasets.addTags,
        api.routes.javascript.Datasets.copyDatasetToSpace,
        api.routes.javascript.Datasets.removeTag,
        api.routes.javascript.Datasets.removeTags,
        api.routes.javascript.Datasets.removeAllTags,
        api.routes.javascript.Datasets.updateInformation,
        api.routes.javascript.Datasets.updateName,
        api.routes.javascript.Datasets.updateDescription,
        api.routes.javascript.Datasets.addCreator,
        api.routes.javascript.Datasets.removeCreator,
        api.routes.javascript.Datasets.moveCreator,
        api.routes.javascript.Datasets.updateLicense,
        api.routes.javascript.Datasets.follow,
        api.routes.javascript.Datasets.unfollow,
        api.routes.javascript.Datasets.detachFile,
        api.routes.javascript.Datasets.download,
        api.routes.javascript.Datasets.downloadPartial,
        api.routes.javascript.Datasets.downloadFolder,
        api.routes.javascript.Datasets.getPreviews,
        api.routes.javascript.Datasets.updateAccess,
        api.routes.javascript.Datasets.addFileEvent,
        api.routes.javascript.Datasets.getMetadataDefinitions,
        api.routes.javascript.Datasets.moveFileBetweenDatasets,
        api.routes.javascript.Datasets.users,
        api.routes.javascript.Datasets.restoreDataset,
        api.routes.javascript.Datasets.emptyTrash,
        api.routes.javascript.Extractions.submitFilesToExtractor,
        api.routes.javascript.Files.download,
        api.routes.javascript.Files.archive,
        api.routes.javascript.Files.sendArchiveRequest,
        api.routes.javascript.Files.unarchive,
        api.routes.javascript.Files.sendUnarchiveRequest,
        api.routes.javascript.Files.comment,
        api.routes.javascript.Files.getTags,
        api.routes.javascript.Files.addTags,
        api.routes.javascript.Files.removeTags,
        api.routes.javascript.Files.removeAllTags,
        api.routes.javascript.Files.updateLicense,
        api.routes.javascript.Files.updateFileName,
        api.routes.javascript.Files.updateDescription,
        api.routes.javascript.Files.extract,
        api.routes.javascript.Files.bulkDeleteFiles,
        api.routes.javascript.Files.removeFile,
        api.routes.javascript.Files.follow,
        api.routes.javascript.Files.unfollow,
        api.routes.javascript.Files.getTechnicalMetadataJSON,
        api.routes.javascript.Files.filePreviewsList,
        api.routes.javascript.Files.updateMetadata,
        api.routes.javascript.Files.addMetadata,
        api.routes.javascript.Files.getMetadataDefinitions,
        api.routes.javascript.Files.users,
        api.routes.javascript.Files.getMetadataJsonLD,
        api.routes.javascript.Previews.upload,
        api.routes.javascript.Previews.uploadMetadata,
        api.routes.javascript.Previews.download,
        api.routes.javascript.Previews.getMetadata,
        api.routes.javascript.Search.searchMultimediaIndex,
        api.routes.javascript.Search.search,
        api.routes.javascript.Search.searchJson,
        api.routes.javascript.Sections.add,
        api.routes.javascript.Sections.delete,
        api.routes.javascript.Sections.comment,
        api.routes.javascript.Sections.getTags,
        api.routes.javascript.Sections.addTags,
        api.routes.javascript.Sections.removeTags,
        api.routes.javascript.Sections.removeAllTags,
        api.routes.javascript.Geostreams.searchSensors,
        api.routes.javascript.Geostreams.searchStreams,
        api.routes.javascript.Geostreams.getSensor,
        api.routes.javascript.Geostreams.getStream,
        api.routes.javascript.Geostreams.getSensorStreams,
        api.routes.javascript.Geostreams.searchDatapoints,
        api.routes.javascript.Geostreams.deleteSensor,
        api.routes.javascript.Geostreams.updateSensorMetadata,
        api.routes.javascript.Geostreams.patchStreamMetadata,
        api.routes.javascript.Collections.list,
        api.routes.javascript.Collections.listCanEdit,
        api.routes.javascript.Collections.addDatasetToCollectionOptions,
        api.routes.javascript.Collections.listPossibleParents,
        api.routes.javascript.Collections.restoreCollection,
        api.routes.javascript.Collections.emptyTrash,
        api.routes.javascript.Selected.get,
        api.routes.javascript.Selected.add,
        api.routes.javascript.Selected.remove,
        api.routes.javascript.Selected.deleteAll,
        api.routes.javascript.Selected.downloadAll,
        api.routes.javascript.Selected.clearAll,
        api.routes.javascript.Selected.tagAll,
        api.routes.javascript.Collections.attachPreview,
        api.routes.javascript.Collections.attachDataset,
        api.routes.javascript.Collections.removeDataset,
        api.routes.javascript.Collections.removeCollection,
        api.routes.javascript.Collections.attachSubCollection,
        api.routes.javascript.Collections.removeSubCollection,
        api.routes.javascript.Collections.follow,
        api.routes.javascript.Collections.unfollow,
        api.routes.javascript.Collections.updateCollectionName,
        api.routes.javascript.Collections.updateCollectionDescription,
        api.routes.javascript.Collections.getCollection,
        api.routes.javascript.Collections.removeFromSpaceAllowed,
        api.routes.javascript.Collections.download,
        api.routes.javascript.Spaces.get,
        api.routes.javascript.Spaces.removeSpace,
        api.routes.javascript.Spaces.list,
        api.routes.javascript.Spaces.listCanEdit,
        api.routes.javascript.Spaces.addCollectionToSpace,
        api.routes.javascript.Spaces.addDatasetToSpace,
        api.routes.javascript.Spaces.removeCollection,
        api.routes.javascript.Spaces.removeDataset,
        api.routes.javascript.Spaces.updateSpace,
        api.routes.javascript.Spaces.updateUsers,
        api.routes.javascript.Spaces.removeUser,
        api.routes.javascript.Spaces.follow,
        api.routes.javascript.Spaces.unfollow,
        api.routes.javascript.Spaces.acceptRequest,
        api.routes.javascript.Spaces.rejectRequest,
        api.routes.javascript.Spaces.verifySpace,
        api.routes.javascript.Tree.getChildrenOfNode,
        api.routes.javascript.Users.getUser,
        api.routes.javascript.Users.findById,
        api.routes.javascript.Users.follow,
        api.routes.javascript.Users.unfollow,
        api.routes.javascript.Users.updateName,
        api.routes.javascript.Users.list,
        api.routes.javascript.Users.keysAdd,
        api.routes.javascript.Users.keysDelete,
        api.routes.javascript.Relations.findTargets,
        api.routes.javascript.Relations.add,
        api.routes.javascript.Relations.delete,
        api.routes.javascript.Projects.addproject,
        api.routes.javascript.Institutions.addinstitution,
        api.routes.javascript.CurationObjects.getCurationObjectOre,
        api.routes.javascript.CurationObjects.findMatchmakingRepositories,
        api.routes.javascript.CurationObjects.retractCurationObject,
        api.routes.javascript.CurationObjects.getCurationFiles,
        api.routes.javascript.CurationObjects.deleteCurationFile,
        api.routes.javascript.CurationObjects.deleteCurationFolder,
        api.routes.javascript.CurationObjects.savePublishedObject,
        api.routes.javascript.CurationObjects.getMetadataDefinitionsByFile,
        api.routes.javascript.CurationObjects.getMetadataDefinitions,
        api.routes.javascript.ContextLD.addContext,
        api.routes.javascript.ContextLD.getContextByName,
        api.routes.javascript.ContextLD.removeById,
        api.routes.javascript.ContextLD.getContextById,
        api.routes.javascript.Metadata.addUserMetadata,
        api.routes.javascript.Metadata.getDefinitions,
        api.routes.javascript.Metadata.getDefinition,
        api.routes.javascript.Metadata.getMetadataDefinition,
        api.routes.javascript.Metadata.getDefinitionsDistinctName,
        api.routes.javascript.Metadata.getAutocompleteName,
        api.routes.javascript.Metadata.getUrl,
        api.routes.javascript.Metadata.addDefinition,
        api.routes.javascript.Metadata.addDefinitionToSpace,
        api.routes.javascript.Metadata.editDefinition,
        api.routes.javascript.Metadata.deleteDefinition,
        api.routes.javascript.Metadata.removeMetadata,
        api.routes.javascript.Metadata.listPeople,
        api.routes.javascript.Metadata.getPerson,
        api.routes.javascript.Metadata.getRepository,
        api.routes.javascript.Metadata.createVocabulary,
        api.routes.javascript.Metadata.updateVocabulary,
        api.routes.javascript.Metadata.deleteVocabulary,
        api.routes.javascript.Events.sendExceptionEmail,
        api.routes.javascript.Extractions.addNewFilesetEvent,
        api.routes.javascript.Extractions.submitFileToExtractor,
        api.routes.javascript.Extractions.submitDatasetToExtractor,
        api.routes.javascript.Extractions.cancelFileExtractionSubmission,
        api.routes.javascript.Extractions.cancelDatasetExtractionSubmission,
        api.routes.javascript.Extractions.addExtractorInfo,
        api.routes.javascript.Extractions.getExtractorInfo,
        api.routes.javascript.Extractions.deleteExtractor,
        api.routes.javascript.Extractions.createExtractorsLabel,
        api.routes.javascript.Extractions.updateExtractorsLabel,
        api.routes.javascript.Extractions.deleteExtractorsLabel,
        api.routes.javascript.Folders.createFolder,
        api.routes.javascript.Folders.deleteFolder,
        api.routes.javascript.Folders.updateFolderName,
        api.routes.javascript.Folders.getAllFoldersByDatasetId,
        api.routes.javascript.Folders.moveFileBetweenFolders,
        api.routes.javascript.Folders.moveFileToDataset,
        api.routes.javascript.Thumbnails.get,
        api.routes.javascript.Tree.getChildrenOfNode,
        controllers.routes.javascript.Login.isLoggedIn,
        controllers.routes.javascript.Login.ldapAuthenticate,
        controllers.routes.javascript.Files.file,
        controllers.routes.javascript.Datasets.dataset,
        controllers.routes.javascript.Datasets.newDataset,
        controllers.routes.javascript.Datasets.createStep2,
        controllers.routes.javascript.ToolManager.launchTool,
        controllers.routes.javascript.ToolManager.getLaunchableTools,
        controllers.routes.javascript.ToolManager.uploadDatasetToTool,
        controllers.routes.javascript.ToolManager.getInstances,
        controllers.routes.javascript.ToolManager.refreshToolSidebar,
        controllers.routes.javascript.ToolManager.removeInstance,
        controllers.routes.javascript.Folders.createFolder,
        controllers.routes.javascript.Datasets.getUpdatedFilesAndFolders,
        controllers.routes.javascript.Collections.collection,
        controllers.routes.javascript.Collections.newCollection,
        controllers.routes.javascript.Collections.newCollectionWithParent,
        controllers.routes.javascript.Spaces.stagingArea,
        controllers.routes.javascript.Extractors.selectExtractors,
        controllers.routes.javascript.Extractors.manageLabels,
        controllers.routes.javascript.Extractors.showJobHistory,
        controllers.routes.javascript.Extractors.submitSelectedExtractions,
        controllers.routes.javascript.CurationObjects.submit,
        controllers.routes.javascript.CurationObjects.getCurationObject,
        controllers.routes.javascript.CurationObjects.getUpdatedFilesAndFolders,
        controllers.routes.javascript.CurationObjects.findMatchingRepositories,
        controllers.routes.javascript.CurationObjects.sendToRepository,
        controllers.routes.javascript.CurationObjects.compareToRepository,
        controllers.routes.javascript.CurationObjects.deleteCuration,
        controllers.routes.javascript.CurationObjects.getStatusFromRepository,
        controllers.routes.javascript.CurationObjects.getPublishedData,
        controllers.routes.javascript.Events.getEvents,
        controllers.routes.javascript.Collections.sortedListInSpace,
        controllers.routes.javascript.Datasets.sortedListInSpace,
        controllers.routes.javascript.Users.sendEmail,
        controllers.routes.javascript.FileLinks.createLink,
        controllers.routes.javascript.Search.search
      )
    ).as(JSON) 
  }

}
