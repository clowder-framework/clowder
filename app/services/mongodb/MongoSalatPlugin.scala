package services.mongodb

import java.net.URL
import java.util.Date

import com.mongodb.CommandFailureException
import com.mongodb.casbah.Imports._
import com.mongodb.casbah.commons.MongoDBObject
import models._
import org.bson.BSONException
import play.api.libs.json._
import play.api.{ Plugin, Logger, Application }
import play.api.Play.current
import com.mongodb.casbah.MongoURI
import com.mongodb.casbah.MongoConnection
import com.mongodb.casbah.MongoDB
import com.mongodb.casbah.MongoCollection
import com.mongodb.casbah.gridfs.GridFS
import org.bson.types.ObjectId
import securesocial.core.Identity
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
  def dropAllData() {
    Logger.debug("**DANGER** Deleting data collections **DANGER**")
    collection("collections").drop()
    collection("comments").drop()
    collection("datasets").drop()
    collection("dtsrequests").drop()
    collection("extractions").drop()
    collection("extractor.servers").drop()
    collection("extractor.names").drop()
    collection("extractor.inputtypes").drop()
    collection("multimedia.features").drop()
    collection("previews.chunks").drop()
    collection("previews.files").drop()
    collection("sections").drop()
    collection("streams").drop()
    collection("thumbnails.chunks").drop()
    collection("thumbnails.files").drop()
    collection("uploads.chunks").drop()
    collection("uploads.files").drop()
    collection("uploadquery.files").drop()
    collection("versus.descriptors").drop()
    collection("metadata").drop()
    collection("contexld").drop()
    collection("spaces.projects").drop()
    collection("spaces.users").drop()
    Logger.debug("**DANGER** Data deleted **DANGER**")
  }

  // ----------------------------------------------------------------------
  // CODE TO UPDATE THE DATABASE
  // ----------------------------------------------------------------------
  def updateDatabase() {
    val appConfig: AppConfigurationService = DI.injector.getInstance(classOf[AppConfigurationService])

    // migrate users to new model
    updateMongoChangeUserType

    // add a space if none exists
    updateMongoAddFirstSpace

    // remove datasets from collection
    updateMongoRemoveDatasetCollection

    // replace collection id strings with UUID in datasets
    updateMongoCollectionsInDatasetStringToUUID

    // change Files in datasets from List[File] to List[UUID]
    updateReplaceFilesInDataset

    // migrate metadata to jsonld
    migrateMetadataRepresentationtoJSONLD

    // collection now requires author
    collectionRequiresAuthor
  }

  private def updateMongoChangeUserType {
    val appConfig: AppConfigurationService = DI.injector.getInstance(classOf[AppConfigurationService])

    if (!appConfig.hasPropertyValue("mongodb.updates", "fixing-typehint-users")) {
      if (System.getProperty("MONGOUPDATE") != null) {
        Logger.info("[MongoDBUpdate] : Fixing _typeHint for users.")
        val q = MongoDBObject("_typeHint" -> "securesocial.core.SocialUser")
        val o = MongoDBObject("$set" -> MongoDBObject("_typeHint" -> "models.ClowderUser"))
        collection("social.users").update(q, o, multi=true, concern=WriteConcern.Safe)
        appConfig.addPropertyValue("mongodb.updates", "fixing-typehint-users")
      } else {
        Logger.warn("[MongoDBUpdate] : Missing fix _typeHint for users.")
      }
    }
  }

  private def updateMongoAddFirstSpace {
    val appConfig: AppConfigurationService = DI.injector.getInstance(classOf[AppConfigurationService])

    if (!appConfig.hasPropertyValue("mongodb.updates", "convert-to-spaces")) {
      if (System.getProperty("MONGOUPDATE") != null) {
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
        appConfig.addPropertyValue("mongodb.updates", "convert-to-spaces")
      } else {
        Logger.warn("[MongoDBUpdate] : Missing fix to convert to spaces.")
      }
    }

    updateTagLength()
  }

  def updateTagLength() {
    val appConfig: AppConfigurationService = DI.injector.getInstance(classOf[AppConfigurationService])

    // migrate users to new model
    if (!appConfig.hasPropertyValue("mongodb.updates", "fixing-taglength")) {
      if (System.getProperty("MONGOUPDATE") != null) {
        Logger.info("[MongoDBUpdate] : Fixing taglength.")
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
        Logger.info("[MongoDBUpdate] : fixing " + collection("uploads.files").count(q) + " files")
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
        Logger.info("[MongoDBUpdate] : fixing " + collection("sections").count(q) + " files")
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
        appConfig.addPropertyValue("mongodb.updates", "fixing-taglength")
      } else {
        Logger.warn("[MongoDBUpdate] : Missing fix taglength for tags.")
      }
    }
  }

  private def updateMongoRemoveDatasetCollection {
    val appConfig: AppConfigurationService = DI.injector.getInstance(classOf[AppConfigurationService])

    if (!appConfig.hasPropertyValue("mongodb.updates", "removed-datasets-collection")) {
      if (System.getProperty("MONGOUPDATE") != null) {
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
        appConfig.addPropertyValue("mongodb.updates", "removed-datasets-collection")
      } else {
        Logger.warn("[MongoDBUpdate] : Missing fix to remove datasets from collection.")
      }
    }
  }

  private def updateMongoCollectionsInDatasetStringToUUID {
    val appConfig: AppConfigurationService = DI.injector.getInstance(classOf[AppConfigurationService])

    if (!appConfig.hasPropertyValue("mongodb.updates", "replace-dataset-collections-string-uuid")) {
      if (System.getProperty("MONGOUPDATE") != null) {
        collection("datasets").foreach { ds =>
          val collection_string = ds.getAsOrElse[MongoDBList]("collections", MongoDBList.empty)
          val collection_uuids = collection_string.map(col => new ObjectId(col.toString)).toList
          ds.put("collections", collection_uuids)
          try {
            collection("datasets").save(ds, WriteConcern.Safe)
          } catch {
            case e: BSONException => Logger.error("Failed to refactor collections (String -> UUID) in  dataset with id" + ds.getAsOrElse[ObjectId]("_id", new ObjectId()).toString())
          }
        }
        appConfig.addPropertyValue("mongodb.updates", "replace-dataset-collections-string-uuid")
      } else {
        Logger.warn("[MongoDBUpdate : Missing fix to replace the collections in the dataset with UUIDs")
      }
    }
  }

  /**
   * Replaces the files in the datasets from acopy of the files tojust the file UUID's.
   */
  private def updateReplaceFilesInDataset{
    val appConfig: AppConfigurationService = DI.injector.getInstance(classOf[AppConfigurationService])

    if (!appConfig.hasPropertyValue("mongodb.updates", "replace-dataset-files-with-id")) {
      if (System.getProperty("MONGOUPDATE") != null) {
        collection("datasets").foreach { ds =>
          val files = ds.getAsOrElse[MongoDBList]("files", MongoDBList.empty)
          val fileIds = files.map(file => new ObjectId(file.asInstanceOf[BasicDBObject].get("_id").toString)).toList
          ds.put("files", fileIds)
          try {
            collection("datasets").save(ds, WriteConcern.Safe)
          }
          catch {
            case e: BSONException => Logger.error("Unable to update files in dataset:" + ds.getAsOrElse[ObjectId]("_id", new ObjectId()).toString() )
          }
        }
      }
      appConfig.addPropertyValue("mongodb.updates", "replace-dataset-files-with-id")
    } else {
      Logger.warn("[MongoDBUpdate : Missing fix to replace the files in the dataset with UUIDs")
    }
  }


  private def migrateMetadataRepresentationtoJSONLD {
    val updateId = "migrate-metadata-jsonld"
    val appConfig: AppConfigurationService = DI.injector.getInstance(classOf[AppConfigurationService])
    val metadataService: MetadataService = DI.injector.getInstance(classOf[MetadataService])

    if (!appConfig.hasPropertyValue("mongodb.updates", updateId)) {
      if (System.getProperty("MONGOUPDATE") != null) {
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
                    val metadataUser = models.Metadata(UUID.generate, attachedTo.get, contextID, contextURL, createdAt, creatorUser, userMD, version)
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
                    val metadataTech = models.Metadata(UUID.generate, attachedTo.get, contextID, contextURL, createdAt, creatorExtractor, techMD, version)
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
                    val metadataUser = models.Metadata(UUID.generate, attachedTo.get, contextID, contextURL, createdAt, creatorUser, userMD, version)
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
                    val metadataTech = models.Metadata(UUID.generate, attachedTo.get, contextID, contextURL, createdAt, creatorExtractor, techMD, version)
                    metadataService.addMetadata(metadataTech)
                  }
                } else {
                  val techMD = Json.parse(com.mongodb.util.JSON.serialize(tmd))
                  val creatorExtractor = ExtractorAgent(id = UUID.generate(), extractorId = Some(new URL("http://clowder.ncsa.illinois.edu/extractors/migration")))
                  val metadataTech = models.Metadata(UUID.generate, attachedTo.get, contextID, contextURL, createdAt, creatorExtractor, techMD, version)
                  metadataService.addMetadata(metadataTech)
                }
              }
            }
            case None => Logger.error(s"[MongoDBUpdate : Missing dataset id")
          }
        }
        appConfig.addPropertyValue("mongodb.updates", updateId)
      } else {
        Logger.warn("[MongoDBUpdate] : Missing fix to update metadata to JSONLD representation")
      }
    }
  }

  private def collectionRequiresAuthor(): Unit = {
    val updateId = "collection-author"
    val appConfig: AppConfigurationService = DI.injector.getInstance(classOf[AppConfigurationService])

    if (!appConfig.hasPropertyValue("mongodb.updates", updateId)) {
      if (System.getProperty("MONGOUPDATE") != null) {
        val q = "author" $exists false
        val o = MongoDBObject("$set" -> MongoDBObject("author" -> SocialUserDAO.dao.toDBObject(User.anonymous)))
        collection("collections").update(q, o, multi=true)
        appConfig.addPropertyValue("mongodb.updates", updateId)
      } else {
        Logger.warn("[MongoDBUpdate] : Missing fix to set anonymous author to collection when not set")
      }
    }
  }
}
