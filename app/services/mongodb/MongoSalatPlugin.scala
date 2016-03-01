package services.mongodb

import java.net.URL
import java.util.{Calendar, Date}

import com.mongodb.CommandFailureException
import com.mongodb.casbah.Imports._
import com.mongodb.casbah.commons.MongoDBObject
import models._
import org.apache.commons.codec.digest.DigestUtils
import org.apache.commons.io.input.CountingInputStream
import org.bson.BSONException
import play.api.libs.json._
import play.api.{ Play, Plugin, Logger, Application }
import play.api.Play.current
import com.mongodb.casbah.MongoURI
import com.mongodb.casbah.MongoConnection
import com.mongodb.casbah.MongoDB
import com.mongodb.casbah.MongoCollection
import com.mongodb.casbah.gridfs.GridFS
import org.bson.types.ObjectId
import services.filesystem.DiskByteStorageService
import services.{MetadataService, DI, AppConfigurationService}

/**
 * Mongo Salat service.
 */
class MongoSalatPlugin(app: Application) extends Plugin {
  // URI to the mongodatabase, for example mongodb://127.0.0.1:27017/medici
  var mongoURI: MongoURI = null

  // hold the connection, if connection failed it will be tried to open next time
  var mongoConnection: MongoConnection = null

  override def onStart() {
    mongoURI = if (play.api.Play.configuration.getString("mongodbURI").isDefined) {
      MongoURI(play.api.Play.configuration.getString("mongodbURI").get)
    } else if (play.api.Play.configuration.getString("mongodb.default").isDefined) {
      Logger.info("mongodb.default is deprecated, please use mongodbURI")
      MongoURI(play.api.Play.configuration.getString("mongodb.default").get)
    } else {
      Logger.info("no connection to mongo specified in , will use default URI mongodb://127.0.0.1:27017/medici")
      MongoURI("mongodb://127.0.0.1:27017/medici")
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

    // create indices.
    Logger.debug("Ensuring indices exist")
    collection("spaces.projects").ensureIndex(MongoDBObject("created" -> -1))
    collection("spaces.projects").ensureIndex(MongoDBObject("public" -> 1))
    collection("spaces.projects").ensureIndex(MongoDBObject("creator" -> 1))

    collection("collections").ensureIndex(MongoDBObject("created" -> -1))
    collection("collections").ensureIndex(MongoDBObject("spaces" -> 1))
    collection("collections").ensureIndex(MongoDBObject("datasets._id" -> 1))
    collection("collections").ensureIndex(MongoDBObject("public" -> 1))
    collection("collections").ensureIndex(MongoDBObject("author.identityId.userId" -> 1, "author.identityId.providerId" -> 1))

    collection("datasets").ensureIndex(MongoDBObject("created" -> -1))
    collection("datasets").ensureIndex(MongoDBObject("tags" -> 1))
    collection("datasets").ensureIndex(MongoDBObject("files._id" -> 1))
    collection("datasets").ensureIndex(MongoDBObject("tags.name" -> 1))

    collection("datasets").ensureIndex(MongoDBObject("spaces" -> 1))
    collection("datasets").ensureIndex(MongoDBObject("public" -> 1))
    collection("datasets").ensureIndex(MongoDBObject("name" -> 1))
    collection("datasets").ensureIndex(MongoDBObject("author.identityId.userId" -> 1, "author.identityId.providerId" -> 1))

    collection("uploads.files").ensureIndex(MongoDBObject("uploadDate" -> -1))
    collection("uploads.files").ensureIndex(MongoDBObject("author.email" -> 1))
    collection("uploads.files").ensureIndex(MongoDBObject("tags.name" -> 1))

    collection("uploadquery.files").ensureIndex(MongoDBObject("uploadDate" -> -1))
    
    collection("previews.files").ensureIndex(MongoDBObject("uploadDate" -> -1, "file_id" -> 1))
    collection("previews.files").ensureIndex(MongoDBObject("uploadDate" -> -1, "section_id" -> 1))
    collection("previews.files").ensureIndex(MongoDBObject("section_id" -> -1))
    collection("previews.files").ensureIndex(MongoDBObject("file_id" -> -1))

    collection("textures.files").ensureIndex(MongoDBObject("file_id" -> 1))
    collection("tiles.files").ensureIndex(MongoDBObject("preview_id" -> 1, "filename" -> 1,"level" -> 1))
    
    collection("sections").ensureIndex(MongoDBObject("uploadDate" -> -1, "file_id" -> 1))
    collection("sections").ensureIndex(MongoDBObject("file_id" -> -1))
    collection("sections").ensureIndex(MongoDBObject("tags.name" -> 1))

    collection("metadata").ensureIndex(MongoDBObject("createdAt" -> -1))
    collection("metadata").ensureIndex(MongoDBObject("creator" -> 1))
    collection("metadata").ensureIndex(MongoDBObject("attachTo" -> 1))

    collection("contextld").ensureIndex(MongoDBObject("contextName" -> 1))

    collection("dtsrequests").ensureIndex(MongoDBObject("startTime" -> -1, "endTime" -> -1))
    collection("dtsrequests").ensureIndex(MongoDBObject("file_id" -> -1))
    collection("versus.descriptors").ensureIndex(MongoDBObject("fileId" -> 1))

    collection("multimedia.distances").ensureIndex(MongoDBObject("source_section"->1,"representation"->1,"distance"->1))
  }

  override def onStop() {
    if (mongoConnection != null)
      mongoConnection.close()
    mongoConnection = null
  }

  /**
   * Returns the database for the connection
   */
  def getDB: MongoDB = mongoConnection.getDB(mongoURI.database.getOrElse("medici"))

  /**
   * Returns a collection in the database
   */
  def collection(collection: String): MongoCollection = getDB(collection)

  /**
    * Based on the resourceRef return the mongo collection.
    */
  def collection(resourceRef: ResourceRef): Option[MongoCollection] = {
    resourceRef.resourceType match {
      case ResourceRef.space => Some(collection("spaces.projects"))
      case ResourceRef.dataset => Some(collection("datasets"))
      case ResourceRef.file => Some(collection("uploads.files"))
      //case ResourceRef.relation => Some(collection("hello"))
      case ResourceRef.preview => Some(collection("previews.files"))
      case ResourceRef.thumbnail => Some(collection("thumbnails.files"))
      case ResourceRef.collection => Some(collection("collections"))
      case ResourceRef.user => Some(collection("social.users"))
      case ResourceRef.comment => Some(collection("comments"))
      case ResourceRef.section => Some(collection("sections"))
      case ResourceRef.curationObject => Some(collection("curationObjects"))
      case ResourceRef.curationFile => Some(collection("curationFiles"))
      case _ => {
        Logger.error(s"Can not map resource ${resourceRef.resourceType} to collection.")
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
      Logger.info(x.getOrElse("_id", "?") + " " + x.getOrElse("path", "?"))
      x.getAs[ObjectId]("_id") match {
        case Some(id) => MongoUtils.removeBlob(UUID(id.toString), name, "nevereverused")
        case None =>
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
  }

  private def updateMongo(updateKey: String, block: () => Unit): Unit = {
    val appConfig: AppConfigurationService = DI.injector.getInstance(classOf[AppConfigurationService])

    if (!appConfig.hasPropertyValue("mongodb.updates", updateKey)) {
      if (System.getProperty("MONGOUPDATE") != null) {
        Logger.info(s"About to begin update of mongo : ${updateKey}.")
        val start = System.currentTimeMillis()
        try {
          block()
          appConfig.addPropertyValue("mongodb.updates", updateKey)
        } catch {
          case e:Exception => {
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

  private def updateMongoChangeUserType() {
    val q = MongoDBObject("_typeHint" -> "securesocial.core.SocialUser")
    val o = MongoDBObject("$set" -> MongoDBObject("_typeHint" -> "models.ClowderUser"))
    collection("social.users").update(q, o, multi=true, concern=WriteConcern.Safe)
  }

  private def updateMongoAddFirstSpace() {
    val spaces = ProjectSpaceDAO.count(new MongoDBObject())
    if (spaces == 0) {
      val datasets = Dataset.count(new MongoDBObject())
      val collections = Collection.count(new MongoDBObject())
      val users = SocialUserDAO.count(new MongoDBObject())
      if ((datasets != 0) || (collections != 0)) {
        Logger.info("[MongoDBUpdate] : Found datasets/collections, will add all to default space")

        // create roles (this is called before Global)
        if (RoleDAO.count() == 0) {
          RoleDAO.save(Role.Admin)
          RoleDAO.save(Role.Editor)
          RoleDAO.save(Role.Viewer)
        }

        // create the space
        val spacename = java.net.InetAddress.getLocalHost.getHostName
        val newspace = new ProjectSpace(name=spacename, description="", created=new Date(), creator=UUID("000000000000000000000000"),
          homePage=List.empty[URL], logoURL=None, bannerURL=None, metadata=List.empty[Metadata],
          collectionCount=collections.toInt, datasetCount=datasets.toInt, userCount=users.toInt)
        ProjectSpaceDAO.save(newspace)
        val spaceId = new ObjectId(newspace.id.stringify)

        // add space to all datasets/collections
        val q = MongoDBObject()
        val o = MongoDBObject("$set" -> MongoDBObject("spaces" -> List[ObjectId](spaceId)))
        collection("datasets").update(q ,o, multi=true)
        collection("collections").update(q ,o, multi=true)

        // add all users as admin
        val adminRole = collection("roles").findOne(MongoDBObject("name" -> "Admin"))
        val spaceRole = MongoDBObject("_typeHint" -> "models.UserSpaceAndRole", "spaceId" -> spaceId, "role" -> adminRole)
        collection("social.users").update(MongoDBObject(), $push("spaceandrole" -> spaceRole), multi=true)

      } else {
        Logger.info("[MongoDBUpdate] : No datasets/collections found, will not create default space")
      }
    } else {
      Logger.info("[MongoDBUpdate] : Found spaces, will not create default space")
    }
  }

  def updateTagLength() {
    val q = MongoDBObject("tags" -> MongoDBObject("$exists" -> true, "$not" -> MongoDBObject("$size" -> 0)))
    val maxTagLength = play.api.Play.configuration.getInt("clowder.tagLength").getOrElse(100)
    Logger.info("[MongoDBUpdate] : fixing " + collection("datasets").count(q) + " datasets")
    collection("datasets").find(q).foreach { x =>
      x.getAsOrElse[MongoDBList]("tags", MongoDBList.empty).foreach { case tag:DBObject =>
        if (tag.getAsOrElse[String]("name", "").length > maxTagLength) {
          Logger.info(x.get("_id").toString + " : truncating " + tag.getAsOrElse[String]("name", ""))
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
      x.getAsOrElse[MongoDBList]("tags", MongoDBList.empty).foreach { case tag:DBObject =>
        if (tag.getAsOrElse[String]("name", "").length > maxTagLength) {
          Logger.info(x.get("_id").toString + " : truncating " + tag.getAsOrElse[String]("name", ""))
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
      x.getAsOrElse[MongoDBList]("tags", MongoDBList.empty).foreach { case tag:DBObject =>
        if (tag.getAsOrElse[String]("name", "").length > maxTagLength) {
          Logger.info(x.get("_id").toString + " : truncating " + tag.getAsOrElse[String]("name", ""))
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
    collection("collections").foreach {c =>
      val datasets = c.getAsOrElse[MongoDBList]("datasets", MongoDBList.empty)
      c.removeField("datasets")
      c.put("datasetCount", datasets.length)
      collection("collections").save(c, WriteConcern.Safe)

      datasets.foreach {d =>
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
      val fileIds = files.map(file => new ObjectId(file.asInstanceOf[BasicDBObject].get("_id").toString)).toList
      ds.put("files", fileIds)
      try {
        collection("datasets").save(ds, WriteConcern.Safe)
      }
      catch {
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
          if(ds.containsField("metadata")) {
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
      collection("collections").update(q, o, multi=true)
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
      }
      catch {
        case e: BSONException => Logger.error("Unable to update invite:" + invite.getAsOrElse[ObjectId]("_id", new ObjectId()).toString)
      }
    }
  }

  private def addLengthSha512PathFile() {
    val dbss = new DiskByteStorageService()
    lazy val rootPath = Play.current.configuration.getString("medici2.diskStorage.path").getOrElse("")
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
        }
        catch {
          case e: Exception => Logger.error("Unable to update file :" + id.toString, e)
        }
      }
    }

    // no need to fixSha512
    DI.injector.getInstance(classOf[AppConfigurationService]).addPropertyValue("mongodb.updates", "fixing-mongo-sha512")
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
        }
        catch {
          case e: Exception => Logger.error("Unable to update file :" + id.toString, e)
        }
      }
    }
  }

  private def updateUserPreference(){
      collection("social.users").update(MongoDBObject(), $unset("repositoryPreferences.access"), multi=true)
      collection("social.users").update(MongoDBObject(), $unset("repositoryPreferences.organizational_affiliation"), multi=true)
      collection("social.users").update(MongoDBObject(), $unset("repositoryPreferences.cost"), multi=true)
      collection("social.users").update(MongoDBObject(), $unset("repositoryPreferences.license"), multi=true)
      collection("social.users").update(MongoDBObject(), $unset("repositoryPreferences"), multi=true)
  }

  private def activateAllUsers() {
    val query = MongoDBObject("active" -> MongoDBObject("$exists" -> false))
    val update = MongoDBObject("$set" -> MongoDBObject("active" -> true))
    collection("social.users").update(query, update, upsert=false, multi=true)
  }

  private def migrateNotesInFiles() {
    collection("uploads.files").foreach { file =>
      val note = file.getAsOrElse[String]("notesHTML", "")
      if(note != "") {
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
      if(note != "") {
        val description = ds.getAsOrElse[String]("description", "")
        if(description != "") {
          ds.put("description", description+ " " +note)
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

}
