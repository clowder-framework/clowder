package services.mongodb

import java.io._
import java.text.SimpleDateFormat
import java.util.{ArrayList, Date}
import javax.inject.{Inject, Singleton}

import Transformation.LidoToCidocConvertion
import util.{Formatters, Parsers}
import api.Permission
import api.Permission.Permission
import com.mongodb.DBObject
import com.mongodb.casbah.Imports._
import com.mongodb.casbah.WriteConcern
import com.mongodb.casbah.commons.MongoDBList
import com.mongodb.casbah.commons.MongoDBObject
import com.mongodb.util.JSON
import com.novus.salat.dao.{ModelCompanion, SalatDAO}
import jsonutils.JsonUtil
import models.{File, _}
import org.apache.commons.io.FileUtils
import org.bson.types.ObjectId
import org.json.JSONObject
import play.api.Logger
import play.api.Play._
import play.api.libs.json.Json._
import play.api.libs.json.{JsArray, JsValue, Json}
import services._
import services.mongodb.MongoContext.context

import scala.annotation.tailrec
import scala.collection.JavaConversions._
import scala.collection.JavaConverters._
import scala.collection.mutable.ListBuffer
import scala.util.parsing.json.JSONArray

/**
 * Use Mongodb to store datasets.
 *
 */
@Singleton
class MongoDBDatasetService @Inject() (
  collections: CollectionService,
  files: FileService,
  comments: CommentService,
  sparql: RdfSPARQLService,
  spaces: SpaceService,
  userService: UserService,
  folders: FolderService,
  metadatas:MetadataService,
  events: EventService,
  appConfig: AppConfigurationService,
  esqueue: ElasticsearchQueue) extends DatasetService {

  object MustBreak extends Exception {}

  /**
   * Count all datasets
   */
  def count(): Long = {
    Dataset.count(MongoDBObject())
  }

  /**
   * Count all datasets in a space
   */
  def countSpace(space: String): Long = {
    count(None, false, None, None, Some(space), Set[Permission](Permission.ViewDataset), None, showAll=true, None, false)
  }

  /**
   * Return a list of datasets in a space, this does not check for permissions
   */
  def listSpace(limit: Integer, space: String): List[Dataset] = {
    list(None, false, limit, None, None, Some(space), Set[Permission](Permission.ViewDataset), None, status=None, showAll=true, owner=None)
  }

  /**
   * Return a list of datasets in a space starting at a specific date, this does not check for permissions
   */
  def listSpace(date: String, nextPage: Boolean, limit: Integer, space: String): List[Dataset] = {
    list(Some(date), nextPage, limit, None, None, Some(space), Set[Permission](Permission.ViewDataset), None, status=None, showAll=true, owner=None)
  }

  /**
    * Return a list of datasets in a space
    */
  def listSpace(limit: Integer, space: String, user:Option[User]): List[Dataset] = {
    list(None, false, limit, None, None, Some(space), Set[Permission](Permission.ViewDataset), user, status=None, showAll=false, owner=None)
  }

  /**
    * Return a list of datasets in a space starting at a specific date, this does not check for permissions
    */
  def listSpace(date: String, nextPage: Boolean, limit: Integer, space: String, user:Option[User]): List[Dataset] = {
    list(Some(date), nextPage, limit, None, None, Some(space), Set[Permission](Permission.ViewDataset), user, status=None, showAll=false, owner=None)
  }

  /**
    * Return a list of datasets in a space filtered by status, this does not check for permissions
    */
  def listSpaceStatus(limit: Integer, space: String, status: String): List[Dataset] = {
    list(None, false, limit, None, None, Some(space), Set[Permission](Permission.ViewDataset), None, Some(status), showAll=true, owner=None)
  }
  /**
    * Return a list of datasets in a space filtered by status
    */
  def listSpaceStatus(limit: Integer, space: String, status: String, user:Option[User]): List[Dataset] = {
    list(None, false, limit, None, None, Some(space), Set[Permission](Permission.ViewDataset), user, Some(status), showAll=true, owner=None)
  }

  /**
    * Return a list of datasets in a space filtered by status
    */
  def listSpaceStatus(date: String, nextPage: Boolean, limit: Integer, space: String, status: String, user:Option[User]): List[Dataset] = {
    list(Some(date), nextPage, limit, None, None, Some(space), Set[Permission](Permission.ViewDataset), user, Some(status), showAll=true, owner=None)
  }

  /**
   * Count all datasets in a collection
   */
  def countCollection(collection: String): Long = {
    count(None, false, None, Some(collection), None, Set[Permission](Permission.ViewDataset), None, showAll=true, None, false)
  }

  /**
   * Return a list of datasets in a collection, this does not check for permissions
   */
  def listCollection(collection: String): List[Dataset] = {
    list(None, false, 0, None, Some(collection), None, Set[Permission](Permission.ViewDataset), None, status=None, showAll=false, owner=None)
  }

  /**
   * Return a list of datasets in a collection, this does not check for permissions
   */
  def listCollection(limit: Integer, collection: String): List[Dataset] = {
    list(None, false, limit, None, Some(collection), None, Set[Permission](Permission.ViewDataset), None, status=None, showAll=false, owner=None)
  }

  /**
   * Return a list of datasets in a collection starting at a specific date, this does not check for permissions
   */
  def listCollection(date: String, nextPage: Boolean, limit: Integer, collection: String): List[Dataset] = {
    list(Some(date), nextPage, limit, None, Some(collection), None, Set[Permission](Permission.ViewDataset), None, status=None, showAll=false, owner=None)
  }

  /**
    * Return a list of datasets in a collection
    */
  def listCollection(collection: String, user:Option[User]): List[Dataset] = {
    list(None, false, 0, None, Some(collection), None, Set[Permission](Permission.ViewDataset), user, status=None, showAll=false, owner=None)
  }

  /**
    * Return a list of datasets in a collection
    */
  def listCollection(limit: Integer, collection: String, user:Option[User]): List[Dataset] = {
    list(None, false, limit, None, Some(collection), None, Set[Permission](Permission.ViewDataset), user, status=None, showAll=false, owner=None)
  }

  /**
    * Return a list of datasets in a collection starting at a specific date
    */
  def listCollection(date: String, nextPage: Boolean, limit: Integer, collection: String, user:Option[User]): List[Dataset] = {
    list(Some(date), nextPage, limit, None, Some(collection), None, Set[Permission](Permission.ViewDataset), user, status=None, showAll=false, owner=None)
  }

  /**
   * Count all datasets the user has access to.
   */
  def countAccess(permissions: Set[Permission], user: Option[User], showAll: Boolean): Long = {
    count(None, false, None, None, None, permissions, user, showAll, None, false)
  }

  /**
   * Return a list of datasets the user has access to.
   */
  def listAccess(limit: Integer, permissions: Set[Permission], user: Option[User], showAll: Boolean, showPublic: Boolean, showOnlyShared : Boolean): List[Dataset] = {
    list(None, false, limit, None, None, None, permissions, user, None, showAll, None, showPublic)
  }

  /**
   * Return a list of datasets the user has access to.
   */
  def listAccess(limit: Integer, title: String, permissions: Set[Permission], user: Option[User],
                 showAll: Boolean, showPublic: Boolean, showOnlyShared : Boolean, exact: Boolean): List[Dataset] = {
    list(None, false, limit, Some(title), None, None, permissions, user, None, showAll, None, showPublic, exactMatch=exact)
  }

  /**
   * Return a list of datasets the user has access to starting at a specific date.
   */
  def listAccess(date: String, nextPage: Boolean, limit: Integer, permissions: Set[Permission], user: Option[User], showAll: Boolean, showPublic: Boolean, showOnlyShared : Boolean): List[Dataset] = {
    list(Some(date), nextPage, limit, None, None, None, permissions, user, None, showAll, None, showPublic)
  }

  /**
   * Return a list of datasets the user has access to starting at a specific date.
   */
  def listAccess(date: String, nextPage: Boolean, limit: Integer, title: String, permissions: Set[Permission], user: Option[User], showAll: Boolean, showPublic: Boolean, showOnlyShared : Boolean, exact : Boolean): List[Dataset] = {
    list(Some(date), nextPage, limit, Some(title), None, None, permissions, user, None, showAll, None, showPublic, exactMatch=exact)
  }

  /**
    * Return a list of datasets in a space the user has access to.
    */
  def listSpaceAccess(limit: Integer, permissions: Set[Permission], space: String, user: Option[User], showAll: Boolean, showPublic: Boolean): List[Dataset] = {
    list(None, false, limit, None, None, Some(space), permissions, user, None, showAll, None, showPublic)
  }

  /**
    * Return a list of datasets in a space the user has access to.
    */
  def listSpaceAccess(limit: Integer, title: String, permissions: Set[Permission], space: String, user: Option[User], showAll: Boolean, showPublic: Boolean): List[Dataset] = {
    list(None, false, limit, Some(title), None, Some(space), permissions, user, None, showAll, None, showPublic)
  }

  /**
    * Return a list of datasets in a space the user has access to starting at a specific date.
    */
  def listSpaceAccess(date: String, nextPage: Boolean, limit: Integer, permissions: Set[Permission], space: String, user: Option[User], showAll: Boolean, showPublic: Boolean): List[Dataset] = {
    list(Some(date), nextPage, limit, None, None, Some(space), permissions, user, None, showAll, None, showPublic)
  }

  /**
    * Return a list of datasets in a space the user has access to starting at a specific date.
    */
  def listSpaceAccess(date: String, nextPage: Boolean, limit: Integer, title: String, permissions: Set[Permission], space: String, user: Option[User], showAll: Boolean, showPublic: Boolean): List[Dataset] = {
    list(Some(date), nextPage, limit, Some(title), None, Some(space), permissions, user, None, showAll, None, showPublic)
  }

  /**
   * Count all datasets the user has created.
   */
  def countUser(user: Option[User], showAll: Boolean, owner: User): Long = {
    count(None, false, None, None, None, Set[Permission](Permission.ViewDataset), user, showAll, Some(owner))
  }

  /**
   * Return a list of datasets the user has created.
   */
  def listUser(limit: Integer, user: Option[User], showAll: Boolean, owner: User): List[Dataset] = {
    list(None, false, limit, None, None, None, Set[Permission](Permission.ViewDataset), user, None, showAll, Some(owner))
  }

  /**
    * Return a list of datasets the user has created.
    */
  def listUserTrash(limit: Integer, user: Option[User], showAll: Boolean, owner: User): List[Dataset] = {
    list(None, false, limit, None, None, None, Set[Permission](Permission.ViewDataset), user, None, showAll, Some(owner), false, false, true)
  }


  /**
   * Return a list of datasets the user has created starting at a specific date.
   */
  def listUser(date: String, nextPage: Boolean, limit: Integer, user: Option[User], showAll: Boolean, owner: User): List[Dataset] = {
    list(Some(date), nextPage, limit, None, None, None, Set[Permission](Permission.ViewDataset), user, None, showAll, Some(owner))
  }

  /**
    * Return a list of datasets the user has created starting at a specific date.
    */
  def listUserTrash(date: String, nextPage: Boolean, limit: Integer, user: Option[User], showAll: Boolean, owner: User): List[Dataset] = {
    list(Some(date), nextPage, limit, None, None, None, Set[Permission](Permission.ViewDataset), user, None, showAll, Some(owner), false, false, true)
  }

  /**
    * Return a list of datasets a user can View.
    */
  def listUser(user: Option[User]): List[Dataset] = {
    if (user.isDefined && user.get.superAdminMode)
      return Dataset.find(new MongoDBObject()).toList

    val orlist = scala.collection.mutable.ListBuffer.empty[MongoDBObject]

    orlist += MongoDBObject("status" -> DatasetStatus.PUBLIC.toString)
    user.foreach{u =>
      orlist += MongoDBObject("spaces" -> List.empty) ++ MongoDBObject("author._id" -> new ObjectId(u.id.stringify))
      val okspaces = u.spaceandrole.filter(_.role.permissions.intersect(Set(Permission.ViewDataset.toString)).nonEmpty)
      if (okspaces.nonEmpty) {
        orlist += ("spaces" $in okspaces.map(x => new ObjectId(x.spaceId.stringify)))
      }
    }
    if (orlist.isEmpty) {
      orlist += MongoDBObject("doesnotexist" -> true)
    }
    Dataset.find($or(orlist.map(_.asDBObject))).toList
  }

  def listUserTrash(user : Option[User], limit : Integer) : List[Dataset] = {
    val (filter,sort) = trashFilterQuery(user)
    Dataset.find(filter).sort(sort).limit(limit).toList.reverse
  }

  private def trashFilterQuery( user: Option[User]) : (DBObject, DBObject) = {
    user match {
      case Some(u) => {
        val trashFilter = MongoDBObject("trash" -> true)
        val author = MongoDBObject("author._id" -> new ObjectId(user.get.id.stringify))
        val sort = {
          MongoDBObject("created" -> 1) ++ MongoDBObject("name" -> 1)
        }
        (trashFilter ++ author,sort)
      }
      case None => {
        val trashFilter = MongoDBObject("trash" ->true)
        val sort = {
          MongoDBObject("created" -> 1) ++ MongoDBObject("name" -> 1)
        }
        (trashFilter,sort)
      }
    }
  }

  /**
   * return count based on input
   */
  private def count(date: Option[String], nextPage: Boolean, title: Option[String], collection: Option[String], space: Option[String], permissions: Set[Permission], user: Option[User], showAll: Boolean, owner: Option[User], exactMatch : Boolean = false): Long = {
    val (filter, _) = filteredQuery(date, nextPage, title, collection, space, Set[Permission](Permission.ViewDataset), user, None, showAll, owner, true, false, false, exactMatch)
    Dataset.count(filter)
  }


  /**
   * return list based on input
   */
  private def list(date: Option[String], nextPage: Boolean, limit: Integer, title: Option[String], collection: Option[String], space: Option[String], permissions: Set[Permission], user: Option[User], status: Option[String], showAll: Boolean, owner: Option[User], showPublic: Boolean = true, showOnlyShared : Boolean = false, showTrash : Boolean = false, exactMatch : Boolean = false): List[Dataset] = {
    val (filter, sort) = filteredQuery(date, nextPage, title, collection, space, permissions, user, status, showAll, owner, showPublic, showOnlyShared, showTrash, exactMatch)
    if (date.isEmpty || nextPage) {
      Dataset.find(filter).sort(sort).limit(limit).toList
    } else {
      Dataset.find(filter).sort(sort).limit(limit).toList.reverse
    }
  }

  /**
   * Monster function, does all the work. Will create a filters and sorts based on the given parameters
   */
  private def filteredQuery(date: Option[String], nextPage: Boolean, titleSearch: Option[String], collection: Option[String], space: Option[String], permissions: Set[Permission], user: Option[User], status:Option[String], showAll: Boolean, owner: Option[User], showPublic: Boolean, showOnlyShared : Boolean, showTrash : Boolean, exactMatch : Boolean): (DBObject, DBObject) = {
    // filter =
    // - owner   == show datasets owned by owner that user can see
    // - space   == show all datasets in space
    // - access  == show all datasets the user can see
    // - default == public only
    val public = MongoDBObject("public" -> true)
    val enablePublic = play.Play.application().configuration().getBoolean("enablePublic")
    //emptySpaces should not be used in most cases since your dataset maybe in a space, then you are changed to viewer or kicked off.
    val emptySpaces = MongoDBObject("spaces" -> List.empty)
    val publicSpaces = spaces.listByStatus(SpaceStatus.PUBLIC.toString).map(s => new ObjectId(s.id.stringify))

    val filterTrash = if (showTrash == false)  {
      MongoDBObject("trash"->false)
    } else {
      val trashNotNone = MongoDBObject("trash" -> true)
      val author = MongoDBObject("author._id" -> new ObjectId(user.get.id.stringify))
      (trashNotNone ++ author)
    }

    // create access filter
    val filterAccess = if (showAll || configuration(play.api.Play.current).getString("permissions").getOrElse("public") == "public" && permissions.contains(Permission.ViewDataset)) {
      MongoDBObject()
    } else {
      user match {
        case Some(u) => {

          val orlist = scala.collection.mutable.ListBuffer.empty[MongoDBObject]
          if(u.superAdminMode) {
            // superAdmin can access all datasets, in a space page or /datasets
            orlist += MongoDBObject()
          }
          if (permissions.contains(Permission.ViewDataset) && enablePublic && showPublic) {
            // if enablePublic == true, only list the dataset user can access, in a space page or /datasets
            if(!u.superAdminMode) {
              orlist += MongoDBObject("status" -> DatasetStatus.PUBLIC.toString)
              orlist += MongoDBObject("status" -> DatasetStatus.DEFAULT.toString) ++ ("spaces" $in publicSpaces)
            }
          }
          //if you are viewing other user's datasets, return the ones you have permission. otherwise filterAccess should
          // including your own datasets. the if condition here is mainly for efficiency.
          if(user == owner || owner.isEmpty) {
            if (owner.isEmpty && !showOnlyShared){
              orlist += MongoDBObject("author._id" -> new ObjectId(u.id.stringify))
            } else if (!owner.isEmpty){
              orlist += MongoDBObject("author._id" -> new ObjectId(u.id.stringify))
            }
          }
          val permissionsString = permissions.map(_.toString)
          val okspaces = if (showOnlyShared){
            u.spaceandrole.filter(_.role.permissions.intersect(permissionsString).nonEmpty).filter((p: UserSpaceAndRole)=>
              (spaces.get(p.spaceId) match {
                case Some(space) => {
                  if (space.userCount > 1){
                    true
                  } else {
                    false
                  }
                }
                case None => false
              })
            )
          } else {
            u.spaceandrole.filter(_.role.permissions.intersect(permissionsString).nonEmpty)
          }
          if (okspaces.nonEmpty) {
            orlist += ("spaces" $in okspaces.map(x => new ObjectId(x.spaceId.stringify)))
          }
          if (orlist.isEmpty) {
            orlist += MongoDBObject("doesnotexist" -> true)
          }
          $or(orlist.map(_.asDBObject))
        }
        case None =>
          if (!permissions.intersect(Permission.READONLY).isEmpty && enablePublic && showPublic) {
            $or(MongoDBObject("status" -> DatasetStatus.PUBLIC.toString),
              MongoDBObject("status" -> DatasetStatus.DEFAULT.toString) ++ ("spaces" $in publicSpaces))
          } else {
            MongoDBObject("doesnotexist" -> true)
          }
      }
    }
    val filterOwner = owner match {
      case Some(o) => {
        if (showOnlyShared){
          MongoDBObject()
        } else {
          MongoDBObject("author._id" -> new ObjectId(o.id.stringify))
        }

      }
      case None => MongoDBObject()
    }
    val filterSpace = space match {
      case Some(s) => MongoDBObject("spaces" -> new ObjectId(s))
      case None => MongoDBObject()
    }
    val filterStatus = status match {
      case Some("public") => MongoDBObject("status" -> DatasetStatus.PUBLIC.toString)
      case Some("private") => MongoDBObject("status" -> DatasetStatus.PRIVATE.toString)
      case Some("default") => MongoDBObject("status" -> DatasetStatus.DEFAULT.toString)
      case Some("trial") => MongoDBObject("status" -> DatasetStatus.TRIAL.toString)
      case Some("publicAll") => {
        val mdbolist = scala.collection.mutable.ListBuffer.empty[MongoDBObject]
        mdbolist += MongoDBObject("status" -> DatasetStatus.PUBLIC.toString)
        mdbolist += MongoDBObject("status" -> DatasetStatus.DEFAULT.toString) ++ ("spaces" $in publicSpaces)
        $or(mdbolist.map(_.asDBObject))
      }
      case None => MongoDBObject()
    }
    val filterCollection = collection match {
      case Some(c) => MongoDBObject("collections" -> new ObjectId(c))
      case None => MongoDBObject()
    }
    val filterTitle = titleSearch match {
      case Some(title) =>  {
        if (exactMatch)
          MongoDBObject("name" -> title)
        else
          MongoDBObject("name" -> ("(?i)" + title).r)
      }
      case None => MongoDBObject()
    }

    val filterNotShared = if (showOnlyShared && owner.isEmpty){
      MongoDBObject("root_spaces" -> MongoDBObject("$not" -> MongoDBObject("$size" -> 0)))
    } else {
      MongoDBObject()
    }

    val filterDate = date match {
      case Some(d) => {
        if (nextPage) {
          ("created" $lt Formatters.iso8601(d))
        } else {
          ("created" $gt Formatters.iso8601(d))
        }
      }
      case None => MongoDBObject()
    }

    val sort = if (date.isDefined && !nextPage) {
      MongoDBObject("created"-> 1) ++ MongoDBObject("name" -> 1)
    } else {
      MongoDBObject("created" -> -1) ++ MongoDBObject("name" -> 1)
    }

    (filterAccess ++ filterDate ++ filterTitle ++ filterStatus ++ filterCollection ++ filterSpace ++ filterOwner ++ filterTrash, sort)
  }

  def isInCollection(dataset: Dataset, collection: Collection): Boolean = {
    dataset.collections.contains(collection.id.stringify)
  }

  /**
   * Get dataset.
   */
  def get(id: UUID): Option[Dataset] = {
    Dataset.findOneById(new ObjectId(id.stringify))
  }

  def get(ids: List[UUID]): DBResult[Dataset] = {
    if (ids.length == 0) return DBResult(List.empty, List.empty)

    val query = MongoDBObject("_id" -> MongoDBObject("$in" -> ids.map(id => new ObjectId(id.stringify))))
    val found = Dataset.find(query).toList
    val notFound = ids.diff(found.map(_.id))

    if (notFound.length > 0)
      Logger.error("Not all dataset IDs found for bulk get request")
    return DBResult(found, notFound)
  }

  /**
   * Updated dataset.
   */
  def update(dataset: Dataset) {
    Dataset.save(dataset)
  }

  def insert(dataset: Dataset): Option[String] = {
    Dataset.insert(dataset).map(_.toString)
  }

  /**
   *
   */
  def getFileId(datasetId: UUID, filename: String): Option[UUID] = {
    get(datasetId) match {
      case Some(dataset) => {
        files.get(dataset.files).found.foreach(file => {
          if(file.filename.equals(filename)) {
            return Some(file.id)
          }
        })
        Logger.error("File does not exist in dataset" + datasetId);
        return None
      }
      case None => { Logger.error("Error getting dataset" + datasetId); return None }
    }
  }

  def modifyRDFOfMetadataChangedDatasets(){
    val changedDatasets = findMetadataChangedDatasets()
    for(changedDataset <- changedDatasets){
      modifyRDFUserMetadata(changedDataset.id)
    }
  }

  def modifyRDFUserMetadata(id: UUID, mappingNumber: String="1") = {
    sparql.removeDatasetFromUserGraphs(id)
    get(id) match {
      case Some(dataset) => {
        import play.api.Play.current
        val theJSON = getUserMetadataJSON(id)
        val fileSep = System.getProperty("file.separator")
        val tmpDir = System.getProperty("java.io.tmpdir")
        var resultDir = tmpDir + fileSep + "medici__rdfuploadtemporaryfiles" + fileSep + new ObjectId().toString
        val resultDirFile = new java.io.File(resultDir)
        resultDirFile.mkdirs()

        if(!theJSON.replaceAll(" ","").equals("{}")){
          val xmlFile = jsonToXML(theJSON)
          new LidoToCidocConvertion(play.api.Play.configuration.getString("datasetsxmltordfmapping.dir_"+mappingNumber).getOrElse(""), xmlFile.getAbsolutePath(), resultDir)
          xmlFile.delete()
        }
        else{
          new java.io.File(resultDir + fileSep + "Results.rdf").createNewFile()
        }
        val resultFile = new java.io.File(resultDir + fileSep + "Results.rdf")

        //Connecting RDF metadata with the entity describing the original file
        val rootNodes = new ArrayList[String]()
        val rootNodesFile = play.api.Play.configuration.getString("datasetRootNodesFile").getOrElse("")
        Logger.debug(rootNodesFile)
        if(!rootNodesFile.equals("*")){
          val rootNodesReader = new BufferedReader(new FileReader(new java.io.File(rootNodesFile)))
          var line = rootNodesReader.readLine()
          while (line != null){
            Logger.debug((line == null).toString() )
            rootNodes.add(line.trim())
            line = rootNodesReader.readLine()
          }
          rootNodesReader.close()
        }

        val resultFileConnected = java.io.File.createTempFile("ResultsConnected", ".rdf")

        val fileWriter =  new BufferedWriter(new FileWriter(resultFileConnected))
        val fis = new FileInputStream(resultFile)
        val data = new Array[Byte]  (resultFile.length().asInstanceOf[Int])
        fis.read(data)
        fis.close()
        resultFile.delete()
        FileUtils.deleteDirectory(resultDirFile)
        //
        val s = new String(data, "UTF-8")
        val rdfDescriptions = s.split("<rdf:Description")
        fileWriter.write(rdfDescriptions(0))
        var i = 0
        for( i <- 1 to (rdfDescriptions.length - 1)){
          fileWriter.write("<rdf:Description" + rdfDescriptions(i))
          if(rdfDescriptions(i).contains("<rdf:type")){
            var isInRootNodes = false
            if(rootNodesFile.equals("*"))
              isInRootNodes = true
            else{
              var j = 0
              try{
                for(j <- 0 to (rootNodes.size()-1)){
                  if(rdfDescriptions(i).contains("\"" + rootNodes.get(j) + "\"")){
                    isInRootNodes = true
                    throw MustBreak
                  }
                }
              }catch {case MustBreak => }
            }

            if(isInRootNodes){
              val theResource = rdfDescriptions(i).substring(rdfDescriptions(i).indexOf("\"")+1, rdfDescriptions(i).indexOf("\"", rdfDescriptions(i).indexOf("\"")+1))
              val theHost = "http://" + play.Play.application().configuration().getString("hostIp").replaceAll("/$", "") + ":" + play.Play.application().configuration().getString("http.port")
              var connection = "<rdf:Description rdf:about=\"" + theHost +"/api/datasets/"+ id
              connection = connection	+ "\"><P129_is_about xmlns=\"http://www.cidoc-crm.org/rdfs/cidoc_crm_v5.0.2.rdfs#\" rdf:resource=\"" + theResource
              connection = connection	+ "\"/></rdf:Description>"
              fileWriter.write(connection)
            }
          }
        }
        fileWriter.close()

        sparql.addFromFile(id, resultFileConnected, "dataset")
        resultFileConnected.delete()

        sparql.addDatasetToGraph(id, "rdfCommunityGraphName")

        setUserMetadataWasModified(id, false)
      }
      case None => {}
    }
  }

  def jsonToXML(theJSON: String): java.io.File = {

    val jsonObject = new JSONObject(theJSON)
    var xml = org.json.XML.toString(jsonObject)

    Logger.debug("thexml: " + xml)

    //Remove spaces from XML tags
    var currStart = xml.indexOf("<")
    var currEnd = -1
    var xmlNoSpaces = ""
    while(currStart != -1){
      xmlNoSpaces = xmlNoSpaces + xml.substring(currEnd+1,currStart)
      currEnd = xml.indexOf(">", currStart+1)
      xmlNoSpaces = xmlNoSpaces + xml.substring(currStart,currEnd+1).replaceAll(" ", "_")
      currStart = xml.indexOf("<", currEnd+1)
    }
    xmlNoSpaces = xmlNoSpaces + xml.substring(currEnd+1)

    val xmlFile = java.io.File.createTempFile("xml",".xml")
    val fileWriter =  new BufferedWriter(new FileWriter(xmlFile))
    fileWriter.write(xmlNoSpaces)
    fileWriter.close()

    return xmlFile
  }

  def toJSON(dataset: Dataset): JsValue = {
    var datasetThumbnail = "None"
    if(!dataset.thumbnail_id.isEmpty)
      datasetThumbnail = dataset.thumbnail_id.toString().substring(5,dataset.thumbnail_id.toString().length-1)

    toJson(Map("id" -> dataset.id.toString, "datasetname" -> dataset.name, "description" -> dataset.description,
      "created" -> dataset.created.toString, "thumbnail" -> datasetThumbnail, "authorId" -> dataset.author.id.stringify))
  }

  /**
   * Return a list of tags and counts found in sections
   */
  def getTags(user: Option[User]): Map[String, Long] = {
    val filter = MongoDBObject("tags" -> MongoDBObject("$not" -> MongoDBObject("$size" -> 0)))
    var tags = scala.collection.mutable.Map[String, Long]()
    Dataset.dao.find(buildTagFilter(user) ++ filter).foreach{ x =>
      x.tags.foreach{ t =>
        tags.put(t.name, tags.get(t.name).getOrElse(0L) + 1L)
      }
    }
    tags.toMap
  }

  private def buildTagFilter(user: Option[User]): MongoDBObject = {
    val orlist = collection.mutable.ListBuffer.empty[MongoDBObject]
    if(!(configuration(play.api.Play.current).getString("permissions").getOrElse("public") == "public")){
      user match {
        case Some(u) if u.superAdminMode => orlist += MongoDBObject()
        case Some(u) if !u.superAdminMode => {
          orlist += MongoDBObject("status" -> DatasetStatus.PUBLIC.toString )
          orlist += MongoDBObject("spaces" -> List.empty) ++ MongoDBObject("author._id" -> new ObjectId(u.id.stringify))
          val okspaces = u.spaceandrole.filter(_.role.permissions.intersect(Set(Permission.ViewDataset.toString)).nonEmpty)
          if(okspaces.nonEmpty){
            orlist += ("spaces" $in okspaces.map(x => new ObjectId(x.spaceId.stringify)))
          }
        }
        case None => orlist += MongoDBObject("status" -> DatasetStatus.PUBLIC.toString )
      }
    } else {
      orlist += MongoDBObject()
    }
    $or(orlist.map(_.asDBObject))
  }

  def isInCollection(datasetId: UUID, collectionId: UUID): Boolean = {
    get(datasetId).exists(_.collections.contains(collectionId.stringify))
  }

  def updateThumbnail(datasetId: UUID, thumbnailId: UUID) {
    Dataset.dao.collection.update(MongoDBObject("_id" -> new ObjectId(datasetId.stringify)),
      $set("thumbnail_id" -> thumbnailId.stringify), false, false, WriteConcern.Safe)
  }

  def createThumbnail(datasetId: UUID) {
    get(datasetId) match {
      case Some(dataset) => {
        val filesInDataset = files.get(dataset.files).found
        for (file <- filesInDataset) {
          if (file.isInstanceOf[models.File]) {
            val theFile = file.asInstanceOf[models.File]
            if (!theFile.thumbnail_id.isEmpty) {
              Dataset.update(MongoDBObject("_id" -> new ObjectId(datasetId.stringify)), $set("thumbnail_id" -> theFile.thumbnail_id.get), false, false, WriteConcern.Safe)
              return
            }
          }
        }
        Dataset.update(MongoDBObject("_id" -> new ObjectId(datasetId.stringify)), $set("thumbnail_id" -> None), false, false, WriteConcern.Safe)
      }
      case None => Logger.debug(s"Dataset $datasetId not found")
    }
  }

  def selectNewThumbnailFromFiles(datasetId: UUID) {
    get(datasetId) match {
      case Some(dataset) => {
        // TODO cleanup
        val filesInDataset = files.get(dataset.files).found
        for (file <- filesInDataset) {
          if (file.isInstanceOf[File]) {
            val theFile = file.asInstanceOf[File]
            if (!theFile.thumbnail_id.isEmpty) {
              Dataset.update(MongoDBObject("_id" -> new ObjectId(datasetId.stringify)), $set("thumbnail_id" -> theFile.thumbnail_id.get), false, false, WriteConcern.Safe)
              return
            }
          }
        }
        Dataset.update(MongoDBObject("_id" -> new ObjectId(datasetId.stringify)), $set("thumbnail_id" -> None), false, false, WriteConcern.Safe)
      }
      case None => Logger.debug("No dataset found with id " + datasetId)
    }
  }

  def findOneByFileId(file_id: UUID): Option[Dataset] = {
    Dataset.dao.findOne(MongoDBObject("files" -> new ObjectId(file_id.stringify)))
  }

  /**
    * get dataset which contains the folder
    *
    * @param folder a Folder object
    * @return dataset containing this folder.
    */
  @tailrec final def folderPath(folder: Folder) : Option[Dataset]= {
    folder.parentType match {
      case "folder" => {
        folders.get(folder.parentId) match {
          case Some(fparent) => folderPath(fparent)
          case _ => None
        }
      }
      case "dataset" => {
        get(folder.parentDatasetId) match {
          case Some(dataset) => Some(dataset)
          case _ => None
        }
      }
      case _ => None
    }
  }

  /**
    * to get a list of datasets which directly contain the given file.
    *
    * Note: directly contain a file means that the given file is not under any folder of a dataset.
    * Warning: this function will not return the dataset if the given file is under a folder of this dataset.
    *
    * @param file_id file id of a given file
    * @return a list of dataset objects with option type
    */
  def findByFileIdDirectlyContain(file_id: UUID): List[Dataset] = {
    Dataset.dao.find(MongoDBObject("files" -> new ObjectId(file_id.stringify))).toList
  }

  /**
    * to get a list of datasets which contain the given file.
    *
    * Note: this function will return back a list of dataset, where a given file is either under a dataset directly or
    * under a folder of a dataset.
    *
    * This function will first get a list of dataset which directly contain a given file.
    * Then to get a dataset which indirectly contain a given file,
    * it will traverse back on the hierarchical paths from the folder of that give file to a dataset.
    *
    * @param file_id  file id of a given file
    * @return a list of dataset objects
    */
  def findByFileIdAllContain(file_id: UUID): List[Dataset] = {
    //1. get list of datasets which directly contains this file.
    val datasetList = findByFileIdDirectlyContain(file_id)
    //2. get list of datasets which indirectly contains this file.
    val foldersContainingFile = folders.findByFileId(file_id)
    (datasetList ::: (for(folder <- foldersContainingFile) yield (folderPath(folder))).flatten).distinct
  }

  def findNotContainingFile(file_id: UUID): List[Dataset] = {
    val listContaining = findByFileIdDirectlyContain(file_id)
    (for (dataset <- Dataset.find(MongoDBObject())) yield dataset).toList.filterNot(listContaining.toSet)
  }

  def findByTag(tag: String, user: Option[User]): List[Dataset] = {
    if(configuration(play.api.Play.current).getString("permissions").getOrElse("public") == "public"){
      Dataset.dao.find(MongoDBObject("tags.name" -> tag)).toList
    } else {
      Dataset.dao.find(buildTagFilter(user) ++
        MongoDBObject("tags.name" -> tag)).toList
    }
  }

  def findByTag(tag: String, start: String, limit: Integer, reverse: Boolean, user: Option[User]): List[Dataset] = {
    var filter = if (start == "") {
      MongoDBObject("tags.name" -> tag)
    } else {
      if (reverse) {
        MongoDBObject("tags.name" -> tag) ++ ("created" $gte Parsers.fromISO8601(start))
      } else {
        MongoDBObject("tags.name" -> tag) ++ ("created" $lte Parsers.fromISO8601(start))
      }
    }
    if(!(configuration(play.api.Play.current).getString("permissions").getOrElse("public") == "public")) {
      filter = buildTagFilter(user) ++ filter
    }
    val order = if (reverse) {
      MongoDBObject("created" -> -1, "name" -> 1)
    } else {
      MongoDBObject("created" -> 1, "name" -> 1)
    }
    Dataset.dao.find(filter).sort(order).limit(limit).toList
  }

  def getMetadata(id: UUID): Map[String, Any] = {
    Dataset.dao.collection.findOne(MongoDBObject("_id" -> new ObjectId(id.stringify)), MongoDBObject("metadata" -> 1)) match {
      case None => Map.empty
      case Some(x) => {
        x.getAs[DBObject]("metadata").get.toMap.asScala.asInstanceOf[scala.collection.mutable.Map[String, Any]].toMap
      }
    }
  }

  def getUserMetadata(id: UUID): scala.collection.mutable.Map[String, Any] = {
    Dataset.dao.collection.findOne(MongoDBObject("_id" -> new ObjectId(id.stringify)), MongoDBObject("userMetadata" -> 1)) match {
      case None => new scala.collection.mutable.HashMap[String, Any]
      case Some(x) => {
        val returnedMetadata = x.getAs[DBObject]("userMetadata").get.toMap.asScala.asInstanceOf[scala.collection.mutable.Map[String, Any]]
        returnedMetadata
      }
    }
  }

  def getUserMetadataJSON(id: UUID): String = {
    Dataset.dao.collection.findOneByID(new ObjectId(id.stringify)) match {
      case None => "{}"
      case Some(x) => {
        x.getAs[DBObject]("userMetadata") match {
          case Some(y) => {
            val returnedMetadata = com.mongodb.util.JSON.serialize(x.getAs[DBObject]("userMetadata").get)
            Logger.debug("retmd: " + returnedMetadata)
            returnedMetadata
          }
          case None => "{}"
        }
      }
    }
  }

  def getTechnicalMetadataJSON(id: UUID): String = {
    Dataset.dao.collection.findOneByID(new ObjectId(id.stringify)) match {
      case None => "{}"
      case Some(x) => {
        x.getAs[DBObject]("metadata") match {
          case Some(y) => {
            val returnedMetadata = com.mongodb.util.JSON.serialize(x.getAs[DBObject]("metadata").get)
            Logger.debug("retmd: " + returnedMetadata)
            returnedMetadata
          }
          case None => "{}"
        }
      }
    }
  }

  def getXMLMetadataJSON(id: UUID): String = {
    Dataset.dao.collection.findOneByID(new ObjectId(id.stringify)) match {
      case None => "{}"
      case Some(x) => {
        x.getAs[DBObject]("datasetXmlMetadata") match {
          case Some(y) => {
            val returnedMetadata = JSON.serialize(x.getAs[DBObject]("datasetXmlMetadata").get)
            Logger.debug("retmd: " + returnedMetadata)
            returnedMetadata
          }
          case None => "{}"
        }
      }
    }
  }

  def addMetadata(id: UUID, json: String) {
    Logger.debug(s"Adding metadata to dataset " + id + " : " + json)
    val md = JSON.parse(json).asInstanceOf[DBObject]
    Dataset.dao.collection.findOne(MongoDBObject("_id" -> new ObjectId(id.stringify)), MongoDBObject("metadata" -> 1)) match {
      case None => {
        Dataset.update(MongoDBObject("_id" -> new ObjectId(id.stringify)), $set("metadata" -> md), false, false, WriteConcern.Safe)
      }
      case Some(x) => {
        x.getAs[DBObject]("metadata") match {
          case Some(map) => {
            val union = map.asInstanceOf[DBObject] ++ md
            Dataset.update(MongoDBObject("_id" -> new ObjectId(id.stringify)), $set("metadata" -> union), false, false, WriteConcern.Safe)
          }
          case None => Map.empty
        }
      }
    }
  }

  def addXMLMetadata(id: UUID, fileId: UUID, json: String) {
    Logger.debug("Adding XML metadata to dataset " + id + " from file " + fileId + ": " + json)
    val md = JsonUtil.parseJSON(json).asInstanceOf[java.util.LinkedHashMap[String, Any]].toMap
    Dataset.update(MongoDBObject("_id" -> new ObjectId(id.stringify)),
      $addToSet("datasetXmlMetadata" -> DatasetXMLMetadata.toDBObject(models.DatasetXMLMetadata(md, fileId.stringify))), false, false, WriteConcern.Safe)
  }

  def removeXMLMetadata(id: UUID, fileId: UUID) {
    Logger.debug("Removing XML metadata belonging to file " + fileId + " from dataset " + id + ".")
    Dataset.update(MongoDBObject("_id" -> new ObjectId(id.stringify)), $pull("datasetXmlMetadata" -> MongoDBObject("fileId" -> fileId.stringify)), false, false, WriteConcern.Safe)
  }

  def addUserMetadata(id: UUID, json: String) {
    Logger.debug("Adding/modifying user metadata to dataset " + id + " : " + json)
    val md = com.mongodb.util.JSON.parse(json).asInstanceOf[DBObject]
    Dataset.update(MongoDBObject("_id" -> new ObjectId(id.stringify)), $set("userMetadata" -> md), false, false, WriteConcern.Safe)
  }

  /** Change the metadataCount field for a dataset */
  def incrementMetadataCount(id: UUID, count: Long) = {
    Dataset.update(MongoDBObject("_id" -> new ObjectId(id.stringify)), $inc("metadataCount" -> count), false, false, WriteConcern.Safe)
  }

  /**
   * Implementation of updateInformation defined in services/DatasetService.scala.
   */
  def updateInformation(id: UUID, description: String, name: String) {
      val result = Dataset.update(MongoDBObject("_id" -> new ObjectId(id.stringify)),
          $set("description" -> description, "name" -> name),
          false, false, WriteConcern.Safe)
  }

  def updateName(id: UUID, name: String) {
    events.updateObjectName(id, name)
    val result = Dataset.update(MongoDBObject("_id" -> new ObjectId(id.stringify)),
      $set("name" -> name),
      false, false, WriteConcern.Safe)
  }

  def updateDescription(id: UUID, description: String){
    val result = Dataset.update(MongoDBObject("_id" -> new ObjectId(id.stringify)),
      $set("description" -> description),
      false, false, WriteConcern.Safe)
  }

  /**
   * Add a creator to the end of the dataset's list of creators
   */
  def addCreator(id: UUID, creator: String) {
    //Don't allow duplicates
    if (Dataset.dao.find(MongoDBObject("_id" -> new ObjectId(id.stringify)) ++ MongoDBObject("creators" -> creator)).length == 0) {
      val result = Dataset.update(MongoDBObject("_id" -> new ObjectId(id.stringify)),
        $push("creators" -> creator),
        false, false, WriteConcern.Safe)
    }
  }

  /**
   * Remove a creator from the dataset's list of creators
   */
  def removeCreator(id: UUID, creator: String) {
    Dataset.dao.update(MongoDBObject("_id" -> new ObjectId(id.stringify)),
      $pull("creators" -> creator), false, false, WriteConcern.Safe)
  }

  /**
   * Move a creator to a new position in the dataset's list of creators
   */
  def moveCreator(id: UUID, creator: String, position: Integer) {
    //Don't move ones that don't exist
    if (Dataset.dao.find(MongoDBObject("_id" -> new ObjectId(id.stringify)) ++ MongoDBObject("creators" -> creator)).length != 0) {
      removeCreator(id, creator);
      Dataset.update(MongoDBObject("_id" -> new ObjectId(id.stringify)),
        $push("creators" -> MongoDBObject("$each" -> MongoDBList(creator), "$position" -> position)),
        false, false, WriteConcern.Safe)
    }
  }

  def updateAuthorFullName(userId: UUID, fullName: String) {
    Dataset.update(MongoDBObject("author._id" -> new ObjectId(userId.stringify)),
      $set("author.fullName" -> fullName), false, true, WriteConcern.Safe)
  }

  /**
   * Implementation of updateLicenseing defined in services/DatasetService.scala.
   */
  def updateLicense(id: UUID, licenseType: String, rightsHolder: String, licenseText: String, licenseUrl: String, allowDownload: String) {
      val licenseData = models.LicenseData(m_licenseType = licenseType, m_rightsHolder = rightsHolder, m_licenseText = licenseText, m_licenseUrl = licenseUrl, m_allowDownload = allowDownload.toBoolean)
      val result = Dataset.update(MongoDBObject("_id" -> new ObjectId(id.stringify)),
          $set("licenseData" -> LicenseData.toDBObject(licenseData)),
          false, false, WriteConcern.Safe)
  }

  def addTags(id: UUID, userIdStr: Option[String], eid: Option[String], tags: List[String]) : List[Tag] = {
    Logger.debug("Adding tags to dataset " + id + " : " + tags)
    // TODO: Need to check for the owner of the dataset before adding tag
    var tagsAdded : ListBuffer[Tag] = ListBuffer.empty[Tag]

    val dataset = get(id).get
    val existingTags = dataset.tags.filter(x => userIdStr == x.userId && eid == x.extractor_id).map(_.name)
    val createdDate = new Date
    val maxTagLength = play.api.Play.configuration.getInt("clowder.tagLength").getOrElse(100)
    tags.foreach(tag => {
      val shortTag = if (tag.length > maxTagLength) {
        Logger.error("Tag is truncated to " + maxTagLength + " chars : " + tag)
        tag.substring(0, maxTagLength)
      } else {
        tag
      }
      // Only add tags with new values.
      if (!existingTags.contains(shortTag)) {
        val tagObj = models.Tag(name = shortTag, userId = userIdStr, extractor_id = eid, created = createdDate)
        tagsAdded += tagObj
        Dataset.update(MongoDBObject("_id" -> new ObjectId(id.stringify)), $addToSet("tags" -> Tag.toDBObject(tagObj)), false, false, WriteConcern.Safe)
      }
    })
    tagsAdded.toList
  }

  def setUserMetadataWasModified(id: UUID, wasModified: Boolean) {
    Dataset.update(MongoDBObject("_id" -> new ObjectId(id.stringify)), $set("userMetadataWasModified" -> Some(wasModified)), false, false, WriteConcern.Safe)
  }

  def findMetadataChangedDatasets(): List[Dataset] = {
    Dataset.find(MongoDBObject("userMetadataWasModified" -> true)).toList
  }

  def removeTag(id: UUID, tagId: UUID) {
    Logger.debug("Removing tag " + tagId)
    val result = Dataset.update(MongoDBObject("_id" -> new ObjectId(id.stringify)), $pull("tags" -> MongoDBObject("_id" -> new ObjectId(tagId.stringify))), false, false, WriteConcern.Safe)
  }

  def removeTags(id: UUID, userIdStr: Option[String], eid: Option[String], tags: List[String]) {
    Logger.debug("Removing tags in dataset " + id + " : " + tags + ", userId: " + userIdStr + ", eid: " + eid)
    val dataset = get(id).get
    val existingTags = dataset.tags.filter(x => userIdStr == x.userId && eid == x.extractor_id).map(_.name)
    Logger.debug("existingTags before user and extractor filtering: " + existingTags.toString)
    // Only remove existing tags.
    tags.intersect(existingTags).map {
      tag =>
        Dataset.update(MongoDBObject("_id" -> new ObjectId(id.stringify)), $pull("tags" -> MongoDBObject("name" -> tag)), false, false, WriteConcern.Safe)
    }
  }

  def removeAllTags(id: UUID) {
    Dataset.update(MongoDBObject("_id" -> new ObjectId(id.stringify)), $set("tags" -> List()), false, false, WriteConcern.Safe)
  }

  // ---------- Tags related code ends ------------------

  /**
   * Check recursively whether a dataset's user-input metadata match a requested search tree.
   */
  def searchUserMetadata(id: UUID, requestedMetadataQuery: Any): Boolean = {
    return searchMetadata(id, requestedMetadataQuery.asInstanceOf[java.util.LinkedHashMap[String, Any]], getUserMetadata(id))
  }

  def searchAllMetadataFormulateQuery(requestedMetadataQuery: Any): List[Dataset] = {
    Logger.debug("top: " + requestedMetadataQuery.asInstanceOf[java.util.LinkedHashMap[String, Any]].toString())
    var theQuery = searchMetadataFormulateQuery(requestedMetadataQuery.asInstanceOf[java.util.LinkedHashMap[String, Any]], "all")
    Logger.debug("thequery: " + theQuery.toString)
    Dataset.find(theQuery).toList
  }

  def searchUserMetadataFormulateQuery(requestedMetadataQuery: Any): List[Dataset] = {
    Logger.debug("top: " + requestedMetadataQuery.asInstanceOf[java.util.LinkedHashMap[String, Any]].toString())
    var theQuery = searchMetadataFormulateQuery(requestedMetadataQuery.asInstanceOf[java.util.LinkedHashMap[String, Any]], "userMetadata")
    Logger.debug("thequery: " + theQuery.toString)
    Dataset.find(theQuery).toList
  }

  def searchMetadataFormulateQuery(requestedMap: java.util.LinkedHashMap[String, Any], root: String): MongoDBObject = {
    Logger.debug("req: " + requestedMap)
    var queryMap = MongoDBList()
    var builder = MongoDBList()
    var orFound = false
    for ((reqKey, reqValue) <- requestedMap) {
      val keyTrimmed = reqKey.replaceAll("__[0-9]+$", "")

      if (keyTrimmed.equals("OR")) {
        queryMap.add(MongoDBObject("$and" -> builder))
        builder = MongoDBList()
        orFound = true
      }
      else {
        var actualKey = keyTrimmed
        if (keyTrimmed.endsWith("__not")) {
          actualKey = actualKey.substring(0, actualKey.length() - 5)
        }

        if (!root.equals("all")) {

          if (!root.equals(""))
            actualKey = root + "." + actualKey

          if (reqValue.isInstanceOf[String]) {
            val currValue = reqValue.asInstanceOf[String]
            if (keyTrimmed.endsWith("__not")) {
              if(currValue.contains(" IGNORE CASE") || currValue.contains(" ANYWHERE")){
                var realValue = currValue.replace(" IGNORE CASE", "").replace(" ANYWHERE", "");
                if(!currValue.contains(" ANYWHERE")){
                  realValue = "^"+realValue+"$";
                }
                if(currValue.contains(" IGNORE CASE")){
                  realValue = "(?i)"+realValue;
                }
                builder += MongoDBObject(actualKey -> MongoDBObject("$not" ->  realValue.r))
              }
              else{
                builder += MongoDBObject(actualKey -> MongoDBObject("$ne" ->  currValue))
              }
            }
            else {
              if(currValue.contains(" IGNORE CASE") || currValue.contains(" ANYWHERE")){
                var realValue = currValue.replace(" IGNORE CASE", "").replace(" ANYWHERE", "");
                if(!currValue.contains(" ANYWHERE")){
                  realValue = "^"+realValue+"$";
                }
                if(currValue.contains(" IGNORE CASE")){
                  realValue = "(?i)"+realValue;
                }
                builder += MongoDBObject(actualKey -> realValue.r)
              }
              else{
                builder += MongoDBObject(actualKey -> currValue)
              }
            }
          } else {
            //recursive
            if (root.equals("userMetadata")) {
              val currValue = searchMetadataFormulateQuery(reqValue.asInstanceOf[java.util.LinkedHashMap[String, Any]], "")
              val elemMatch = actualKey $elemMatch currValue
              builder.add(elemMatch)
            }
            else {
              val currValue = searchMetadataFormulateQuery(reqValue.asInstanceOf[java.util.LinkedHashMap[String, Any]], actualKey)
              builder += currValue
            }
          }
        } else {
          var objectForEach = MongoDBList()
          val allRoots = Map(1 -> "userMetadata", 2 -> "metadata", 3 -> "datasetXmlMetadata.xmlMetadata")
          allRoots.keys.foreach {
            i =>
              var tempActualKey = allRoots(i) + "." + actualKey

              if (reqValue.isInstanceOf[String]) {
                val currValue = reqValue.asInstanceOf[String]
                if (keyTrimmed.endsWith("__not")) {
                  if(currValue.contains(" IGNORE CASE") || currValue.contains(" ANYWHERE")){
	                var realValue = currValue.replace(" IGNORE CASE", "").replace(" ANYWHERE", "");
	                if(!currValue.contains(" ANYWHERE")){
	                  realValue = "^"+realValue+"$";
	                }
	                if(currValue.contains(" IGNORE CASE")){
	                  realValue = "(?i)"+realValue;
	                }
	                objectForEach += MongoDBObject(tempActualKey -> MongoDBObject("$not" ->  realValue.r))
                  }
                  else{
                	objectForEach += MongoDBObject(tempActualKey -> MongoDBObject("$ne" ->  currValue))
                  }
                }
                else {
                  if(currValue.contains(" IGNORE CASE") || currValue.contains(" ANYWHERE")){
	                var realValue = currValue.replace(" IGNORE CASE", "").replace(" ANYWHERE", "");
	                if(!currValue.contains(" ANYWHERE")){
	                  realValue = "^"+realValue+"$";
	                }
	                if(currValue.contains(" IGNORE CASE")){
	                  realValue = "(?i)"+realValue;
	                }
	                objectForEach += MongoDBObject(tempActualKey -> realValue.r)
                  }
                  else{
                	objectForEach += MongoDBObject(tempActualKey -> currValue)
                  }
                }
              } else {
                //recursive
                if (allRoots(i).equals("userMetadata")) {
                  val currValue = searchMetadataFormulateQuery(reqValue.asInstanceOf[java.util.LinkedHashMap[String, Any]], "")
                  val elemMatch = tempActualKey $elemMatch currValue
                  objectForEach.add(elemMatch)
                }
                else {
                  val currValue = searchMetadataFormulateQuery(reqValue.asInstanceOf[java.util.LinkedHashMap[String, Any]], tempActualKey)
                  objectForEach += currValue
                }
              }
          }

          builder.add(MongoDBObject("$or" -> objectForEach))

        }
      }
    }

    if (orFound) {
      queryMap.add(MongoDBObject("$and" -> builder))
      return MongoDBObject("$or" -> queryMap)
    }
    else if (!builder.isEmpty) {
      return MongoDBObject("$and" -> builder)
    }
    else if (!root.equals("")) {
      return (root $exists true)
    }
    else {
      return new MongoDBObject()
    }
  }


  /**
   * Check recursively whether a (sub)tree of a dataset's metadata matches a requested search subtree.
   */
  def searchMetadata(id: UUID, requestedMap: java.util.LinkedHashMap[String, Any], currentMap: scala.collection.mutable.Map[String, Any]): Boolean = {
    var allMatch = true
    Logger.debug("req: " + requestedMap);
    Logger.debug("curr: " + currentMap);
    for ((reqKey, reqValue) <- requestedMap) {
      var reqKeyCompare = reqKey
      if (reqKeyCompare.equals("OR")) {
        if (allMatch)
          return true
        else
          allMatch = true
      }
      else {
        if (allMatch) {
          var isNot = false
          if (reqKeyCompare.endsWith("__not")) {
            isNot = true
            reqKeyCompare = reqKeyCompare.dropRight(5)
          }
          var matchFound = false
          try {
            for ((currKey, currValue) <- currentMap) {
              val currKeyCompare = currKey
              if (reqKeyCompare.equals(currKeyCompare)) {
                //If search subtree remaining is a string (ie we have reached a leaf), then remaining subtree currently examined is bound to be a string, as the path so far was the same.
                //Therefore, we do string comparison.
                if (reqValue.isInstanceOf[String]) {
                  if (currValue.isInstanceOf[com.mongodb.BasicDBList]) {
                    for (itemInCurrValue <- currValue.asInstanceOf[com.mongodb.BasicDBList]) {
                      if (reqValue.asInstanceOf[String].trim().equals("*") || reqValue.asInstanceOf[String].trim().equalsIgnoreCase(itemInCurrValue.asInstanceOf[String].trim())) {
                        matchFound = true
                        throw MustBreak
                      }
                    }
                  }
                  else {
                    if (reqValue.asInstanceOf[String].trim().equals("*") || reqValue.asInstanceOf[String].trim().equalsIgnoreCase(currValue.asInstanceOf[String].trim())) {
                      matchFound = true
                    }
                  }
                }
                //If search subtree remaining is not a string (ie we haven't reached a leaf yet), then remaining subtree currently examined is bound to not be a string, as the path so far was the same.
                //Therefore, we do maps (actually subtrees) comparison.
                else {
                  if (currValue.isInstanceOf[com.mongodb.BasicDBList]) {
                    for (itemInCurrValue <- currValue.asInstanceOf[com.mongodb.BasicDBList]) {
                      val currValueMap = itemInCurrValue.asInstanceOf[com.mongodb.BasicDBObject].toMap().asScala.asInstanceOf[scala.collection.mutable.Map[String, Any]]
                      if (searchMetadata(id, reqValue.asInstanceOf[java.util.LinkedHashMap[String, Any]], currValueMap)) {
                        matchFound = true
                        throw MustBreak
                      }
                    }
                  }
                  else {
                    val currValueMap = currValue.asInstanceOf[com.mongodb.BasicDBObject].toMap().asScala.asInstanceOf[scala.collection.mutable.Map[String, Any]]
                    if (searchMetadata(id, reqValue.asInstanceOf[java.util.LinkedHashMap[String, Any]], currValueMap)) {
                      matchFound = true
                    }
                  }
                }

                throw MustBreak
              }
            }
          } catch {
            case MustBreak =>
          }
          if (isNot)
            matchFound = !matchFound
          if (!matchFound)
            allMatch = false
        }
      }
    }
    return allMatch
  }

  def addFile(datasetId: UUID, file: File) {
    Dataset.update(MongoDBObject("_id" -> new ObjectId(datasetId.stringify)), $addToSet("files" -> new ObjectId(file.id.stringify)), false, false, WriteConcern.Safe)
  }

  def addFolder(datasetId: UUID, folderId: UUID) {
    Dataset.update(MongoDBObject("_id" -> new ObjectId(datasetId.stringify)), $addToSet("folders" -> new ObjectId(folderId.stringify)), false, false, WriteConcern.Safe)
  }

  def addCollection(datasetId: UUID, collectionId: UUID) {
    Dataset.update(MongoDBObject("_id" -> new ObjectId(datasetId.stringify)), $addToSet("collections" -> new ObjectId(collectionId.stringify)), false, false, WriteConcern.Safe)
  }

  def removeCollection(datasetId: UUID, collectionId: UUID) {
    Dataset.update(MongoDBObject("_id" -> new ObjectId(datasetId.stringify)), $pull("collections" -> new ObjectId(collectionId.stringify)), false, false, WriteConcern.Safe)
  }

  def removeFile(datasetId: UUID, fileId: UUID) {
    Dataset.update(MongoDBObject("_id" -> new ObjectId(datasetId.stringify)), $pull("files" -> new ObjectId(fileId.stringify)), false, false, WriteConcern.Safe)
    removeXMLMetadata(datasetId, fileId)
  }

  def removeFolder(datasetId: UUID, folderId: UUID) {
    Dataset.update(MongoDBObject("_id" -> new ObjectId(datasetId.stringify)), $pull("folders" -> new ObjectId(folderId.stringify)), false, false, WriteConcern.Safe)
  }

  def newThumbnail(datasetId: UUID) {
    Dataset.findOneById(new ObjectId(datasetId.stringify)) match {
      case Some(dataset) => {
        val filesInDataset = files.get(dataset.files).found
        for (file <- filesInDataset) {
          if (file.isInstanceOf[models.File]) {
            val theFile = file.asInstanceOf[models.File]
            if (!theFile.thumbnail_id.isEmpty) {
              Dataset.update(MongoDBObject("_id" -> new ObjectId(datasetId.stringify)), $set("thumbnail_id" -> theFile.thumbnail_id.get), false, false, WriteConcern.Safe)
              return
            }
          }
        }
        Dataset.update(MongoDBObject("_id" -> new ObjectId(datasetId.stringify)), $set("thumbnail_id" -> None), false, false, WriteConcern.Safe)
      }
      case None =>
    }
  }

  def removeDataset(id: UUID, host: String, apiKey: Option[String], user: Option[User]) {
    Dataset.findOneById(new ObjectId(id.stringify)) match {
      case Some(dataset) => {
        dataset.collections.foreach(c => collections.removeDataset(c, dataset.id))
        for (comment <- comments.findCommentsByDatasetId(id)) {
          comments.removeComment(comment)
        }
        for (f <- dataset.files) {
          val notTheDataset = for (currDataset <- findByFileIdDirectlyContain(f) if !dataset.id.toString.equals(currDataset.id.toString)) yield currDataset
          if (notTheDataset.size == 0)
            files.removeFile(f, host, apiKey, user)
        }
        for (folder <- dataset.folders ) {
          folders.delete(folder, host, apiKey, user)
        }
        for (follower <- dataset.followers) {
          userService.unfollowDataset(follower, id)
        }
        for(space <- dataset.spaces) {
          spaces.removeDataset(dataset.id, space)
        }
        metadatas.removeMetadataByAttachTo(ResourceRef(ResourceRef.dataset, id), host, apiKey, user)
        Dataset.remove(MongoDBObject("_id" -> new ObjectId(dataset.id.stringify)))
        appConfig.incrementCount('datasets, -1)
        current.plugin[ElasticsearchPlugin].foreach {
          _.delete(id.stringify)
        }
      }
      case None =>
    }
  }

  def indexAll() = {
    // Bypass Salat in case any of the file records are malformed to continue past them
    Dataset.dao.collection.find(MongoDBObject(), MongoDBObject("_id" -> 1)).foreach(d => {
      index(new UUID(d.get("_id").toString))
    })
  }

  def index(id: UUID) {
    try
      esqueue.queue("index_dataset", new ResourceRef('dataset, id))
    catch {
      case except: Throwable => Logger.error(s"Error queuing dataset ${id.stringify}: ${except}")
      case _ => Logger.error(s"Error queuing dataset ${id.stringify}")
    }
  }

  def addToSpace(datasetId: UUID, spaceId: UUID): Unit = {
    val result = Dataset.update(
      MongoDBObject("_id" -> new ObjectId(datasetId.stringify)),
      $addToSet("spaces" -> Some(new ObjectId(spaceId.stringify))),
      false, false)
    if (get(datasetId).exists(_.isTRIAL == true) && spaces.get(spaceId).exists(_.isTrial == false)) {
      Dataset.update(MongoDBObject("_id" -> new ObjectId(datasetId.stringify)),
        $set("status" -> DatasetStatus.DEFAULT.toString),
        false, false)
    }
  }

  def removeFromSpace(datasetId: UUID, spaceId: UUID): Unit = {
    val result = Dataset.update(
      MongoDBObject("_id" -> new ObjectId(datasetId.stringify)),
      $pull("spaces" -> Some(new ObjectId(spaceId.stringify))),
      false, false)

    if (play.Play.application().configuration().getBoolean("verifySpaces")) {

      get(datasetId) match {
        case Some(d) if !d.spaces.map(s => spaces.get(s)).flatten.exists(_.isTrial == false) =>
          Dataset.update(MongoDBObject("_id" -> new ObjectId(datasetId.stringify)),
            $set("status" -> DatasetStatus.TRIAL.toString),
            false, false)
        case _ =>
      }
    }
  }


  def dumpAllDatasetMetadata(): List[String] = {
		    Logger.debug("Dumping metadata of all datasets.")

		    val fileSep = System.getProperty("file.separator")
		    val lineSep = System.getProperty("line.separator")
		    var dsMdDumpDir = play.api.Play.configuration.getString("datasetdump.dir").getOrElse("")
			if(!dsMdDumpDir.endsWith(fileSep))
				dsMdDumpDir = dsMdDumpDir + fileSep
			var dsMdDumpMoveDir = play.api.Play.configuration.getString("datasetdumpmove.dir").getOrElse("")
			if(dsMdDumpMoveDir.equals("")){
				Logger.warn("Will not move dumped datasets metadata to staging directory. No staging directory set.")
			}
			else{
			    if(!dsMdDumpMoveDir.endsWith(fileSep))
				  dsMdDumpMoveDir = dsMdDumpMoveDir + fileSep
			}

			var unsuccessfulDumps: ListBuffer[String] = ListBuffer.empty

			for(dataset <- Dataset.findAll){
			  try{
				  val dsId = dataset.id.toString

				  val dsTechnicalMetadata = getTechnicalMetadataJSON(dataset.id)
				  val dsUserMetadata = getUserMetadataJSON(dataset.id)
				  val dsXMLMetadata = getXMLMetadataJSON(dataset.id)
				  if(dsTechnicalMetadata != "{}" || dsUserMetadata != "{}" || dsXMLMetadata != "{}"){

				    val datasetnameNoSpaces = dataset.name.replaceAll("\\s+","_")
				    val filePathInDirs = dsId.charAt(dsId.length()-3)+ fileSep + dsId.charAt(dsId.length()-2)+dsId.charAt(dsId.length()-1)+ fileSep + dsId + fileSep + datasetnameNoSpaces + "__metadata.txt"
				    val mdFile = new java.io.File(dsMdDumpDir + filePathInDirs)
				    mdFile.getParentFile().mkdirs()

				    val fileWriter =  new BufferedWriter(new FileWriter(mdFile))
					fileWriter.write(dsTechnicalMetadata + lineSep + lineSep + dsUserMetadata + lineSep + lineSep + dsXMLMetadata)
					fileWriter.close()

					if(!dsMdDumpMoveDir.equals("")){
					  try{
						  val mdMoveFile = new java.io.File(dsMdDumpMoveDir + filePathInDirs)
					      mdMoveFile.getParentFile().mkdirs()

						  if(mdFile.renameTo(mdMoveFile)){
			            	Logger.debug("Dataset metadata dumped and moved to staging directory successfully.")
						  }else{
			            	Logger.warn("Could not move dumped dataset metadata to staging directory.")
			            	throw new Exception("Could not move dumped dataset metadata to staging directory.")
						  }
					  }catch {case ex:Exception =>{
						  val badDatasetId = dataset.id.toString
						  Logger.error("Unable to stage dumped metadata of dataset with id "+badDatasetId+": "+ex.printStackTrace())
						  unsuccessfulDumps += badDatasetId
					  }}
					}

				  }
			  }catch {case ex:Exception =>{
			    val badDatasetId = dataset.id.toString
			    Logger.error("Unable to dump metadata of dataset with id "+badDatasetId+": "+ex.printStackTrace())
			    unsuccessfulDumps += badDatasetId
			  }}
			}

		    return unsuccessfulDumps.toList
	}

  def dumpAllDatasetGroupings(): List[String] = {

		    Logger.debug("Dumping dataset groupings of all datasets.")

		    val fileSep = System.getProperty("file.separator")
		    val lineSep = System.getProperty("line.separator")
		    var datasetsDumpDir = play.api.Play.configuration.getString("datasetdump.dir").getOrElse("")
			if(!datasetsDumpDir.endsWith(fileSep))
				datasetsDumpDir = datasetsDumpDir + fileSep
			var dsDumpMoveDir = play.api.Play.configuration.getString("datasetdumpmove.dir").getOrElse("")
			if(dsDumpMoveDir.equals("")){
				Logger.warn("Will not move dumped dataset groupings to staging directory. No staging directory set.")
			}
			else{
			    if(!dsDumpMoveDir.endsWith(fileSep))
				  dsDumpMoveDir = dsDumpMoveDir + fileSep
			}

			var unsuccessfulDumps: ListBuffer[String] = ListBuffer.empty

			for(dataset <- Dataset.findAll){
			  try{
				  val dsId = dataset.id.toString
				  val datasetnameNoSpaces = dataset.name.replaceAll("\\s+","_")
				  val filePathInDirs = dsId.charAt(dsId.length()-3)+ fileSep + dsId.charAt(dsId.length()-2)+dsId.charAt(dsId.length()-1)+ fileSep + dsId + fileSep + datasetnameNoSpaces + ".txt"

				  val groupingFile = new java.io.File(datasetsDumpDir + filePathInDirs)
				  groupingFile.getParentFile().mkdirs()

				  val filePrintStream =  new PrintStream(groupingFile)
          files.get(dataset.files).found.foreach(file => {
            filePrintStream.println("id:"+file.id.toString+" "+"filename:"+file.filename)
          })
				  filePrintStream.close()

				  if(!dsDumpMoveDir.equals("")){
					  try{
						  val groupingMoveFile = new java.io.File(dsDumpMoveDir + filePathInDirs)
					      groupingMoveFile.getParentFile().mkdirs()

						  if(groupingFile.renameTo(groupingMoveFile)){
			            	Logger.debug("Dataset file grouping dumped and moved to staging directory successfully.")
						  }else{
			            	Logger.warn("Could not move dumped dataset file grouping to staging directory.")
			            	throw new Exception("Could not move dumped dataset file grouping to staging directory.")
						  }
					  }catch {case ex:Exception =>{
						  val badDatasetId = dataset.id.toString
						  Logger.error("Unable to stage file grouping of dataset with id "+badDatasetId+": "+ex.printStackTrace())
						  unsuccessfulDumps += badDatasetId
					  }}
					}
			  }catch {case ex:Exception =>{
			    val badDatasetId = dataset.id.toString
			    Logger.error("Unable to dump file grouping of dataset with id "+badDatasetId+": "+ex.printStackTrace())
			    unsuccessfulDumps += badDatasetId
			  }}
			}

		    return unsuccessfulDumps.toList
	}

  def addFollower(id: UUID, userId: UUID) {
    Dataset.dao.update(MongoDBObject("_id" -> new ObjectId(id.stringify)),
                    $addToSet("followers" -> new ObjectId(userId.stringify)), false, false, WriteConcern.Safe)
  }

  def removeFollower(id: UUID, userId: UUID) {
    Dataset.dao.update(MongoDBObject("_id" -> new ObjectId(id.stringify)),
                    $pull("followers" -> new ObjectId(userId.stringify)), false, false, WriteConcern.Safe)
  }

  def incrementViews(id: UUID, user: Option[User]): (Int, Date) = {
    Logger.debug("updating views for dataset "+id.toString)
    val viewdate = new Date

    val updated = Dataset.dao.collection.findAndModify(
      query=MongoDBObject("_id" -> new ObjectId(id.stringify)),
      update=$inc("stats.views" -> 1) ++ $set("stats.last_viewed" -> viewdate),
      upsert=true, fields=null, sort=null, remove=false, returnNew=true)

    user match {
      case Some(u) => {
        Logger.debug("updating views for user "+u.toString)
        DatasetStats.update(MongoDBObject("user_id" -> new ObjectId(u.id.stringify), "resource_id" -> new ObjectId(id.stringify), "resource_type" -> "dataset"),
          $inc("views" -> 1) ++ $set("last_viewed" -> viewdate), true, false, WriteConcern.Safe)
      }
      case None => {}
    }

    // Return updated count
    return updated match {
      case Some(u) => (u.get("stats").asInstanceOf[BasicDBObject].get("views").asInstanceOf[Int], viewdate)
      case None => (0, viewdate)
    }
  }

  def incrementDownloads(id: UUID, user: Option[User]) = {
    Logger.debug("updating downloads for dataset "+id.toString)
    Dataset.update(MongoDBObject("_id" -> new ObjectId(id.stringify)),
      $inc("stats.downloads" -> 1) ++ $set("stats.last_downloaded" -> new Date), true, false, WriteConcern.Safe)

    user match {
      case Some(u) => {
        Logger.debug("updating downloads for user "+u.toString)
        DatasetStats.update(MongoDBObject("user_id" -> new ObjectId(u.id.stringify), "resource_id" -> new ObjectId(id.stringify), "resource_type" -> "dataset"),
          $inc("downloads" -> 1) ++ $set("last_downloaded" -> new Date), true, false, WriteConcern.Safe)
      }
      case None => {}
    }
  }

  def getMetrics(): Iterator[Dataset] = {
    Dataset.find(MongoDBObject("trash" -> false)).toIterator
  }
}

object Dataset extends ModelCompanion[Dataset, ObjectId] {
  val dao = current.plugin[MongoSalatPlugin] match {
    case None => throw new RuntimeException("No MongoSalatPlugin");
    case Some(x) => new SalatDAO[Dataset, ObjectId](collection = x.collection("datasets")) {}
  }
}

object DatasetXMLMetadata extends ModelCompanion[DatasetXMLMetadata, ObjectId] {
  val dao = current.plugin[MongoSalatPlugin] match {
    case None => throw new RuntimeException("No MongoSalatPlugin");
    case Some(x) => new SalatDAO[DatasetXMLMetadata, ObjectId](collection = x.collection("datasetxmlmetadata")) {}
  }
}

/**
 * ModelCompanion object for the models.LicenseData class. Specific to MongoDB implementation, so should either
 * be in it's own utility class within services, or, as it is currently implemented, within one of the common
 * services classes that utilize it.
 */
object LicenseData extends ModelCompanion[LicenseData, ObjectId] {
//  val collection = MongoConnection()("test-alt")("licensedata")
//  val dao = new SalatDAO[LicenseData, ObjectId](collection = collection) {}
  val dao = current.plugin[MongoSalatPlugin] match {
    case None => throw new RuntimeException("No MongoSalatPlugin");
    case Some(x) => new SalatDAO[LicenseData, ObjectId](collection = x.collection("licensedata")) {}
  }
}

object DatasetStats extends ModelCompanion[StatisticUser, ObjectId] {
  val dao = current.plugin[MongoSalatPlugin] match {
    case None => throw new RuntimeException("No MongoSalatPlugin");
    case Some(x) => new SalatDAO[StatisticUser, ObjectId](collection = x.collection("statistics.users")) {}
  }
}
