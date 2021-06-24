package services.mongodb

import java.net.URL
import java.util.{Calendar, Date}

import com.mongodb.{BasicDBObject, CommandFailureException}
import com.mongodb.casbah.Imports._
import com.mongodb.casbah.commons.MongoDBObject
import MongoContext.context
import api.Permission
import models._
import org.apache.commons.codec.digest.DigestUtils
import org.apache.commons.io.input.CountingInputStream
import org.bson.BSONException
import play.api.libs.json._
import play.api.{Application, Logger, Play, Plugin}
import play.api.Play.current
import com.mongodb.casbah.MongoURI
import com.mongodb.casbah.MongoConnection
import com.mongodb.casbah.MongoDB
import com.mongodb.casbah.MongoCollection
import com.mongodb.casbah.gridfs.GridFS
import com.mongodb.casbah.Imports.DBObject
import org.bson.types.ObjectId
import services.filesystem.DiskByteStorageService
import services.{AppConfigurationService, ByteStorageService, DI, MetadataService}

import scala.collection.JavaConverters._
import scala.collection.mutable.{Map => MutaMap}

/**
 * Mongo Salat service.
 */
class MongoSalatPlugin(app: Application) extends Plugin {
  // URI to the mongodatabase, for example mongodb://127.0.0.1:27017/clowder
  var mongoURI: MongoURI = null

  // hold the connection, if connection failed it will be tried to open next time
  var mongoConnection: MongoConnection = null

  lazy val appConfig: AppConfigurationService = DI.injector.getInstance(classOf[AppConfigurationService])

  override def onStart() {
    mongoURI = if (play.api.Play.configuration.getString("mongodbURI").isDefined) {
      MongoURI(play.api.Play.configuration.getString("mongodbURI").get)
    } else if (play.api.Play.configuration.getString("mongodb.default").isDefined) {
      Logger.info("mongodb.default is deprecated, please use mongodbURI")
      MongoURI(play.api.Play.configuration.getString("mongodb.default").get)
    } else {
      Logger.info("no connection to mongo specified in , will use default URI mongodb://127.0.0.1:27017/clowder")
      MongoURI("mongodb://127.0.0.1:27017/clowder")
    }
    Logger.info("Connecting to : " + mongoURI.toString())

    // connect to the database
    mongoConnection = mongoURI.connect.fold(l => throw l, r => r)

    // update database if needed
    updateDatabase()

    // drop old indices
    scala.util.control.Exception.ignoring(classOf[CommandFailureException]) {
      collection("datasets").dropIndex("tags.name_text")
    }
    scala.util.control.Exception.ignoring(classOf[CommandFailureException]) {
      collection("uploads.files").dropIndex("tags.name_text")
    }
    scala.util.control.Exception.ignoring(classOf[CommandFailureException]) {
      collection("uploads.files").dropIndex("tags_1")
    }
    scala.util.control.Exception.ignoring(classOf[CommandFailureException]) {
      collection("sections").dropIndex("tags.name_text")
    }
    scala.util.control.Exception.ignoring(classOf[CommandFailureException]) {
      collection("uploads.files").dropIndex("uploadDate_-1")
    }
    scala.util.control.Exception.ignoring(classOf[CommandFailureException]) {
      collection("uploads.files").dropIndex("author.email_1")
    }
    scala.util.control.Exception.ignoring(classOf[CommandFailureException]) {
      collection("uploads.files").dropIndex("tags.name_1")
    }
    scala.util.control.Exception.ignoring(classOf[CommandFailureException]) {
      collection("uploads.files").dropIndex("filename_1_uploadDate_1")
    }
    scala.util.control.Exception.ignoring(classOf[CommandFailureException]) {
      collection("previews.files").dropIndex("uploadDate_-1_file_id_1")
    }
    scala.util.control.Exception.ignoring(classOf[CommandFailureException]) {
      collection("previews.files").dropIndex("uploadDate_-1_section_id_1")
    }
    scala.util.control.Exception.ignoring(classOf[CommandFailureException]) {
      collection("previews.files").dropIndex("section_id_-1")
    }
    scala.util.control.Exception.ignoring(classOf[CommandFailureException]) {
      collection("previews.files").dropIndex("file_id_-1")
    }
    scala.util.control.Exception.ignoring(classOf[CommandFailureException]) {
      collection("previews.files").dropIndex("filename_1_uploadDate_1")
    }
    scala.util.control.Exception.ignoring(classOf[CommandFailureException]) {
      collection("textures.files").dropIndex("file_id_1")
    }
    scala.util.control.Exception.ignoring(classOf[CommandFailureException]) {
      collection("tiles.files").dropIndex("preview_id_1_filename_1_level_1")
    }

    // create indices.
    Logger.debug("Ensuring indices exist")
    collection("spaces.projects").ensureIndex(MongoDBObject("created" -> -1))
    collection("spaces.projects").ensureIndex(MongoDBObject("public" -> 1))
    collection("spaces.projects").ensureIndex(MongoDBObject("creator" -> 1))

    collection("collections").ensureIndex(MongoDBObject("created" -> -1))
    collection("collections").ensureIndex(MongoDBObject("spaces" -> 1))
    collection("collections").ensureIndex(MongoDBObject("datasets._id" -> 1))
    collection("collections").ensureIndex(MongoDBObject("public" -> 1))
    collection("collections").ensureIndex(MongoDBObject("author._id" -> 1))
    collection("collections").ensureIndex(MongoDBObject("stats" -> 1))

    collection("comments").ensureIndex(MongoDBObject("file_id" -> 1))
    collection("comments").ensureIndex(MongoDBObject("dataset_id" -> 1))
    collection("comments").ensureIndex(MongoDBObject("comment_id" -> 1))

    collection("datasets").ensureIndex(MongoDBObject("created" -> -1))
    collection("datasets").ensureIndex(MongoDBObject("tags" -> 1))
    collection("datasets").ensureIndex(MongoDBObject("files._id" -> 1))
    collection("datasets").ensureIndex(MongoDBObject("tags.name" -> 1))
    collection("datasets").ensureIndex(MongoDBObject("status" -> 1))
    collection("datasets").ensureIndex(MongoDBObject("collections" -> 1))
    collection("datasets").ensureIndex(MongoDBObject("spaces" -> 1))
    collection("datasets").ensureIndex(MongoDBObject("public" -> 1))
    collection("datasets").ensureIndex(MongoDBObject("name" -> 1))
    collection("datasets").ensureIndex(MongoDBObject("author._id" -> 1))
    collection("datasets").ensureIndex(MongoDBObject("status" -> 1, "spaces" -> 1, "author._id" -> 1))
    collection("datasets").ensureIndex(MongoDBObject("created" -> -1, "name" -> 1))
    collection("datasets").ensureIndex(MongoDBObject("files" -> 1))
    collection("datasets").ensureIndex(MongoDBObject("stats" -> 1))

    collection("dtsrequests").ensureIndex(MongoDBObject("fileid" -> 1))

    collection("events").ensureIndex(MongoDBObject("targetuser._id" -> 1))
    collection("events").ensureIndex(MongoDBObject("object_id" -> 1))
    collection("events").ensureIndex(MongoDBObject("user._id" -> 1))

    collection("extractions").ensureIndex(MongoDBObject("file_id" -> 1))

    collection("folders").ensureIndex(MongoDBObject("parentDatasetId" -> 1))
    collection("folders").ensureIndex(MongoDBObject("files" -> 1))

    collection("uploads").ensureIndex(MongoDBObject("uploadDate" -> -1))
    collection("uploads").ensureIndex(MongoDBObject("author.email" -> 1))
    collection("uploads").ensureIndex(MongoDBObject("tags.name" -> 1))
    collection("uploads").ensureIndex(MongoDBObject("author._id" -> 1, "_id" -> 1))
    collection("uploads").ensureIndex(MongoDBObject("status" -> 1))
    collection("uploads").ensureIndex(MongoDBObject("stats" -> 1))

    collection("uploadquery.files").ensureIndex(MongoDBObject("uploadDate" -> -1))

    collection("previews").ensureIndex(MongoDBObject("uploadDate" -> -1, "file_id" -> 1))
    collection("previews").ensureIndex(MongoDBObject("uploadDate" -> -1, "section_id" -> 1))
    collection("previews").ensureIndex(MongoDBObject("section_id" -> -1))
    collection("previews").ensureIndex(MongoDBObject("file_id" -> -1))

    collection("textures").ensureIndex(MongoDBObject("file_id" -> 1))
    collection("tiles").ensureIndex(MongoDBObject("preview_id" -> 1, "filename" -> 1, "level" -> 1))

    collection("sections").ensureIndex(MongoDBObject("uploadDate" -> -1, "file_id" -> 1))
    collection("sections").ensureIndex(MongoDBObject("file_id" -> -1))
    collection("sections").ensureIndex(MongoDBObject("tags.name" -> 1))
    collection("sections").ensureIndex(MongoDBObject("file_id" -> 1, "author._id" -> 1))

    collection("metadata").ensureIndex(MongoDBObject("createdAt" -> -1))
    collection("metadata").ensureIndex(MongoDBObject("creator" -> 1))
    collection("metadata").ensureIndex(MongoDBObject("attachedTo" -> 1))
    collection("metadata").ensureIndex(MongoDBObject("attachedTo.resourceType" -> 1, "attachedTo._id" -> 1))

    collection("contextld").ensureIndex(MongoDBObject("contextName" -> 1))

    collection("statistics.users").ensureIndex(MongoDBObject("user_id" -> 1, "resource_id" -> 1))

    collection("dtsrequests").ensureIndex(MongoDBObject("startTime" -> -1, "endTime" -> -1))
    collection("dtsrequests").ensureIndex(MongoDBObject("file_id" -> -1))
    collection("versus.descriptors").ensureIndex(MongoDBObject("fileId" -> 1))

    collection("multimedia.distances").ensureIndex(MongoDBObject("source_section" -> 1, "representation" -> 1, "distance" -> 1, "target_spaces" -> 1))
  }

  override def onStop() {
    if (mongoConnection != null)
      mongoConnection.close()
    mongoConnection = null
  }

  /**
   * Returns the database for the connection
   */
  def getDB: MongoDB = mongoConnection.getDB(mongoURI.database.getOrElse("clowder"))

  /**
   * Returns a collection in the database
   */
  def collection(collection: String): MongoCollection = getDB(collection)

  /**
   * Based on the resourceRef return the mongo collection.
   */
  def collection(resourceRef: ResourceRef): Option[MongoCollection] = collection(resourceRef.resourceType)

  def collection(resourceType: Symbol): Option[MongoCollection] = {
    resourceType match {
      case ResourceRef.space => Some(collection("spaces.projects"))
      case ResourceRef.dataset => Some(collection("datasets"))
      case ResourceRef.file => Some(collection("uploads"))
      //case ResourceRef.relation => Some(collection("hello"))
      case ResourceRef.preview => Some(collection("previews"))
      case ResourceRef.thumbnail => Some(collection("thumbnails"))
      case ResourceRef.collection => Some(collection("collections"))
      case ResourceRef.user => Some(collection("social.users"))
      case ResourceRef.comment => Some(collection("comments"))
      case ResourceRef.section => Some(collection("sections"))
      case ResourceRef.curationObject => Some(collection("curationObjects"))
      case ResourceRef.curationFile => Some(collection("curationFiles"))
      case _ => {
        Logger.error(s"Can not map resource ${resourceType} to collection.")
        None
      }
    }
  }

  /**
   * Returns a GridFS for writing files, the files will be placed in
   * two collections that start with the prefix (&lt;prefix&gt;.fs and
   * &lt;prefix.chunks&gt;).
   */
  def gridFS(prefix: String = "fs"): GridFS = GridFS(getDB, prefix)

  /**
   * Drop all collections
   */
  def dropAllData(resetAll: Boolean) {
    Logger.debug("**DANGER** Deleting data collections **DANGER**")

    collection("collections").drop()
    collection("comments").drop()
    collection("contextld").drop()
    collection("curationFiles").drop()
    collection("curationFolders").drop()
    collection("curationObjects").drop()
    collection("datasets").drop()
    collection("datasetxmlmetadata").drop()
    collection("dtsrequests").drop()
    collection("events").drop()
    collection("extractions").drop()
    collection("extractor.details").drop()
    collection("extractor.inputtypes").drop()
    collection("extractor.names").drop()
    collection("extractor.servers").drop()
    collection("extractors.info").drop()
    collection("folders").drop()
    removeFiles("geometries")
    collection("licensedata").drop()
    collection("metadata").drop()
    collection("metadata.definitions").drop()
    collection("multimedia.distances").drop()
    collection("multimedia.features").drop()
    removeFiles("previews")
    collection("previews.files.annotations").drop()
    collection("relations").drop()
    collection("sectionIndexInfo").drop()
    collection("sections").drop()
    collection("spaceandrole").drop()
    collection("spaces.extractors").drop()
    collection("spaces.invites").drop()
    collection("spaces.projects").drop()
    collection("spaces.users").drop()
    collection("streams").drop()
    collection("tags").drop()
    removeFiles("textures")
    removeFiles("thumbnails")
    removeFiles("tiles")
    collection("uploadquery.files").drop()
    removeFiles("uploads")
    collection("versus.descriptors").drop()
    collection("webpage.resources").drop()

    if (resetAll) {
      collection("app.configuration").drop()
      collection("institutions").drop()
      collection("jobs").drop()
      collection("projects").drop()
      collection("social.authenticator").drop()
      collection("social.token").drop()
      collection("social.users").drop()
      collection("roles").drop()
      removeFiles("logos")

      // call global onStart to initialize
      app.global.onStart(app)
    }

    // call onStart to make sure all indices exist.
    onStart()

    Logger.debug("**DANGER** Data deleted **DANGER**")
  }

  private def removeFiles(name: String): Unit = {
    // delete blobs
    collection(name + ".files").find(new BasicDBObject("loader", new BasicDBObject("$ne", classOf[MongoDBByteStorage].getName))).foreach { x =>
      (x.getAs[String]("path"), x.getAs[String]("loader")) match {
        case (Some(p), Some(l)) => ByteStorageService.delete(l, p, name)
        case _ =>
      }
    }
    collection(name + ".chunks").drop()
    collection(name + ".files").drop()
    collection(name).drop()
  }

  // ----------------------------------------------------------------------
  // CODE TO UPDATE THE DATABASE
  // ----------------------------------------------------------------------
  def updateDatabase() {

    //add trash field dataset
    updateMongo("add-trash-dataset", addDateMovedToTrashDatasets)

    //add trash field collection
    updateMongo("add-trash-collection", addDateMovedToTrashCollections)

    // migrate users to new model
    updateMongo("fixing-typehint-users", updateMongoChangeUserType)

    // add a space if none exists
    updateMongo("convert-to-spaces", updateMongoAddFirstSpace)

    // fix tag length to 100 chars
    updateMongo("fixing-taglength", updateTagLength)

    // remove datasets from collection
    updateMongo("removed-datasets-collection", updateMongoRemoveDatasetCollection)

    // replace collection id strings with UUID in datasets
    updateMongo("replace-dataset-collections-string-uuid", updateMongoCollectionsInDatasetStringToUUID)

    // change Files in datasets from List[File] to List[UUID]
    updateMongo("replace-dataset-files-with-id", updateReplaceFilesInDataset)

    // migrate metadata to jsonld
    updateMongo("migrate-metadata-jsonld", migrateMetadataRepresentationtoJSONLD)

    // collection now requires author
    updateMongo("collection-author", collectionRequiresAuthor)

    // Adds creation date and expiration date to Space Invites assumes they were just created.
    updateMongo("update-space-invites", updateSpaceInvites)

    // Add file length, sha512 to all uploads and fixes path
    updateMongo("update-file-length-sha512-path", addLengthSha512PathFile)

    // Fix sha512
    updateMongo("fixing-mongo-sha512", fixSha512)

    //remove Affiliation and License, access and cost in user.repositoryPreferences
    updateMongo("update-user-preferences", updateUserPreference)

    // activate all users
    updateMongo("activate-users", activateAllUsers)

    //Move the current notes in files to description. And delete the notes field
    updateMongo("migrate-notes-files", migrateNotesInFiles)

    //Append the current notes to the end of the description in datasets. And delete the notes field
    updateMongo("migrate-notes-datasets", migrateNotesInDatasets)

    //Add author and creation date to folders
    updateMongo("add-creator-to-folders", addAuthorAndDateToFolders)

    //Add author and creation date to curation folders.
    updateMongo("add-creator-to-curation-folders", addAuthorAndDateToCurationFolders)

    //don't use gridfs for metadata
    updateMongo("split-gridfs", splitGridFSMetadata)

    //Store admin in database not by email
    updateMongo("add-admin-to-user-object", addAdminFieldToUser)

    // Change creator in dataset from Identity to MiniUser.
    updateMongo("use-miniuser", useMiniUser)

    //Whenever a root flag is not set, mark it as true.
    updateMongo("add-collection-root-map", addRootMapToCollections)

    // update the number of collections in a space
    updateMongo("update-collection-counter-in-space", fixCollectionCounterInSpaces)

    // rename admin to serverAdmin to make clear what type of admin they are
    updateMongo("rename-admin-serverAdmin", renameAdminServerAdmin)

    //Update all object_name & source_name in events
    updateMongo("update-events-name", updateEventObjectName)

    updateMongo("update-user-spaces", removeDeletedSpacesFromUser)

    updateMongo("update-counts-spaces", updateCountsInSpaces)

    //add private (the default status) flag for each dataset/collection/space
    updateMongo("add-trial-flag", addTrialFlag)

    // instead of user agreeent we now have a terms of services
    updateMongo("switch-user-agreement-to-terms-of-services", switchToTermsOfServices)

    updateMongo("fix-metadata-count", fixMetadataCount)

    // add status field to files
    updateMongo("add-file-status", addFileStatus)

    // Duplicate all clowder instance metadata to all existing spaces
    updateMongo("add-metadata-per-space", addMetadataPerSpace)

    updateMongo("add-trial-flag2", addTrialFlag2)

    // Make sure all email addresses of userpassword are lowercase
    updateMongo("user-emails-to-lowercase", updateMongoEmailCase)

    // Move SHA512 from File object into file.digest metadata
    updateMongo("copy-sha512-to-metadata-and-remove-all", copySha512ToMetadataAndRemove)

    // Change repository in extractors.info collection into a list
    updateMongo("update-repository-type-in-extractors-info", updateRepositoryType)

    // Change existing 'In Curation' curation objects/pub requests to 'In Prepaparation' 
    updateMongo("change-in-curation-status-to-in-preparation", updateInCurationStatus)

    // Change from User active and serverAdmin flags to single status 
    updateMongo("change-to-user-status", updateToUserStatus)
    
    // Capture original filename from FRBR metadata supplied by SEAD Migrator 
    updateMongo("populate-original-filename", updateOriginalFilename)

    // Removes the private key from extraction logs
    updateMongo("remove-key-extraction-log", removeKeyFromExtractorLogs)

    // Updates gravatar url's from http to https
    updateMongo("update-avatar-url-to-https", updateAvatarUrl)

    // Updates permissions for the editor Role
    updateMongo("update-editor-role", updateEditorRole)

    // Updates permissions for the admin Role
    updateMongo("update-admin-role", updateAdminRole)

    // Updates extractors enabled and disabled in a space
    updateMongo("update-space-extractors-selection", updateSpaceExtractorsSelection)
  }

  private def updateMongo(updateKey: String, block: () => Unit): Unit = {
    if (!appConfig.hasPropertyValue("mongodb.updates", updateKey)) {
      if (System.getProperty("MONGOUPDATE") != null) {
        Logger.info(s"About to begin update of mongo : ${updateKey}.")
        val start = System.currentTimeMillis()
        try {
          block()
          appConfig.addPropertyValue("mongodb.updates", updateKey)
        } catch {
          case e: Exception => {
            Logger.error(s"Could not run mongo update for ${updateKey}", e)
          }
        }
        val time = (System.currentTimeMillis() - start) / 1000.0
        Logger.info(s"Took ${time} second to migrate mongo : ${updateKey}")
      } else {
        Logger.warn(s"Missing mongo update ${updateKey}. Application might be broken.")
      }
    }
  }

  private def addDateMovedToTrashCollections() {
    val q = MongoDBObject()
    val s = MongoDBObject("$set" -> MongoDBObject("dateMovedToTrash" -> None, "trash"->false))
    collection("collections").update(q,s, multi=true)
  }

  private def addDateMovedToTrashDatasets() {
    val q = MongoDBObject()
    val s = MongoDBObject("$set" -> MongoDBObject("dateMovedToTrash" -> None, "trash"->false))
    collection("datasets").update(q,s, multi=true)
  }

  private def updateMongoChangeUserType() {
    val q = MongoDBObject("_typeHint" -> "securesocial.core.SocialUser")
    val o = MongoDBObject("$set" -> MongoDBObject("_typeHint" -> "models.ClowderUser"))
    collection("social.users").update(q, o, multi = true, concern = WriteConcern.Safe)
  }

  private def updateMongoAddFirstSpace() {
    val spaces = ProjectSpaceDAO.count(new MongoDBObject())
    if (spaces == 0) {
      val datasets = Dataset.count(new MongoDBObject())
      val collections = Collection.count(new MongoDBObject())
      val users = SocialUserDAO.count(new MongoDBObject())
      if ((datasets != 0) || (collections != 0)) {
        Logger.debug("[MongoDBUpdate] : Found datasets/collections, will add all to default space")

        // create roles (this is called before Global)
        if (RoleDAO.count() == 0) {
          RoleDAO.save(Role.Admin)
          RoleDAO.save(Role.Editor)
          RoleDAO.save(Role.Viewer)
        }

        // create the space
        val spacename = java.net.InetAddress.getLocalHost.getHostName
        val newspace = new ProjectSpace(name = spacename, description = "", created = new Date(), creator = UUID("000000000000000000000000"),
          homePage = List.empty[URL], logoURL = None, bannerURL = None, metadata = List.empty[Metadata],
          collectionCount = collections.toInt, datasetCount = datasets.toInt, userCount = users.toInt)
        ProjectSpaceDAO.save(newspace)
        val spaceId = new ObjectId(newspace.id.stringify)

        // add space to all datasets/collections
        val q = MongoDBObject()
        val o = MongoDBObject("$set" -> MongoDBObject("spaces" -> List[ObjectId](spaceId)))
        collection("datasets").update(q, o, multi = true)
        collection("collections").update(q, o, multi = true)

        // add all users as admin
        val adminRole = collection("roles").findOne(MongoDBObject("name" -> "Admin"))
        val spaceRole = MongoDBObject("_typeHint" -> "models.UserSpaceAndRole", "spaceId" -> spaceId, "role" -> adminRole)
        collection("social.users").update(MongoDBObject(), $push("spaceandrole" -> spaceRole), multi = true)

      } else {
        Logger.debug("[MongoDBUpdate] : No datasets/collections found, will not create default space")
      }
    } else {
      Logger.debug("[MongoDBUpdate] : Found spaces, will not create default space")
    }
  }

  def updateTagLength() {
    val q = MongoDBObject("tags" -> MongoDBObject("$exists" -> true, "$not" -> MongoDBObject("$size" -> 0)))
    val maxTagLength = play.api.Play.configuration.getInt("clowder.tagLength").getOrElse(100)
    Logger.debug("[MongoDBUpdate] : fixing " + collection("datasets").count(q) + " datasets")
    collection("datasets").find(q).foreach { x =>
      x.getAsOrElse[MongoDBList]("tags", MongoDBList.empty).foreach {
        case tag: DBObject =>
          if (tag.getAsOrElse[String]("name", "").length > maxTagLength) {
            Logger.debug(x.get("_id").toString + " : truncating " + tag.getAsOrElse[String]("name", ""))
            tag.put("name", tag.getAsOrElse[String]("name", "").substring(0, maxTagLength))
          }
      }
      try {
        collection("datasets").save(x)
      } catch {
        case e: BSONException => {
          Logger.error(x.get("_id").toString + " : bad string\n" + x.toString, e)
        }
      }
    }
    collection("uploads.files").find(q).foreach { x =>
      x.getAsOrElse[MongoDBList]("tags", MongoDBList.empty).foreach {
        case tag: DBObject =>
          if (tag.getAsOrElse[String]("name", "").length > maxTagLength) {
            Logger.debug(x.get("_id").toString + " : truncating " + tag.getAsOrElse[String]("name", ""))
            tag.put("name", tag.getAsOrElse[String]("name", "").substring(0, maxTagLength))
          }
      }
      try {
        collection("uploads.files").save(x)
      } catch {
        case e: BSONException => {
          Logger.error(x.get("_id").toString + " : bad string\n" + x.toString, e)
        }
      }
    }
    collection("sections").find(q).foreach { x =>
      x.getAsOrElse[MongoDBList]("tags", MongoDBList.empty).foreach {
        case tag: DBObject =>
          if (tag.getAsOrElse[String]("name", "").length > maxTagLength) {
            Logger.debug(x.get("_id").toString + " : truncating " + tag.getAsOrElse[String]("name", ""))
            tag.put("name", tag.getAsOrElse[String]("name", "").substring(0, maxTagLength))
          }
      }
      try {
        collection("sections").save(x)
      } catch {
        case e: BSONException => {
          Logger.error(x.get("_id").toString + " : bad string\n" + x.toString, e)
        }
      }
    }
  }

  private def updateMongoRemoveDatasetCollection() {
    collection("collections").foreach { c =>
      val datasets = c.getAsOrElse[MongoDBList]("datasets", MongoDBList.empty)
      c.removeField("datasets")
      c.put("datasetCount", datasets.length)
      collection("collections").save(c, WriteConcern.Safe)

      datasets.foreach { d =>
        if (c._id.isDefined) {
          collection("datasets").update(MongoDBObject("_id" -> d.asInstanceOf[DBObject].get("_id")), $addToSet("collections" -> c._id.get.toString))
        }
      }
    }
  }

  private def updateMongoCollectionsInDatasetStringToUUID() {
    collection("datasets").foreach { ds =>
      val collection_string = ds.getAsOrElse[MongoDBList]("collections", MongoDBList.empty)
      val collection_uuids = collection_string.map(col => new ObjectId(col.toString)).toList
      ds.put("collections", collection_uuids)
      try {
        collection("datasets").save(ds, WriteConcern.Safe)
      } catch {
        case e: BSONException => Logger.error("Failed to refactor collections (String -> UUID) in  dataset with id" + ds.getAsOrElse[ObjectId]("_id", new ObjectId()).toString)
      }
    }
  }

  /**
   * Replaces the files in the datasets from a copy of the files to just the file UUID's.
   */
  private def updateReplaceFilesInDataset() {
    collection("datasets").foreach { ds =>
      val files = ds.getAsOrElse[MongoDBList]("files", MongoDBList.empty)
      //Following statement fails if datasets with fileIds already exist
      val fileIds = files.map(file => new ObjectId(file.asInstanceOf[BasicDBObject].get("_id").toString)).toList
      ds.put("files", fileIds)
      try {
        collection("datasets").save(ds, WriteConcern.Safe)
      } catch {
        case e: BSONException => Logger.error("Unable to update files in dataset:" + ds.getAsOrElse[ObjectId]("_id", new ObjectId()).toString)
      }
    }
  }

  private def migrateMetadataRepresentationtoJSONLD() {
    val metadataService: MetadataService = DI.injector.getInstance(classOf[MetadataService])

    // update metadata on datasets
    collection("datasets").foreach { ds =>
      ds.getAs[ObjectId]("_id") match {
        case Some(dsId) => {
          val createdAt = new Date()
          val attachedTo = Some(ResourceRef(ResourceRef.dataset, UUID(dsId.toString)))
          val contextURL: Option[URL] = None
          val contextID: Option[UUID] = None
          val version = None
          // user metadata
          ds.getAs[DBObject]("userMetadata") match {
            case Some(umd) => {
              if (umd.keySet().size() > 0) {
                val userMD = Json.parse(com.mongodb.util.JSON.serialize(umd))
                val user = User.anonymous
                val userURI = "https://clowder.ncsa.illinois.edu/clowder/api/users/" + user.id
                val creatorUser = UserAgent(user.id, "cat:user", MiniUser(user.id, user.fullName, user.avatarUrl.getOrElse(""), user.email), Some(new URL(userURI)))
                val metadataUser = models.Metadata(UUID.generate(), attachedTo.get, contextID, contextURL, createdAt, creatorUser, userMD, version)
                metadataService.addMetadata(metadataUser)
              }
            }
            case None => {}
          }
          // system metadata
          ds.getAs[DBObject]("metadata") match {
            case Some(tmd) => {
              if (tmd.keySet().size() > 0) {
                val techMD = Json.parse(com.mongodb.util.JSON.serialize(tmd))
                val creatorExtractor = ExtractorAgent(id = UUID.generate(), extractorId = Some(new URL("http://clowder.ncsa.illinois.edu/extractors/migration")))
                val metadataTech = models.Metadata(UUID.generate(), attachedTo.get, contextID, contextURL, createdAt, creatorExtractor, techMD, version)
                metadataService.addMetadata(metadataTech)
              }
            }
            case None => {}
          }
        }
        case None => Logger.error(s"[MongoDBUpdate : Missing dataset id")
      }
    }
    // update metadata on files
    collection("uploads.files").foreach { ds =>
      ds.getAs[ObjectId]("_id") match {
        case Some(fileId) => {
          val createdAt = new Date()
          val attachedTo = Some(ResourceRef(ResourceRef.file, UUID(fileId.toString)))
          val contextURL: Option[URL] = None
          val contextID: Option[UUID] = None
          val version = None
          // user metadata
          ds.getAs[DBObject]("userMetadata") match {
            case Some(umd) => {
              if (umd.keySet().size() > 0) {
                val userMD = Json.parse(com.mongodb.util.JSON.serialize(umd))
                val user = User.anonymous
                val userURI = "https://clowder.ncsa.illinois.edu/clowder/api/users/" + user.id
                val creatorUser = UserAgent(user.id, "cat:user", MiniUser(user.id, user.fullName, user.avatarUrl.getOrElse(""), user.email), Some(new URL(userURI)))
                val metadataUser = models.Metadata(UUID.generate(), attachedTo.get, contextID, contextURL, createdAt, creatorUser, userMD, version)
                metadataService.addMetadata(metadataUser)
              }
            }
            case None => {}
          }
          // system metadata
          if (ds.containsField("metadata")) {
            val tmd = ds.get("metadata")
            if (tmd.isInstanceOf[BasicDBList]) {
              val tmdlist = tmd.asInstanceOf[BasicDBList]
              tmdlist.foreach { x =>
                val techMD = Json.parse(com.mongodb.util.JSON.serialize(x))
                val creatorExtractor = ExtractorAgent(id = UUID.generate(), extractorId = Some(new URL("http://clowder.ncsa.illinois.edu/extractors/migration")))
                val metadataTech = models.Metadata(UUID.generate(), attachedTo.get, contextID, contextURL, createdAt, creatorExtractor, techMD, version)
                metadataService.addMetadata(metadataTech)
              }
            } else {
              val techMD = Json.parse(com.mongodb.util.JSON.serialize(tmd))
              val creatorExtractor = ExtractorAgent(id = UUID.generate(), extractorId = Some(new URL("http://clowder.ncsa.illinois.edu/extractors/migration")))
              val metadataTech = models.Metadata(UUID.generate(), attachedTo.get, contextID, contextURL, createdAt, creatorExtractor, techMD, version)
              metadataService.addMetadata(metadataTech)
            }
          }
        }
        case None => Logger.error(s"[MongoDBUpdate : Missing file id")
      }
    }
  }

  private def collectionRequiresAuthor() {
    val q = "author" $exists false
    val o = MongoDBObject("$set" -> MongoDBObject("author" -> SocialUserDAO.dao.toDBObject(User.anonymous)))
    collection("collections").update(q, o, multi = true)
  }

  /**
   * Adds a creation and expiration date to old invites. Considering them as just created when the update script is run.
   */
  private def updateSpaceInvites() {
    collection("spaces.invites").foreach { invite =>

      val TokenDurationKey = securesocial.controllers.Registration.TokenDurationKey
      val DefaultDuration = securesocial.controllers.Registration.DefaultDuration
      val TokenDuration = Play.current.configuration.getInt(TokenDurationKey).getOrElse(DefaultDuration)
      invite.put("creationTime", new Date())
      val ONE_MINUTE_IN_MILLIS = 60000
      val date: Calendar = Calendar.getInstance()
      val t = date.getTimeInMillis
      val afterAddingMins: Date = new Date(t + (TokenDuration * ONE_MINUTE_IN_MILLIS))
      invite.put("expirationTime", afterAddingMins)
      try {
        collection("spaces.invites").save(invite, WriteConcern.Safe)
      } catch {
        case e: BSONException => Logger.error("Unable to update invite:" + invite.getAsOrElse[ObjectId]("_id", new ObjectId()).toString)
      }
    }
  }

  private def addLengthSha512PathFile() {
    val dbss = new DiskByteStorageService()
    lazy val rootPath = Play.current.configuration.getString("clowder.diskStorage.path").getOrElse("")
    for (prefix <- List[String]("uploads", "previews", "textures", "geometries", "thumbnails", "tiles")) {
      val files = gridFS(prefix)
      collection(prefix + ".files").foreach { file =>
        val relpath = file.getOrElse("path", "").toString
        val id = file.getAsOrElse[ObjectId]("_id", new ObjectId())
        var deletepath = false
        if (relpath.contains("/")) {
          val fullpath = if (relpath.startsWith("/"))
            relpath
          else
            dbss.makePath(rootPath, prefix, relpath)
          file.put("path", fullpath)
          file.put("loader", classOf[DiskByteStorageService].getName)
          dbss.load(fullpath, prefix) match {
            case Some(is) => {
              val cis = new CountingInputStream(is)
              val sha512 = DigestUtils.sha512Hex(cis)
              cis.close()
              file.put("sha512", sha512)
              file.put("length", cis.getByteCount)
            }
            case None => {
              file.put("sha512", "")
              file.put("length", -1)
            }
          }
        } else {
          if (file.containsField("path"))
            deletepath = true
          file.put("loader", classOf[MongoDBByteStorage].getName)
          files.findOne(id) match {
            case Some(f) => {
              try {
                val cis = new CountingInputStream(f.inputStream)
                val sha512 = DigestUtils.sha512Hex(cis)
                cis.close()
                file.put("sha512", sha512)
                file.put("length", cis.getByteCount)
              } catch {
                case _: Throwable => {
                  file.put("sha512", "")
                  file.put("length", -1)
                }
              }
            }
            case None => {
              file.put("sha512", "")
              file.put("length", -1)
            }
          }
        }
        try {
          collection(prefix + ".files").save(file, WriteConcern.Safe)
          if (deletepath)
            collection(prefix + ".files").update(MongoDBObject("_id" -> id), $unset("path"))
        } catch {
          case e: Exception => Logger.error("Unable to update file :" + id.toString, e)
        }
      }
    }

    // no need to fixSha512
    appConfig.addPropertyValue("mongodb.updates", "fixing-mongo-sha512")
  }

  private def fixSha512() {
    for (prefix <- List[String]("uploads", "previews", "textures", "geometries", "thumbnails", "tiles")) {
      val files = gridFS(prefix)
      collection(prefix + ".files").find(MongoDBObject("loader" -> classOf[MongoDBByteStorage].getName)).snapshot().foreach { file =>
        val id = file.getAsOrElse[ObjectId]("_id", new ObjectId())
        files.findOne(id) match {
          case Some(f) => {
            try {
              val cis = new CountingInputStream(f.inputStream)
              val sha512 = DigestUtils.sha512Hex(cis)
              cis.close()
              file.put("sha512", sha512)
              file.put("length", cis.getByteCount)
            } catch {
              case _: Throwable => {
                file.put("sha512", "")
                file.put("length", -1)
              }
            }
          }
          case None => {
            file.put("sha512", "")
            file.put("length", -1)
          }
        }
        try {
          collection(prefix + ".files").save(file, WriteConcern.Safe)
        } catch {
          case e: Exception => Logger.error("Unable to update file :" + id.toString, e)
        }
      }
    }
  }

  private def updateUserPreference() {
    collection("social.users").update(MongoDBObject(), $unset("repositoryPreferences.access"), multi = true)
    collection("social.users").update(MongoDBObject(), $unset("repositoryPreferences.organizational_affiliation"), multi = true)
    collection("social.users").update(MongoDBObject(), $unset("repositoryPreferences.cost"), multi = true)
    collection("social.users").update(MongoDBObject(), $unset("repositoryPreferences.license"), multi = true)
    collection("social.users").update(MongoDBObject(), $unset("repositoryPreferences"), multi = true)
  }

  private def activateAllUsers() {
    val query = MongoDBObject("active" -> MongoDBObject("$exists" -> false))
    val update = MongoDBObject("$set" -> MongoDBObject("active" -> true))
    collection("social.users").update(query, update, upsert = false, multi = true)
  }

  private def migrateNotesInFiles() {
    collection("uploads.files").foreach { file =>
      val note = file.getAsOrElse[String]("notesHTML", "")
      if (note != "") {
        file.put("description", note)
        file.remove("notesHTML")
        try {
          collection("uploads.files").save(file, WriteConcern.Safe)
        } catch {
          case e: BSONException => Logger.error("Unable to migrate note to description in file:" + file.getAsOrElse[ObjectId]("_id", new ObjectId()).toString)
        }
      }
    }
  }

  private def migrateNotesInDatasets() {
    collection("datasets").foreach { ds =>
      val note = ds.getAsOrElse[String]("notesHTML", "")
      if (note != "") {
        val description = ds.getAsOrElse[String]("description", "")
        if (description != "") {
          ds.put("description", description + " " + note)
        } else {
          ds.put("description", note)
        }
        ds.remove("notesHTML")
        try {
          collection("datasets").save(ds, WriteConcern.Safe)
        } catch {
          case e: BSONException => Logger.error("Unable to migrate note to description in dataset:" + ds.getAsOrElse[ObjectId]("_id", new ObjectId()).toString)
        }
      }

    }
  }

  private def addAuthorAndDateToFolders() {
    collection("folders").foreach { folder =>
      val datasetId = folder.getAsOrElse[ObjectId]("parentDatasetId", new ObjectId())
      if (datasetId != "") {
        collection("datasets").findOne(MongoDBObject("_id" -> datasetId)).foreach { dataset =>
          val author = dataset.getAsOrElse[BasicDBObject]("author", new BasicDBObject())
          val id = author.getAsOrElse[ObjectId]("_id", new ObjectId())
          val fullName = author.getAsOrElse[String]("fullName", "")
          val avatarUrl = author.getAsOrElse[String]("avatarUrl", "")
          val email = author.getAsOrElse[String]("email", "")
          val miniUser = Map("_id" -> id, "fullName" -> fullName, "avatarURL" -> avatarUrl, "email" -> email)
          val createdAt = new Date()
          folder.put("author", miniUser)
          folder.put("created", createdAt)
          try {
            collection("folders").save(folder, WriteConcern.Safe)
          } catch {
            case e: BSONException => Logger.error("Unable to add author and created to Folder: " + folder.getAsOrElse[ObjectId]("_id", new ObjectId()).toString)
          }
        }
      }
    }
  }

  private def addAuthorAndDateToCurationFolders() {
    collection("curationFolders").foreach { cFolder =>
      val coId = cFolder.getAsOrElse[ObjectId]("parentCurationObjectId", new ObjectId())
      if (coId != "") {
        collection("curationObjects").findOne(MongoDBObject("_id" -> coId)).foreach { curationObject =>
          val author = curationObject.getAsOrElse[BasicDBObject]("author", new BasicDBObject())
          val id = author.getAsOrElse[ObjectId]("_id", new ObjectId())
          val fullName = author.getAsOrElse[String]("fullName", "")
          val avatarUrl = author.getAsOrElse[String]("avatarUrl", "")
          val email = author.getAsOrElse[String]("email", "")
          val miniUser = Map("_id" -> id, "fullName" -> fullName, "avatarURL" -> avatarUrl, "email" -> email)
          val createdAt = new Date()
          cFolder.put("author", miniUser)
          cFolder.put("created", createdAt)
          try {
            collection("curationFolders").save(cFolder, WriteConcern.Safe)
          } catch {
            case e: BSONException => Logger.error("Unable to add author and created to curation Folder: " + cFolder.getAsOrElse[ObjectId]("_id", new ObjectId()).toString)
          }
        }
      }

    }
  }

  private def splitGridFSMetadata() {
    val ignoreCopyKeys = List[String]("_id", "path", "metadata", "chunkSize", "aliases", "md5")
    val ignoreRemoveKeys = List[String]("_id", "filename", "contentType", "length", "chunkSize", "uploadDate", "aliases", "md5")

    // fix logos
    collection("logos").find().snapshot().foreach { x =>
      val id = MongoDBObject("_id" -> new ObjectId(x.get("file_id").toString))
      if (x.getAsOrElse[String]("loader", "") == classOf[MongoDBByteStorage].getName) {
        x.put("loader_id", x.get("file_id").toString)
        collection("logos.files").find(id).foreach { y =>
          y.keySet().asScala.toList.foreach { k =>
            if (!ignoreRemoveKeys.contains(k)) {
              y.remove(k)
            }
          }
          try {
            collection("logos.files").save(y, WriteConcern.Safe)
          } catch {
            case e: BSONException => Logger.error("Unable to save logos.files: " + y.getAsOrElse[ObjectId]("_id", new ObjectId()).toString)
          }
        }
      } else {
        collection("logos.files").find(id).foreach { y =>
          x.put("loader_id", y.getAsOrElse[String]("path", ""))
        }
        try {
          collection("logos.files").remove(id)
        } catch {
          case e: BSONException => Logger.error("Unable to remove logos.files: " + x.getAsOrElse[ObjectId]("_id", new ObjectId()).toString)
        }
      }
      x.remove("file_id")
      try {
        collection("logos").save(x, WriteConcern.Safe)
      } catch {
        case e: BSONException => Logger.error("Unable to write cleaned up logo: " + x.getAsOrElse[ObjectId]("_id", new ObjectId()).toString)
      }
    }

    for (prefix <- List[String]("uploads", "previews", "textures", "geometries", "thumbnails", "tiles")) {
      val oldCollection = collection(prefix + ".files")
      val newCollection = collection(prefix)
      oldCollection.find().snapshot().foreach { x =>
        val id = x.get("_id")

        val c = new MongoDBObject()
        val r = new MongoDBObject()
        c.put("_id", id)

        if (x.getAsOrElse[String]("loader", "") == classOf[MongoDBByteStorage].getName) {
          c.put("loader_id", id.toString)
        } else {
          c.put("loader_id", x.get("path"))
        }

        x.keySet.asScala.toList.foreach { k =>
          if (!ignoreCopyKeys.contains(k)) {
            c.put(k, x.get(k))
          }
          if (!ignoreRemoveKeys.contains(k)) {
            r.put(k, "")
          }
        }

        try {
          newCollection.insert(c, WriteConcern.Safe)
        } catch {
          case e: BSONException => Logger.error(s"Unable to write new ${prefix} : " + x.getAsOrElse[ObjectId]("_id", new ObjectId()).toString)
        }
        if (x.getAsOrElse[String]("loader", "") == classOf[MongoDBByteStorage].getName) {
          try {
            oldCollection.update(MongoDBObject("_id" -> id), MongoDBObject("$unset" -> r))
          } catch {
            case e: BSONException => Logger.error(s"Unable to write cleaned up ${prefix}.files : " + x.getAsOrElse[ObjectId]("_id", new ObjectId()).toString)
          }
        } else {
          Logger.debug("Removing " + x.getAsOrElse[ObjectId]("_id", new ObjectId()).toString)
          try {
            oldCollection.remove(MongoDBObject("_id" -> id))
          } catch {
            case e: BSONException => Logger.error(s"Unable to remove ${prefix}.files : " + x.getAsOrElse[ObjectId]("_id", new ObjectId()).toString)
          }
        }
      }
    }
  }

  private def addAdminFieldToUser() {
    val admins = collection("app.configuration").findOne(MongoDBObject("key" -> "admins")) match {
      case Some(x) => {
        x.get("value") match {
          case l: BasicDBList => l.toList.asInstanceOf[List[String]]
          case y => List[String](y.asInstanceOf[String])
        }
      }
      case None => List.empty[String]
    }

    val users = collection("social.users")
    admins.foreach { email =>
      users.find(MongoDBObject("email" -> email)).foreach { user =>
        user.put("admin", true)
        try {
          users.save(user, WriteConcern.Safe)
        } catch {
          case e: BSONException => Logger.error("Unable to mark user as admin: " + user._id.toString)
        }
      }
    }

    collection("app.configuration").remove(MongoDBObject("key" -> "admins"))
  }

  private def useMiniUser(): Unit = {
    if (!appConfig.hasPropertyValue("mongodb.updates", "split-gridfs")) {
      throw new Exception("Missing split-gridfs migration.")
    }

    for (coll <- List[String]("collections", "uploads", "datasets", "curationObjects", "curationFiles", "comments", "comments")) {
      collection(coll).foreach { c =>
        val author = c.getAsOrElse[BasicDBObject]("author", new BasicDBObject())
        val id = author.getAsOrElse[ObjectId]("_id", new ObjectId())
        val fullName = author.getAsOrElse[String]("fullName", "")
        val avatarUrl = author.getAsOrElse[String]("avatarUrl", "")
        val email = author.getAsOrElse[String]("email", "")
        val miniUser = Map("_id" -> id, "fullName" -> fullName, "avatarURL" -> avatarUrl, "email" -> email)
        c.remove("author")
        c.put("author", miniUser)
        try {
          collection(coll).save(c, WriteConcern.Safe)
        } catch {
          case e: BSONException => Logger.error(s"Unable to update the user in ${coll} from Identity to MiniUser with id: ${id.toString}")
        }
      }
    }

    collection("curationObjects").foreach { co =>
      val datasets: MongoDBList = co.getAsOrElse[MongoDBList]("datasets", new MongoDBList())
      datasets.foreach {
        case dataset: DBObject => {
          val author = dataset.getAsOrElse[BasicDBObject]("author", new BasicDBObject())
          val id = author.getAsOrElse[ObjectId]("_id", new ObjectId())
          val fullName = author.getAsOrElse[String]("fullName", "")
          val avatarUrl = author.getAsOrElse[String]("avatarUrl", "")
          val email = author.getAsOrElse[String]("email", "")
          val miniUser = Map("_id" -> id, "fullName" -> fullName, "avatarURL" -> avatarUrl, "email" -> email)
          dataset.remove("author")
          dataset.put("author", miniUser)
        }
        case None => Logger.error("Can not parse the datasets within the curation Object with id: " + co.getAsOrElse[ObjectId]("_id", new ObjectId()).toString)
      }
      co.put("datasets", datasets)
      try {
        collection("curationObjects").save(co, WriteConcern.Safe)
      } catch {
        case e: BSONException => Logger.error("Unable to update the user in dataset within curation object with Id: " + co.getAsOrElse[ObjectId]("_id", new ObjectId()).toString)
      }
    }
  }

  private def addRootMapToCollections() {
    collection("collections").foreach { c =>
      val parents = c.getAsOrElse[MongoDBList]("parent_collection_ids", MongoDBList.empty)
      val spaces = c.getAsOrElse[MongoDBList]("spaces", MongoDBList.empty)
      val parentCollections = collection("collections").find(MongoDBObject("_id" -> MongoDBObject("$in" -> parents)))
      var parentSpaces = MongoDBList.empty
      parentCollections.foreach { pc =>
        pc.getAsOrElse[MongoDBList]("spaces", MongoDBList.empty).foreach { ps => parentSpaces += ps }
      }
      val root_spaces = scala.collection.mutable.ListBuffer.empty[ObjectId]
      spaces.foreach { s =>

        if (!(parentSpaces contains s)) {
          root_spaces += new ObjectId(s.toString())
        }
      }

      c.put("root_spaces", root_spaces.toList)
      c.remove("root_flag")
      try {
        collection("collections").save(c, WriteConcern.Safe)
      } catch {
        case e: BSONException => Logger.error("Unable to set root flag for collection with id: " + c.getAsOrElse[ObjectId]("_id", new ObjectId()).toString)
      }

    }
  }

  private def fixCollectionCounterInSpaces() {
    collection("spaces.projects").foreach { space =>
      val spaceId = space.getAsOrElse[ObjectId]("_id", new ObjectId())
      val collections = collection("collections").find(MongoDBObject("root_spaces" -> spaceId))
      space.put("collectionCount", collections.length)
      try {
        collection("spaces.projects").save(space, WriteConcern.Safe)
      } catch {
        case e: BSONException => Logger.error("Unable to update the collection count for space with id: " + spaceId.toString)
      }

    }
  }

  private def renameAdminServerAdmin() {
    val q = MongoDBObject()
    val o = MongoDBObject("$rename" -> MongoDBObject("admin" -> "serverAdmin"))
    collection("social.users").update(q, o, multi = true, concern = WriteConcern.Safe)
  }

  private def updateEventObjectName(): Unit = {
    for (coll <- List[String]("collections", "spaces.projects", "datasets", "uploads.files", "curationObjects")) {
      collection(coll).foreach { ds =>
        (ds.getAs[ObjectId]("_id"), ds.getAs[String]("name")) match {
          case (Some(id), Some(name)) => {
            collection("events").update(MongoDBObject("object_id" -> new ObjectId(id.toString)), $set("object_name" -> name), multi = true)
            collection("events").update(MongoDBObject("source_id" -> new ObjectId(id.toString)), $set("source_name" -> name), multi = true)
          }
          case _ => {}
        }
      }
    }
  }

  private def removeDeletedSpacesFromUser() {
    collection("social.users").foreach { user =>
      val roles = user.getAsOrElse[MongoDBList]("spaceandrole", MongoDBList.empty)
      val newRoles = MongoDBList.empty
      roles.foreach { role =>
        val resp = collection("spaces.projects").find(MongoDBObject("_id" -> role.asInstanceOf[BasicDBObject].get("spaceId")))
        if (resp.size > 0) {
          newRoles += role
        }
      }
      user.put("spaceandrole", newRoles)
      try {
        collection("social.users").save(user, WriteConcern.Safe)
      } catch {
        case e: BSONException => Logger.error("Unable to update spaces for user with id:" + user.getAsOrElse("_id", new ObjectId()).toString())
      }
    }
  }

  private def updateCountsInSpaces() {

    collection("spaces.projects").foreach { space =>
      val spaceId = space.getAsOrElse("_id", new ObjectId()).toString()
      val collections = collection("collections").find(MongoDBObject("root_spaces" -> MongoDBObject("$in" -> MongoDBList(new ObjectId(spaceId)))))
      val datasets = collection("datasets").find(MongoDBObject("spaces" -> MongoDBObject("$in" -> MongoDBList(new ObjectId(spaceId)))))
      space.put("datasetCount", datasets.length)
      space.put("collectionCount", collections.length)
      try {
        collection("spaces.projects").save(space, WriteConcern.Safe)
      } catch {
        case e: BSONException => Logger.error("Unable to update the counts for space with id: " + spaceId)
      }
    }
  }

  private def fixMetadataCount(): Unit = {
    // set everbody metadata to 0
    for (coll <- List[String]("collections", "curationObjects", "datasets", "uploads", "previews", "sections")) {
      collection(coll).update(MongoDBObject(), $set("metadataCount" -> 0))
    }

    // aggregate all metadata and update all records
    var lastId: ObjectId = null
    var lastCollection: String = null
    var count = 0
    collection("metadata").find().sort(MongoDBObject("attachedTo" -> 1)).foreach{d =>
      d.getAs[DBObject]("attachedTo").foreach{at =>
        (at.getAs[ObjectId]("_id"), at.getAs[String]("resourceType")) match {
          case (Some(id), Some(coll)) => {
            if (id != lastId) {
              if (lastId != null) {
                try {
                  collection(lastCollection).update(MongoDBObject("_id" -> lastId), $set("metadataCount" -> count))
                } catch {
                  case e: BSONException => Logger.error(s"Unable to update the metadata counts for ${lastCollection} with id ${id} to ${count}")
                }
              }
              lastId = id
              lastCollection = coll
              count = 1
            } else {
              count += 1
            }
          }
          case (id, coll) => {
            Logger.error(s"Unable to update the metadata counts for ${coll} with id ${id}")
          }
        }
      }
    }
  }

  private def switchToTermsOfServices(): Unit = {
    val ua = collection("app.configuration").findOne(MongoDBObject("key" -> "userAgreement.message"))
    if (ua.isDefined && ua.get("value").toString != "") {
      collection("app.configuration").insert(MongoDBObject("key" -> "tos.date") ++ MongoDBObject("value" -> new Date()))
    }
    collection("app.configuration").update(MongoDBObject("key" -> "userAgreement.message"), $set(("key", "tos.text")))
  }

  private def addFileStatus(): Unit = {
    collection("uploads").update(MongoDBObject(), $set("status" -> FileStatus.PROCESSED.toString), multi = true)
  }

  private def addMetadataPerSpace() {
    val metadataService: MetadataService = DI.injector.getInstance(classOf[MetadataService])

    collection("spaces.projects").foreach { space =>
      val metadatas = collection("metadata.definitions").find(MongoDBObject("spaceId" -> null))
      val spaceId = space.getAsOrElse("_id", new ObjectId())
      metadatas.foreach { metadata =>

        val json = metadata.getAsOrElse("json", new BasicDBObject())
        val md = new BasicDBObject()
        md.put("_id", new ObjectId())
        md.put("spaceId", spaceId)
        md.put("json", json)
        try {
          collection("metadata.definitions").insert(md, WriteConcern.Safe)
        } catch {
          case e: BSONException => Logger.error("Unable to add the metadata definition for space with id: " + spaceId)
        }
      }
    }
  }

  private def addTrialFlag(): Unit = {
    val q = MongoDBObject()
    val s = MongoDBObject("$set" -> MongoDBObject("status" -> SpaceStatus.TRIAL.toString))
    val d = MongoDBObject("$set" -> MongoDBObject("status" -> DatasetStatus.PRIVATE.toString))
    collection("datasets").update(q, d, multi = true)
    collection("spaces.projects").update(q, s, multi = true)
  }

  private def addTrialFlag2(): Unit = {
    val q = MongoDBObject()

    val (s, d) = if (play.Play.application().configuration().getBoolean("verifySpaces")) {
      (MongoDBObject("$set" -> MongoDBObject("status" -> SpaceStatus.TRIAL.toString)),
        MongoDBObject("$set" -> MongoDBObject("status" -> DatasetStatus.TRIAL.toString)))
    } else {
      (MongoDBObject("$set" -> MongoDBObject("status" -> SpaceStatus.PRIVATE.toString)),
        MongoDBObject("$set" -> MongoDBObject("status" -> DatasetStatus.DEFAULT.toString)))
    }
    collection("datasets").update(q, d, multi = true)
    collection("spaces.projects").update(q, s, multi = true)
  }

  private def updateMongoEmailCase(): Unit = {
    val userpasses = collection("social.users").find(MongoDBObject("identityId.providerId" -> "userpass"))
    userpasses.foreach { user =>
      (user.getAs[ObjectId]("_id"), user.getAs[String]("email"),
        user.getAsOrElse[DBObject]("identityId", new MongoDBObject()).getAs[String]("userId")) match {
          case (Some(userId), Some(email), Some(username)) => {
            try {
              // Find if user exists with lowercase email already
              val conflicts = collection("social.users").count(MongoDBObject(
                "_id" -> MongoDBObject("$ne" -> userId),
                "identityId" -> MongoDBObject("userId" -> username.toLowerCase, "providerId" -> "userpass")))

              if (conflicts == 0) {
                collection("social.users").update(MongoDBObject("_id" -> userId),
                  MongoDBObject("$set" -> MongoDBObject(
                    "email" -> email.toLowerCase,
                    "identityId" -> MongoDBObject("userId" -> username.toLowerCase, "providerId" -> "userpass"))), upsert = false, multi = true)
              } else {
                // If there's already an account with lowercase email, deactivate this account
                collection("social.users").update(MongoDBObject("_id" -> userId),
                  MongoDBObject("$set" -> MongoDBObject("active" -> false)), upsert = false, multi = true)
              }
            } catch {
              case e: BSONException => Logger.error("Unable to update email for user with id: " + user)
            }
          }
          case _ => Logger.error("Missing user fields when updating email case")
        }
    }
  }

  private def copySha512ToMetadataAndRemove(): Unit = {
    for (colln <- List[String]("uploads")) {
      // Iteracte across all files that have a sha512 entry
      collection(colln).find(MongoDBObject(
        "sha512" -> MongoDBObject("$exists" -> true))).snapshot().foreach { file =>
        val id = file.getAsOrElse[ObjectId]("_id", new ObjectId())
        file.getAs[String]("sha512") match {
          case Some(sha) => {
            // Check for extractor metadata for a newer SHA512
            val mdQuery = MongoDBObject("attachedTo._id" -> file._id, "creator.extractorId" -> ".*ncsa.file.digest".r)
            val attachedMdCount = collection("metadata").count(mdQuery)

            if (attachedMdCount > 0) {
              // There is file digest metadata for this file, so check it
              val mdResponse = collection("metadata").find(mdQuery).snapshot()
              mdResponse.foreach { md =>
                md.getAs[DBObject]("content") match {
                  case Some(content) => {
                    if (sha != content.get("sha512").toString)
                      Logger.error("Old SHA512 does not match metadata digest for file " + id.toString)
                  }
                  case None => Logger.error("no extractorId found in md for file " + id.toString)
                }
              }
            } else {
              // No file digest metadata for this file, so we will use current SHA512 to imitate extractor
              val mdObj = Metadata(
                UUID.generate,
                ResourceRef(ResourceRef.file, UUID(id.toString)),
                None, //contextId
                None, //contentURL,
                new Date(),
                new ExtractorAgent(
                  UUID.generate,
                  "extractor",
                  Some("ncsa.file.digest"),
                  Some(new URL("http://clowder.ncsa.illinois.edu/clowder/api/extractors/ncsa.file.digest"))),
                Json.parse("{\"sha512\": \"" + sha + "\"}"),
                None)
              val dbmd = com.novus.salat.grater[Metadata].asDBObject(mdObj)
              collection("metadata").insert(dbmd, WriteConcern.Safe)

              try {
                val mdCount = file.getOrElse("metadataCount", "0").toString.toLong
                file.put("metadataCount", mdCount + 1)
              } catch {
                case e: Exception => {
                  // If we can't get metadataCount from file correctly, just set to 1 for newly added md
                  Logger.error("Unable to update metadataCount; setting to 1", e)
                  file.put("metadataCount", 1L)
                }
              }
            }

            // Overwrite if not, give error flag if so and they dont match
            file.remove("sha512")
          }
          case None => Logger.error("SHA512 is None for file: " + id.toString)
        }

        try {
          collection(colln).save(file, WriteConcern.Safe)
        } catch {
          case e: Exception => Logger.error("Unable to update file :" + id.toString, e)
        }
      }
    }
  }

  /**
   * In order to support adding multiple repositories for an extractor in extractors.info collection, changing the
   * existing repository type from Repository to List[Repository] in those records that have not been updated yet.
   */
  private def updateRepositoryType(): Unit = {
    val extractorsInfoCollection = collection("extractors.info")

    extractorsInfoCollection.foreach { extractor =>

      val repository = extractor.get("repository")

      if (!repository.isInstanceOf[BasicDBList]) {
        extractor.put("repository", MongoDBList(repository))

        try {
          extractorsInfoCollection.save(extractor, WriteConcern.Safe)
        } catch {
          case e: BSONException => Logger.error("Failed to update collection extractors.info entry with id " + extractor.getAsOrElse[ObjectId]("_id", new ObjectId()).toString)
        }
      }
    }
  }

  private def updateInCurationStatus(): Unit = {
    CurationDAO.update(MongoDBObject("status" -> "In Curation"),
      $set("status" -> "In Preparation"), false, true, WriteConcern.Safe)
  }

  private def updateToUserStatus() {
    collection("social.users").foreach { su =>
      val active = su.getAs[Boolean]("active")
      val admin = su.getAs[Boolean]("serverAdmin")
      if (!active.getOrElse(false)) {
        su.put("status", UserStatus.Inactive.toString)
      } else {
        if (admin.getOrElse(false)) {
          su.put("status", UserStatus.Admin.toString)
        } else {
          su.put("status", UserStatus.Active.toString)
        }
      }

      su.removeField("active")
      su.removeField("serverAdmin")
      collection("social.users").save(su, WriteConcern.Safe)
    }
  }

  private def updateAvatarUrl() {
    val q =  MongoDBObject("avatarUrl" -> "^http://www.gravatar.com".r)
    collection("social.users").find(q).foreach { user =>
      val avatar_url = user.getAsOrElse[String]("avatarUrl", "")
      if(avatar_url.indexOf("http://www.gravatar.com") == 0 ) {
        val index = avatar_url.lastIndexOf("/")
        val new_gravatar = "https://www.gravatar.com/avatar" + avatar_url.substring(index)
        user.put("avatarUrl", new_gravatar)
      }
      collection("social.users").save(user, WriteConcern.Safe)
    }
    collection("events").find(MongoDBObject("user.avatarURL" -> "^http://www.gravatar.com".r)).foreach{ event =>
      event.getAs[DBObject]("user") match {
        case Some(mini_user) => {
          val avatar_url = mini_user.getAsOrElse("avatarURL", "")
          if(avatar_url.indexOf("http://www.gravatar.com") == 0 ) {
            val index = avatar_url.lastIndexOf("/")
            val new_gravatar = "https://www.gravatar.com/avatar" + avatar_url.substring(index)
            mini_user.put("avatarURL", new_gravatar)
          }
        }
        case _ => Logger.info("No miniuser associated with the event")
      }
      event.getAs[DBObject]("targetuser") match {
        case Some(mini_user) => {
          val avatar_url = mini_user.getAsOrElse("avatarURL", "")
          if(avatar_url.indexOf("http://www.gravatar.com") == 0 ) {
            val index = avatar_url.lastIndexOf("/")
            val new_gravatar = "https://www.gravatar.com/avatar" + avatar_url.substring(index)
            mini_user.put("avatarURL", new_gravatar)
          }
        }
        case _ => Logger.info("No targetuser associated with the event")
      }
      collection("events").save(event, WriteConcern.Safe)
    }
    collection("collections").find(MongoDBObject("author.avatarURL" -> "^http://www.gravatar.com".r)).foreach{ c =>
      c.getAs[DBObject]("author") match {
        case Some(mini_user) => {
          val avatar_url = mini_user.getAsOrElse("avatarURL", "")
          if(avatar_url.indexOf("http://www.gravatar.com") == 0 ) {
            val index = avatar_url.lastIndexOf("/")
            val new_gravatar = "https://www.gravatar.com/avatar" + avatar_url.substring(index)
            mini_user.put("avatarURL", new_gravatar)
          }
          collection("collections").save(c, WriteConcern.Safe)
        }
        case _ => Logger.info("No miniuser associated with the collection ")
      }
    }
    collection("datasets").find(MongoDBObject("author.avatarURL" -> "^http://www.gravatar.com".r)).foreach{ dataset =>
      dataset.getAs[DBObject]("author") match {
        case Some(mini_user) => {
          val avatar_url = mini_user.getAsOrElse("avatarURL", "")
          if(avatar_url.indexOf("http://www.gravatar.com") == 0 ) {
            val index = avatar_url.lastIndexOf("/")
            val new_gravatar = "https://www.gravatar.com/avatar" + avatar_url.substring(index)
            mini_user.put("avatarURL", new_gravatar)

          }
          collection("datasets").save(dataset, WriteConcern.Safe)
        }
        case _ => Logger.info("No miniuser associated with the dataset ")
      }
    }
    collection("folders").find(MongoDBObject("author.avatarURL" -> "^http://www.gravatar.com".r))foreach{ folder =>
      folder.getAs[DBObject]("author") match {
        case Some(mini_user) => {
          val avatar_url = mini_user.getAsOrElse("avatarURL", "")
          if(avatar_url.indexOf("http://www.gravatar.com") == 0 ) {
            val index = avatar_url.lastIndexOf("/")
            val new_gravatar = "https://www.gravatar.com/avatar" + avatar_url.substring(index)
            mini_user.put("avatarURL", new_gravatar)

          }
          collection("folders").save(folder, WriteConcern.Safe)
        }
        case _ => Logger.info("No miniuser associated with the folder")
      }
    }
    collection("uploads").find(MongoDBObject("author.avatarURL" -> "^http://www.gravatar.com".r))foreach{ file =>
      file.getAs[DBObject]("author") match {
        case Some(mini_user) => {
          val avatar_url = mini_user.getAsOrElse("avatarURL", "")
          if(avatar_url.indexOf("http://www.gravatar.com") == 0 ) {
            val index = avatar_url.lastIndexOf("/")
            val new_gravatar = "https://www.gravatar.com/avatar" + avatar_url.substring(index)
            mini_user.put("avatarURL", new_gravatar)

          }
          collection("uploads").save(file, WriteConcern.Safe)
        }
        case _ => Logger.info("No miniuser associated with the file")
      }
    }
    collection("comments").find(MongoDBObject("author.avatarURL" -> "^http://www.gravatar.com".r))foreach{ comment =>
      comment.getAs[DBObject]("author") match {
        case Some(mini_user) => {
          val avatar_url = mini_user.getAsOrElse("avatarURL", "")
          if(avatar_url.indexOf("http://www.gravatar.com") == 0 ) {
            val index = avatar_url.lastIndexOf("/")
            val new_gravatar = "https://www.gravatar.com/avatar" + avatar_url.substring(index)
            mini_user.put("avatarURL", new_gravatar)

          }
          collection("comments").save(comment, WriteConcern.Safe)
        }
        case _ => Logger.info("No miniuser associated with the comment ")
      }
    }
    collection("curationObjects").find(MongoDBObject("author.avatarURL" -> "^http://www.gravatar.com".r))foreach{ event =>
      event.getAs[DBObject]("author") match {
        case Some(mini_user) => {
          val avatar_url = mini_user.getAsOrElse("avatarURL", "")
          if(avatar_url.indexOf("http://www.gravatar.com") == 0 ) {
            val index = avatar_url.lastIndexOf("/")
            val new_gravatar = "https://www.gravatar.com/avatar" + avatar_url.substring(index)
            mini_user.put("avatarURL", new_gravatar)

          }
          collection("curationObjects").save(event, WriteConcern.Safe)
        }
        case _ => Logger.info("No miniuser associated with the curation Object ")
      }
    }
    collection("metadata").find(MongoDBObject("creator.typeOfAgent" -> "cat:user", "creator.user.avatarURL" -> "^http://www.gravatar.com".r) )foreach{ metadata =>
      metadata.getAs[DBObject]("creator") match {
        case Some(creator) => {
          val typeOfAgent = creator.getAsOrElse("typeOfAgent", "")
          if(typeOfAgent == "cat:user") {
            creator.getAs[DBObject]("user") match {
              case Some(mini_user) => {
                val avatar_url = mini_user.getAsOrElse("avatarURL", "")
                if(avatar_url.indexOf("http://www.gravatar.com") == 0 ) {
                  val index = avatar_url.lastIndexOf("/")
                  val new_gravatar = "https://www.gravatar.com/avatar" + avatar_url.substring(index)
                  mini_user.put("avatarURL", new_gravatar)

                }
                collection("metadata").save(metadata, WriteConcern.Safe)
            }
              case _ => Logger.info("No miniuser associated with the curation Object ")
          }
        }
        }
        case _ => Logger.info("No agent associated with the curation Object ")
      }
    }


  }

  private def updateOriginalFilename(): Unit = {

    val metadataService: MetadataService = DI.injector.getInstance(classOf[MetadataService])
    val mdQuery = MongoDBObject("attachedTo.resourceType" -> "file", "creator.typeOfAgent" -> "cat:user")
    val attachedMdCount = collection("metadata").count(mdQuery)

    if (attachedMdCount > 0) {
      // There is file digest metadata for this file, so check it
      collection("metadata").find(mdQuery).foreach { md =>
        md.getAs[DBObject]("content") match {
          case Some(content) => {
            val path = content.getAsOrElse[String]("Upload Path", "")
            if (path.length > 0) {
              if (path.lastIndexOf("/") >= 0) {
                Logger.info("Assigning name/: " + path.substring(path.lastIndexOf("/")+1) + " from path " + path)
                md.getAs[DBObject]("attachedTo") match {
                  case Some(ref) => {
                                  collection("uploads").update(MongoDBObject("_id" -> new ObjectId(ref.get("_id").toString())),
                  MongoDBObject("$set" -> MongoDBObject(
                    "originalname" -> path.substring(path.lastIndexOf("/")+1))), false, false, WriteConcern.Safe)
                  
                  }
                  case _ => Logger.info("Nope")
                }
              }

            }

          }
        }
      }
    }
  }

  private def removeKeyFromExtractorLogs(): Unit = {
    collection("extractions").foreach { extraction =>
      val status = extraction.getAs[String]("status").getOrElse("")
      val commKey = "key=" + play.Play.application().configuration().getString("commKey")
      val parsed_status = status.replace(commKey, "key=secretKey")
      extraction.put("status", parsed_status)
      collection("extractions").save(extraction, WriteConcern.Safe)
    }
  }


  private def updateAdminRole(): Unit = {
    val query = MongoDBObject("name" -> "Admin")
    val operation = MongoDBObject("$addToSet" -> MongoDBObject("permissions" -> Permission.ArchiveFile.toString))
    collection("roles").update(query, operation)
  }

  private def updateEditorRole(): Unit = {
    val query = MongoDBObject("name" -> "Editor")
    collection("roles").find(query).foreach {role =>
      role.put("permissions", Permission.EDITOR_PERMISSIONS.map(_.toString).toSet)
      collection("roles").save(role, WriteConcern.Safe)
    }
    collection("social.users").foreach{user =>
      val userSpaceRoles = user.getAsOrElse[MongoDBList]("spaceandrole", MongoDBList.empty)
      userSpaceRoles.foreach{ userSpaceRole =>
        val tempUserSpace = userSpaceRole.asInstanceOf[BasicDBObject]
        val tempRole = tempUserSpace.get("role").asInstanceOf[BasicDBObject]
        if(tempRole.get("name") == "Editor") {
          tempRole.put("permissions", Permission.EDITOR_PERMISSIONS.map(_.toString).toSet)
        }
      }
      user.put("spaceandrole", userSpaceRoles)
      collection("social.users").save(user, WriteConcern.Safe)
    }
  }

  private def updateSpaceExtractorsSelection(): Unit = {
    collection("spaces.extractors").foreach { space =>
      val enabled = space.getAsOrElse[MongoDBList]("extractors", MongoDBList.empty)

      collection("spaces.extractors").update(
        MongoDBObject("_id" -> space.get("_id")),
        MongoDBObject("$set" -> MongoDBObject("enabled" -> enabled)), upsert = false, multi = false)

      collection("spaces.extractors").update(
        MongoDBObject("_id" -> space.get("_id")),
        $unset("extractors"))
    }
    print("DONE")
  }
}
