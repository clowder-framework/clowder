package controllers

import javax.inject.{Inject, Singleton}

import api.Permission
import api.Permission._
import play.api.{Logger, Routes}
import play.api.mvc.Action
import services._
import models.{UUID, User, Event}
import play.api.Logger

import scala.collection.mutable.ListBuffer

/**
 * Main application controller.
 */
@Singleton
class Application @Inject() (files: FileService, collections: CollectionService, datasets: DatasetService,
                             spaces: SpaceService, events: EventService, comments: CommentService,
                             sections: SectionService, users: UserService) extends SecuredController {
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
    val datasetsCountAccess = datasets.countAccess(Set[Permission](Permission.ViewDataset), user, request.superAdmin)
    val filesCount = files.count()
    val collectionsCount = collections.count()
    val collectionsCountAccess = collections.countAccess(Set[Permission](Permission.ViewCollection), user, request.superAdmin)
    val spacesCount = spaces.count()
    val spacesCountAccess = spaces.countAccess(Set[Permission](Permission.ViewSpace), user, request.superAdmin)
    val usersCount = users.count()
    //newsfeedEvents is the combination of followedEntities and requestevents, then take the most recent 20 of them.
    var newsfeedEvents = user.fold(List.empty[Event])(u => events.getEvents(u.followedEntities, Some(20)).sorted(Ordering.by((_: Event).created).reverse))
    newsfeedEvents =  (newsfeedEvents ::: events.getRequestEvents(user, Some(20)))
      .sorted(Ordering.by((_: Event).created).reverse).take(20)
    user match {
      case Some(usr) => {
        users.findById(usr.id) match {
          case Some(clowderUser) => {
            val datasetsUser = datasets.listUser(4, Some(clowderUser), request.superAdmin, clowderUser)
            val datasetcommentMap = datasetsUser.map { dataset =>
              var allComments = comments.findCommentsByDatasetId(dataset.id)
              dataset.files.map { file =>
                allComments ++= comments.findCommentsByFileId(file)
                sections.findByFileId(file).map { section =>
                  allComments ++= comments.findCommentsBySectionId(section.id)
                }
              }
              dataset.id -> allComments.size
            }.toMap
            val collectionList = collections.listUser(4, Some(clowderUser), request.superAdmin, clowderUser)
            var collectionsWithThumbnails = collectionList.map {c =>
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
            val spacesUser = spaces.listUser(4, Some(clowderUser),request.superAdmin, clowderUser)
            var followers: List[(UUID, String, String, String)] = List.empty
            for (followerID <- clowderUser.followers.take(3)) {
              var userFollower = users.findById(followerID)
              userFollower match {
                case Some(uFollower) => {
                  var ufEmail = uFollower.email.getOrElse("")
                  followers = followers.++(List((uFollower.id, ufEmail, uFollower.getAvatarUrl(), uFollower.fullName)))
                }
              }
            }
            var followedUsers: List[(UUID, String, String, String)] = List.empty
            var followedFiles: List[(UUID, String, String)] = List.empty
            var followedDatasets: List[(UUID, String, String)] = List.empty
            var followedCollections: List[(UUID, String, String)] = List.empty
            var followedSpaces: List[(UUID, String, String)] = List.empty
            var maxDescLength = 50
            for (tidObject <- clowderUser.followedEntities) {
              if (tidObject.objectType == "user") {
                var followedUser = users.get(tidObject.id)
                followedUser match {
                  case Some(fuser) => {
                    followedUsers = followedUsers.++(List((fuser.id, fuser.fullName, fuser.email.get, fuser.getAvatarUrl())))
                  }
                }
              } else if (tidObject.objectType == "file") {
                var followedFile = files.get(tidObject.id)
                followedFile match {
                  case Some(ffile) => {
                    followedFiles = followedFiles.++(List((ffile.id, ffile.filename, ffile.contentType)))
                  }
                }
              } else if (tidObject.objectType == "dataset") {
                var followedDataset = datasets.get(tidObject.id)
                followedDataset match {
                  case Some(fdset) => {
                    followedDatasets = followedDatasets.++(List((fdset.id, fdset.name, fdset.description.substring(0, Math.min(maxDescLength, fdset.description.length())))))
                  }
                }
              } else if (tidObject.objectType == "collection") {
                var followedCollection = collections.get(tidObject.id)
                followedCollection match {
                  case Some(fcoll) => {
                    followedCollections = followedCollections.++(List((fcoll.id, fcoll.name, fcoll.description.substring(0, Math.min(maxDescLength, fcoll.description.length())))))
                  }
                }
              } else if (tidObject.objectType == "'space") {
                var followedSpace = spaces.get(tidObject.id)
                followedSpace match {
                  case Some(fspace) => {
                    followedSpaces = followedSpaces.++(List((fspace.id, fspace.name, fspace.description.substring(0, Math.min(maxDescLength, fspace.description.length())))))
                  }
                }
              }
            }
            Ok(views.html.home(AppConfiguration.getDisplayName, newsfeedEvents, clowderUser, datasetsUser, datasetcommentMap, decodedCollections.toList, spacesUser, true, followers, followedUsers,
           followedFiles, followedDatasets, followedCollections,followedSpaces, Some(true)))
          }
          case None =>  Ok(views.html.index(latestFiles, datasetsCount, datasetsCountAccess, filesCount, collectionsCount, collectionsCountAccess,
            spacesCount, spacesCountAccess, usersCount, AppConfiguration.getDisplayName, AppConfiguration.getWelcomeMessage))

        }
      }
      case None => Ok(views.html.index(latestFiles, datasetsCount, datasetsCountAccess, filesCount, collectionsCount, collectionsCountAccess,
        spacesCount, spacesCountAccess, usersCount, AppConfiguration.getDisplayName, AppConfiguration.getWelcomeMessage))
    }
  }

  def about = UserAction { implicit request =>
    implicit val user = request.user
    val latestFiles = files.latest(5)
    val datasetsCount = datasets.count()
    val datasetsCountAccess = datasets.countAccess(Set[Permission](Permission.ViewDataset), user, request.superAdmin)
    val filesCount = files.count()
    val collectionsCount = collections.count()
    val collectionsCountAccess = collections.countAccess(Set[Permission](Permission.ViewCollection), user, request.superAdmin)
    val spacesCount = spaces.count()
    val spacesCountAccess = spaces.countAccess(Set[Permission](Permission.ViewSpace), user, request.superAdmin)
    val usersCount = users.count()

    Ok(views.html.index(latestFiles, datasetsCount, datasetsCountAccess, filesCount, collectionsCount, collectionsCountAccess,
        spacesCount, spacesCountAccess, usersCount, AppConfiguration.getDisplayName, AppConfiguration.getWelcomeMessage))
  }
  
  def options(path:String) = UserAction { implicit request =>
    Logger.info("---controller: PreFlight Information---")
    Ok("")
   }

  def apidoc(path: String) = ApiHelpController.getResource("/api-docs.json/" + path)

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
        routes.javascript.Files.fileBySection,
        routes.javascript.Datasets.dataset,
        routes.javascript.Datasets.datasetBySection,
        routes.javascript.Geostreams.list,
        routes.javascript.Collections.collection,
        routes.javascript.RedirectUtility.authenticationRequiredMessage,
        routes.javascript.Profile.viewProfileUUID,
        api.routes.javascript.Admin.removeAdmin,        
        api.routes.javascript.Comments.comment,
        api.routes.javascript.Comments.removeComment,
        api.routes.javascript.Comments.editComment,
        api.routes.javascript.Datasets.get,
        api.routes.javascript.Datasets.list,
        api.routes.javascript.Datasets.listCanEdit,
        api.routes.javascript.Datasets.comment,
        api.routes.javascript.Datasets.createEmptyDataset,
        api.routes.javascript.Datasets.attachExistingFile,
        api.routes.javascript.Datasets.attachMultipleFiles,
        api.routes.javascript.Datasets.deleteDataset,
        api.routes.javascript.Datasets.detachAndDeleteDataset,
        api.routes.javascript.Datasets.datasetFilesList,
        api.routes.javascript.Datasets.getTechnicalMetadataJSON,
        api.routes.javascript.Datasets.listInCollection,
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
        api.routes.javascript.Datasets.download,
        api.routes.javascript.Datasets.getPreviews,
        api.routes.javascript.Files.download,
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
        api.routes.javascript.Search.searchMultimediaIndex,
        api.routes.javascript.Sections.add,
        api.routes.javascript.Sections.delete,
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
        api.routes.javascript.Collections.list,
        api.routes.javascript.Collections.listCanEdit,
        api.routes.javascript.Collections.attachPreview,
        api.routes.javascript.Collections.attachDataset,
        api.routes.javascript.Collections.removeDataset,
        api.routes.javascript.Collections.removeCollection,
        api.routes.javascript.Collections.follow,
        api.routes.javascript.Collections.unfollow,
        api.routes.javascript.Collections.updateCollectionName,
        api.routes.javascript.Collections.updateCollectionDescription,
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
        api.routes.javascript.Users.getUser,
        api.routes.javascript.Users.follow,
        api.routes.javascript.Users.unfollow,
        api.routes.javascript.Relations.findTargets,
        api.routes.javascript.Relations.add,
        api.routes.javascript.Relations.delete,
        api.routes.javascript.Projects.addproject,
        api.routes.javascript.Institutions.addinstitution,
        api.routes.javascript.Users.getUser,
        api.routes.javascript.CurationObjects.getCurationObjectOre,
        api.routes.javascript.CurationObjects.findMatchmakingRepositories,
        api.routes.javascript.CurationObjects.retractCurationObject,
        api.routes.javascript.Metadata.addUserMetadata,
        api.routes.javascript.Metadata.searchByKeyValue,
        api.routes.javascript.Metadata.getDefinitions,
        api.routes.javascript.Metadata.getDefinition,
        api.routes.javascript.Metadata.getUrl,
        api.routes.javascript.Metadata.addDefinition,
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
