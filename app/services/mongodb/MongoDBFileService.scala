package services.mongodb

import services._
import models._
import com.mongodb.casbah.commons.MongoDBObject
import java.text.SimpleDateFormat
import scala.collection.mutable.ListBuffer
import Transformation.LidoToCidocConvertion
import java.util.{Calendar, ArrayList}
import java.io._
import org.apache.commons.io.FileUtils
import org.json.JSONObject
import play.api.libs.json.{Json, JsValue}
import com.mongodb.util.JSON
import java.nio.file.{FileSystems, Files}
import java.nio.file.attribute.BasicFileAttributes
import collection.JavaConverters._
import scala.collection.JavaConversions._
import javax.inject.{Inject, Singleton}
import com.mongodb.casbah.WriteConcern
import play.api.Logger
import com.mongodb.casbah.gridfs.GridFS
import scala.Some
import scala.util.parsing.json.JSONArray
import play.api.libs.json.JsArray
import models.File
import play.api.libs.json.JsObject
import java.util.Date
import com.novus.salat.dao.{ModelCompanion, SalatDAO}
import MongoContext.context
import play.api.Play.current
import com.mongodb.casbah.Imports._
import securesocial.core.Identity
import play.api.http.ContentTypes
import play.api.libs.MimeTypes
import scala.collection.mutable.MutableList


/**
 * Use mongo for both metadata and blobs.
 * 
 * @author Luigi Marini
 *
 */
@Singleton
class MongoDBFileService @Inject() (
  datasets: DatasetService,
  collections: CollectionService,
  sections: SectionService,
  comments: CommentService,
  previews: PreviewService,
  threeD: ThreeDService,
  sparql: RdfSPARQLService) extends FileService {

  object MustBreak extends Exception {}

  /**
   * List all files.
   */
  def listFiles(): List[File] = {
    (for (file <- FileDAO.find(MongoDBObject())) yield file).toList
  }

  /**
   * List files after a specified date.
   */
  def listFilesAfter(date: String, limit: Int): List[File] = {
    val order = MongoDBObject("uploadDate" -> -1)
    if (date == "") {
      FileDAO.find("isIntermediate" $ne true).sort(order).limit(limit).toList
    } else {
      val sinceDate = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss").parse(date)
      Logger.info("After " + sinceDate)
      FileDAO.find($and("isIntermediate" $ne true, "uploadDate" $lt sinceDate)).sort(order).limit(limit).toList
    }
  }

  /**
   * List files before a specified date.
   */
  def listFilesBefore(date: String, limit: Int): List[File] = {
    var order = MongoDBObject("uploadDate" -> -1)
    if (date == "") {
      FileDAO.find("isIntermediate" $ne true).sort(order).limit(limit).toList
    } else {
      order = MongoDBObject("uploadDate" -> 1)
      val sinceDate = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss").parse(date)
      Logger.info("Before " + sinceDate)
      var fileList = FileDAO.find($and("isIntermediate" $ne true, "uploadDate" $gt sinceDate)).sort(order).limit(limit + 1).toList.reverse
      fileList = fileList.filter(_ != fileList.last)
      fileList
    }
  }

  def latest(): Option[File] = {
    val results = FileDAO.find(MongoDBObject()).sort(MongoDBObject("created" -> -1)).limit(1).toList
    if (results.size > 0)
      Some(results(0))
    else
      None
  }

  def latest(i: Int): List[File] = {
    FileDAO.find(MongoDBObject()).sort(MongoDBObject("uploadDate" -> -1)).limit(i).toList
  }

  def first(): Option[File] = {
    val results = FileDAO.find(MongoDBObject()).sort(MongoDBObject("created" -> 1)).limit(1).toList
    if (results.size > 0)
      Some(results(0))
    else
      None
  }

  /**
   * Store file metadata.
   */
  def storeFileMD(id: UUID, filename: String, contentType: Option[String], author: Identity): Option[File] = {
    val files = current.plugin[MongoSalatPlugin] match {
      case None => throw new RuntimeException("No MongoSalatPlugin");
      case Some(x) => x.gridFS("uploads")
    }

    // required to avoid race condition on save
    files.db.setWriteConcern(WriteConcern.Safe)

    val mongoFile = files.createFile(Array[Byte]())
    mongoFile.filename = filename
    var ct = contentType.getOrElse(ContentTypes.BINARY)
    if (ct == ContentTypes.BINARY) {
      ct = MimeTypes.forFileName(filename).getOrElse(ContentTypes.BINARY)
    }
    mongoFile.contentType = ct
    mongoFile.put("path", id.stringify)
    mongoFile.put("author", SocialUserDAO.toDBObject(author))
    mongoFile.save
    val oid = mongoFile.getAs[ObjectId]("_id").get

    Some(File(UUID(oid.toString), None, mongoFile.filename.get, author, mongoFile.uploadDate, mongoFile.contentType.get, mongoFile.length))
  }

  /**
   * Save blob.
   */
  def save(inputStream: InputStream, filename: String, contentType: Option[String], author: Identity, showPreviews: String = "DatasetLevel"): Option[File] = {
    val files = current.plugin[MongoSalatPlugin] match {
      case None    => throw new RuntimeException("No MongoSalatPlugin");
      case Some(x) =>  x.gridFS("uploads")
    }

    // required to avoid race condition on save
    files.db.setWriteConcern(WriteConcern.Safe)

    val mongoFile = files.createFile(inputStream)
    Logger.debug("Uploading file " + filename)
    mongoFile.filename = filename
    var ct = contentType.getOrElse(play.api.http.ContentTypes.BINARY)
    if (ct == play.api.http.ContentTypes.BINARY) {
      ct = play.api.libs.MimeTypes.forFileName(filename).getOrElse(play.api.http.ContentTypes.BINARY)
    }
    mongoFile.contentType = ct
    mongoFile.put("showPreviews", showPreviews)
    mongoFile.put("author", SocialUserDAO.toDBObject(author))
    mongoFile.save
    val oid = mongoFile.getAs[ObjectId]("_id").get

    //No LicenseData needed here, as on creation, default arg handles it. MMF - 5/2014
    Some(File(UUID(oid.toString), None, mongoFile.filename.get, author, mongoFile.uploadDate, mongoFile.contentType.get, mongoFile.length, showPreviews))
  }

  /**
   * Get blob.
   */
  def getBytes(id: UUID): Option[(InputStream, String, String, Long)] = {
    val files = GridFS(SocialUserDAO.dao.collection.db, "uploads")
    files.findOne(MongoDBObject("_id" -> new ObjectId(id.stringify))) match {
      case Some(file) => Some(file.inputStream,
        file.getAs[String]("filename").getOrElse("unknown-name"),
        file.getAs[String]("contentType").getOrElse("unknown"),
        file.getAs[Long]("length").getOrElse(0))
      case None => None
    }
  }

  def index(id: UUID) {
    get(id) match {
      case Some(file) => {
        var tagListBuffer = new ListBuffer[String]()

        for (tag <- file.tags) {
          tagListBuffer += tag.name
        }

        val tagsJson = new JSONArray(tagListBuffer.toList)

        Logger.debug("tagStr=" + tagsJson);

        val commentsByFile = for (comment <- comments.findCommentsByFileId(id)) yield {
          comment.text
        }
        val commentJson = new JSONArray(commentsByFile)

        Logger.debug("commentStr=" + commentJson.toString())

        val usrMd = getUserMetadataJSON(id)
        Logger.debug("usrmd=" + usrMd)

        val techMd = getTechnicalMetadataJSON(id)
        Logger.debug("techmd=" + techMd)

        val xmlMd = getXMLMetadataJSON(id)
        Logger.debug("xmlmd=" + xmlMd)

        var fileDsId = ""
        var fileDsName = ""

        for (dataset <- datasets.findByFileId(file.id)) {
          fileDsId = fileDsId + dataset.id.stringify + " %%% "
          fileDsName = fileDsName + dataset.name + " %%% "
        }
        
        val formatter = new SimpleDateFormat("dd/MM/yyyy")

        current.plugin[ElasticsearchPlugin].foreach {
          _.index("data", "file", id,
            List(("filename", file.filename), ("contentType", file.contentType),("author",file.author.fullName),("uploadDate",formatter.format(file.uploadDate)),("datasetId",fileDsId),("datasetName",fileDsName), ("tag", tagsJson.toString), ("comments", commentJson.toString), ("usermetadata", usrMd), ("technicalmetadata", techMd), ("xmlmetadata", xmlMd)))
        }
        
      }
      case None => Logger.error("File not found: " + id)
    }
  }

  def modifyRDFOfMetadataChangedFiles() {
    val changedFiles = findMetadataChangedFiles()
    for (changedFile <- changedFiles) {
      modifyRDFUserMetadata(changedFile.id)
    }
  }


  def modifyRDFUserMetadata(id: UUID, mappingNumber: String = "1") = {
    sparql.removeFileFromGraphs(id, "rdfCommunityGraphName")
    get(id) match {
      case Some(file) => {
        val theJSON = getUserMetadataJSON(id)
        val fileSep = System.getProperty("file.separator")
        val tmpDir = System.getProperty("java.io.tmpdir")
        var resultDir = tmpDir + fileSep + "medici__rdfuploadtemporaryfiles" + fileSep + UUID.generate.stringify
        val resultDirFile = new java.io.File(resultDir)
        resultDirFile.mkdirs()

        if (!theJSON.replaceAll(" ", "").equals("{}")) {
          val xmlFile = jsonToXML(theJSON)
          new LidoToCidocConvertion(play.api.Play.configuration.getString("filesxmltordfmapping.dir_" + mappingNumber).getOrElse(""), xmlFile.getAbsolutePath(), resultDir)
          xmlFile.delete()
        }
        else {
          new java.io.File(resultDir + fileSep + "Results.rdf").createNewFile()
        }
        val resultFile = new java.io.File(resultDir + fileSep + "Results.rdf")

        //Connecting RDF metadata with the entity describing the original file
        val rootNodes = new ArrayList[String]()
        val rootNodesFile = play.api.Play.configuration.getString("rootNodesFile").getOrElse("")
        Logger.debug(rootNodesFile)
        if (!rootNodesFile.equals("*")) {
          val rootNodesReader = new BufferedReader(new FileReader(new java.io.File(rootNodesFile)))
          var line = rootNodesReader.readLine()
          while (line != null) {
            Logger.debug((line == null).toString())
            rootNodes.add(line.trim())
            line = rootNodesReader.readLine()
          }
          rootNodesReader.close()
        }

        val resultFileConnected = java.io.File.createTempFile("ResultsConnected", ".rdf")

        val fileWriter = new BufferedWriter(new FileWriter(resultFileConnected))
        val fis = new FileInputStream(resultFile)
        val data = new Array[Byte](resultFile.length().asInstanceOf[Int])
        fis.read(data)
        fis.close()
        resultFile.delete()
        FileUtils.deleteDirectory(resultDirFile)
        //
        val s = new String(data, "UTF-8")
        val rdfDescriptions = s.split("<rdf:Description")
        fileWriter.write(rdfDescriptions(0))
        var i = 0
        for (i <- 1 to (rdfDescriptions.length - 1)) {
          fileWriter.write("<rdf:Description" + rdfDescriptions(i))
          if (rdfDescriptions(i).contains("<rdf:type")) {
            var isInRootNodes = false
            if (rootNodesFile.equals("*"))
              isInRootNodes = true
            else {
              var j = 0
              try {
                for (j <- 0 to (rootNodes.size() - 1)) {
                  if (rdfDescriptions(i).contains("\"" + rootNodes.get(j) + "\"")) {
                    isInRootNodes = true
                    throw MustBreak
                  }
                }
              } catch {
                case MustBreak =>
              }
            }

            if (isInRootNodes) {
              val theResource = rdfDescriptions(i).substring(rdfDescriptions(i).indexOf("\"") + 1, rdfDescriptions(i).indexOf("\"", rdfDescriptions(i).indexOf("\"") + 1))
              val theHost = "http://" + play.Play.application().configuration().getString("hostIp").replaceAll("/$", "") + ":" + play.Play.application().configuration().getString("http.port")
              var connection = "<rdf:Description rdf:about=\"" + theHost + "/api/files/" + id
              connection = connection + "\"><P129_is_about xmlns=\"http://www.cidoc-crm.org/rdfs/cidoc_crm_v5.0.2.rdfs#\" rdf:resource=\"" + theResource
              connection = connection + "\"/></rdf:Description>"
              fileWriter.write(connection)
            }
          }
        }
        fileWriter.close()

        sparql.addFromFile(id, resultFileConnected, "file")
        resultFileConnected.delete()

        sparql.addFileToGraph(id, "rdfCommunityGraphName")

        setUserMetadataWasModified(id, false)
      }
      case None => {}
    }
  }

  def jsonToXML(theJSON: String): java.io.File = {

    val jsonObject = new JSONObject(theJSON)
    var xml = org.json.XML.toString(jsonObject)

    //Remove spaces from XML tags
    var currStart = xml.indexOf("<")
    var currEnd = -1
    var xmlNoSpaces = ""
    while (currStart != -1) {
      xmlNoSpaces = xmlNoSpaces + xml.substring(currEnd + 1, currStart)
      currEnd = xml.indexOf(">", currStart + 1)
      xmlNoSpaces = xmlNoSpaces + xml.substring(currStart, currEnd + 1).replaceAll(" ", "_")
      currStart = xml.indexOf("<", currEnd + 1)
    }
    xmlNoSpaces = xmlNoSpaces + xml.substring(currEnd + 1)

    val xmlFile = java.io.File.createTempFile("xml", ".xml")
    val fileWriter = new BufferedWriter(new FileWriter(xmlFile))
    fileWriter.write(xmlNoSpaces)
    fileWriter.close()

    return xmlFile
  }

  def getXMLMetadataJSON(id: UUID): String = {
    FileDAO.dao.collection.findOneByID(new ObjectId(id.stringify)) match {
      case None => "{}"
      case Some(x) => {
        x.getAs[DBObject]("xmlMetadata") match {
          case Some(y) => {
            val returnedMetadata = com.mongodb.util.JSON.serialize(x.getAs[DBObject]("xmlMetadata").get)
            returnedMetadata
          }
          case None => "{}"
        }
      }
    }
  }
  
 
  

  def removeTags(id: UUID, userIdStr: Option[String], eid: Option[String], tags: List[String]) {
    Logger.debug("Removing tags in file " + id + " : " + tags + ", userId: " + userIdStr + ", eid: " + eid)
    val file = get(id).get
    val existingTags = file.tags.filter(x => userIdStr == x.userId && eid == x.extractor_id).map(_.name)
    Logger.debug("existingTags after user and extractor filtering: " + existingTags.toString)
    // Only remove existing tags.
    tags.intersect(existingTags).map {
      tag =>
        FileDAO.update(MongoDBObject("_id" -> new ObjectId(id.stringify)), $pull("tags" -> MongoDBObject("name" -> tag)), false, false, WriteConcern.Safe)
    }
  }

  def addMetadata(fileId: UUID, metadata: JsValue) {
    val doc = JSON.parse(Json.stringify(metadata)).asInstanceOf[DBObject]
    FileDAO.update(MongoDBObject("_id" -> new ObjectId(fileId.stringify)), $addToSet("metadata" -> doc), false, false, WriteConcern.Safe)
  }

  def get(id: UUID): Option[File] = {
    FileDAO.findOneById(new ObjectId(id.stringify)) match {
      case Some(file) => {
        val previewsByFile = previews.findByFileId(file.id)
        val sectionsByFile = sections.findByFileId(file.id)
        val sectionsWithPreviews = sectionsByFile.map { s =>
          val p = PreviewDAO.findOne(MongoDBObject("section_id"->s.id))
          s.copy(preview = p)
        }
        Some(file.copy(sections = sectionsWithPreviews, previews = previewsByFile))
      }
      case None => None
    }
  }

  def listOutsideDataset(dataset_id: UUID): List[File] = {
    datasets.get(dataset_id) match{
      case Some(dataset) => {
        val list = for (file <- FileDAO.findAll(); if(!isInDataset(file,dataset) && !file.isIntermediate.getOrElse(false))) yield file
        return list.toList
      }
      case None =>{
        return FileDAO.findAll.toList
      }
    }
  }

  def isInDataset(file: File, dataset: Dataset): Boolean = {
    for(dsFile <- dataset.files){
      if(dsFile.id == file.id)
        return true
    }
    return false
  }

  //Not used yet
  def getMetadata(id: UUID): scala.collection.immutable.Map[String,Any] = {
    FileDAO.dao.collection.findOneByID(new ObjectId(id.stringify)) match {
      case None => new scala.collection.immutable.HashMap[String,Any]
      case Some(x) => {
        val returnedMetadata = x.getAs[DBObject]("metadata").get.toMap.asScala.asInstanceOf[scala.collection.mutable.Map[String,Any]].toMap
        returnedMetadata
      }
    }
  }

  def getUserMetadata(id: UUID): scala.collection.mutable.Map[String,Any] = {
    FileDAO.dao.collection.findOneByID(new ObjectId(id.stringify)) match {
      case None => new scala.collection.mutable.HashMap[String,Any]
      case Some(x) => {
        x.getAs[DBObject]("userMetadata") match{
          case Some(y)=>{
            val returnedMetadata = x.getAs[DBObject]("userMetadata").get.toMap.asScala.asInstanceOf[scala.collection.mutable.Map[String,Any]]
            returnedMetadata
          }
          case None => new scala.collection.mutable.HashMap[String,Any]
        }
      }
    }
  }

  def getUserMetadataJSON(id: UUID): String = {
    FileDAO.dao.collection.findOneByID(new ObjectId(id.stringify)) match {
      case None => "{}"
      case Some(x) => {
        x.getAs[DBObject]("userMetadata") match{
          case Some(y)=>{
            val returnedMetadata = com.mongodb.util.JSON.serialize(x.getAs[DBObject]("userMetadata").get)
            returnedMetadata
          }
          case None => "{}"
        }
      }
    }
  }

  def getTechnicalMetadataJSON(id: UUID): String = {
    FileDAO.dao.collection.findOneByID(new ObjectId(id.stringify)) match {
      case None => "{}"
      case Some(x) => {
        x.getAs[DBObject]("metadata") match{
          case Some(y)=>{
            val returnedMetadata = com.mongodb.util.JSON.serialize(x.getAs[DBObject]("metadata").get)
            returnedMetadata
          }
          case None => "{}"
        }
      }
    }
  }

  
  
  /**
   *  Add versus descriptors to the metadata
   *
   * Reads descriptors received from versus( in the response body)  as list of JSON objects
   * Each JSON object has four fields <extraction_id,adapter_name,extractor_name,descriptor>
   * Parse each json object based on the field/key name and obtain the values and combine them as a tuple
   * Obtain the existing versus descriptors in the "metadata" as list of tuples (extraction_id,adapter_name,extractor_name,descriptor)
   * merge with the tuples obtained from descriptors received from versus
   * write it back to the versus_descriptors field of "metadata" to mongoDB
   *
   */
 def addVersusMetadata(id:UUID,json:JsValue){
    val doc = JSON.parse(Json.stringify(json)).asInstanceOf[DBObject].toMap
              .asScala.asInstanceOf[scala.collection.mutable.Map[String,Any]].toMap
    VersusDAO.insert(new Versus(id,doc),WriteConcern.Safe)
    Logger.info("--Added versus descriptors in json format received from versus to the metadata field --")
  } 
  
 def getVersusMetadata(id: UUID): Option[JsValue] = {
    val versusDescriptor=VersusDAO.findOne(MongoDBObject("fileId" -> new ObjectId(id.stringify)))
    versusDescriptor.map{
      vdes=>
      	 val x=com.mongodb.util.JSON.serialize(vdes.asInstanceOf[Versus].descriptors("versus_descriptors"))
      	 Json.parse(x)
     }
  } 
 
  /**
   * This is the previous code that adds Versus descriptors to the metadata. If the Versus extraction is carried out more than once, it takes care of it
   * by merging the extractions results into single list
   * This works fine in Mac but due to some reason, it does not work in Ubuntu and gives mongodb exception
   * TODO: need to incorporate this into the current addVersusMetadata code
   * 
   * This code will be deleted once we figure out where to add versus metadata
   */
   /*def addVersusMetadata(id: UUID, json: JsValue) {
   
     Logger.debug("******MongoDB::::Adding Versus metadata to file " + id.toString )

    var jsonlist = json.as[List[JsObject]] // read json as list of JSON objects

    var addmd = jsonlist.map {
      list =>
        Logger.debug("extraction_id=" + list \ ("extraction_id"))
        Logger.debug("adapter_name=" + list \ ("adapter_name"))
        Logger.debug("extractor_name=" + list \ ("extractor_name"))
        //Logger.debug("descriptor=" + list \ ("descriptor"))
        (list \ ("extraction_id"), list \ ("adapter_name"), list \ ("extractor_name"), list \ ("descriptor"))
    } /* to access into the list of json objects and convert as list of tuples*/

    val doc = com.mongodb.util.JSON.parse(json.toString)

    FileDAO.dao.collection.findOneByID(new ObjectId(id.stringify)) match {
      case Some(x) => {

        x.getAs[DBObject]("metadata") match {
          case None => {
            Logger.debug("-----No metadata field found: Adding meta data field and setting Versus Descriptors----")
            FileDAO.dao.collection.update(MongoDBObject("_id" -> new ObjectId(id.stringify)), $set("metadata.versus_descriptors" -> doc), false, false, WriteConcern.Safe)
            Logger.debug("-----Added metadata field ----")
            
            
          }
          case Some(map) => {

            Logger.debug("----metadata found--- ")

            val returnedMetadata = com.mongodb.util.JSON.serialize(x.getAs[DBObject]("metadata").get)
            Logger.debug("retmd: " + returnedMetadata)

            val retmd = Json.toJson(returnedMetadata)
            var ksize = map.keySet().size()
            Logger.debug("Contains Keys versus descriptors: " + map.containsField("versus_descriptors"))
            val listd = Json.parse(returnedMetadata) \ ("versus_descriptors")

            var mdList = listd.as[List[JsObject]].map {
              md =>
                Logger.debug("extraction_id=" + md \ ("extraction_id"))
                Logger.debug("adapter_name=" + md \ ("adapter_name"))
                Logger.debug("extractor_name=" + md \ ("extractor_name"))
                Logger.debug("descriptor=" + md \ ("descriptor"))
                (md \ ("extraction_id"), md \ ("adapter_name"), md \ ("extractor_name"), md \ ("descriptor"))
            }

            val versusmd = mdList ++ addmd

            var versusmdList = for ((id, a, e, d) <- versusmd) yield Json.obj("extraction_id" -> id, "adapter_name" -> a, "extractor_name" -> e, "descriptor" -> d)

            val jobj = Json.obj("versus_descriptors" -> getJsonArray(versusmdList))

            Logger.debug("versus mdList:  " + jobj)

            FileDAO.dao.collection.update(MongoDBObject("_id" -> new ObjectId(id.stringify)), $set("metadata" -> com.mongodb.util.JSON.parse(jobj.toString)), false, false, WriteConcern.Safe)

          }
        }

      }
      case None => Logger.error("Error getting file" + id)
    }
  }*/

  /*convert list of JsObject to JsArray*/
  def getJsonArray(list: List[JsObject]): JsArray = {
    list.foldLeft(JsArray())((acc, x) => acc ++ Json.arr(x))
  }

  def addUserMetadata(id: UUID, json: String) {
    Logger.debug("Adding/modifying user metadata to file " + id + " : " + json)
    val md = com.mongodb.util.JSON.parse(json).asInstanceOf[DBObject]
    FileDAO.update(MongoDBObject("_id" -> new ObjectId(id.stringify)), $set("userMetadata" -> md), false, false, WriteConcern.Safe)
  }

  def addXMLMetadata(id: UUID, json: String) {
    Logger.debug("Adding/modifying XML file metadata to file " + id + " : " + json)
    val md = com.mongodb.util.JSON.parse(json).asInstanceOf[DBObject]
    FileDAO.update(MongoDBObject("_id" -> new ObjectId(id.stringify)), $set("xmlMetadata" -> md), false, false, WriteConcern.Safe)
  }


  def findByTag(tag: String): List[File] = {
    FileDAO.find(MongoDBObject("tags.name" -> tag)).toList
  }

  def findIntermediates(): List[File] = {
    FileDAO.find(MongoDBObject("isIntermediate" -> true)).toList
  }
  
  /**
   * Implementation of updateLicenseing defined in services/FileService.scala.
   */
  def updateLicense(id: UUID, licenseType: String, rightsHolder: String, licenseText: String, licenseUrl: String, allowDownload: String) {      
      val licenseData = models.LicenseData(m_licenseType = licenseType, m_rightsHolder = rightsHolder, m_licenseText = licenseText, m_licenseUrl = licenseUrl, m_allowDownload = allowDownload.toBoolean)
      val result = FileDAO.update(MongoDBObject("_id" -> new ObjectId(id.stringify)), 
          $set("licenseData" -> LicenseData.toDBObject(licenseData)), 
          false, false, WriteConcern.Safe);      
  }

  // ---------- Tags related code starts ------------------
  // Input validation is done in api.Files, so no need to check again.
  def addTags(id: UUID, userIdStr: Option[String], eid: Option[String], tags: List[String]) {
    Logger.debug("Adding tags to file " + id + " : " + tags)
    val file = get(id).get
    val existingTags = file.tags.filter(x => userIdStr == x.userId && eid == x.extractor_id).map(_.name)
    val createdDate = new Date
    tags.foreach(tag => {
      // Only add tags with new values.
      if (!existingTags.contains(tag)) {
        val tagObj = models.Tag(name = tag, userId = userIdStr, extractor_id = eid, created = createdDate)
        FileDAO.update(MongoDBObject("_id" -> new ObjectId(id.stringify)), $addToSet("tags" -> Tag.toDBObject(tagObj)), false, false, WriteConcern.Safe)
      }
    })
  }

  def removeAllTags(id: UUID) {
    FileDAO.update(MongoDBObject("_id" -> new ObjectId(id.stringify)), $set("tags" -> List()), false, false, WriteConcern.Safe)
  }
  // ---------- Tags related code ends ------------------

  def comment(id: UUID, comment: Comment) {
    FileDAO.update(MongoDBObject("_id" -> new ObjectId(id.stringify)), $addToSet("comments" -> Comment.toDBObject(comment)), false, false, WriteConcern.Safe)
  }
  
  def setIntermediate(id: UUID){
    FileDAO.update(MongoDBObject("_id" -> new ObjectId(id.stringify)), $set("isIntermediate" -> Some(true)), false, false, WriteConcern.Safe)
  }

  def renameFile(id: UUID, newName: String){
    FileDAO.update(MongoDBObject("_id" -> new ObjectId(id.stringify)), $set("filename" -> newName), false, false, WriteConcern.Safe)
  }

  def setContentType(id: UUID, newType: String){
    FileDAO.update(MongoDBObject("_id" -> new ObjectId(id.stringify)), $set("contentType" -> newType), false, false, WriteConcern.Safe)
  }

  def setUserMetadataWasModified(id: UUID, wasModified: Boolean){
    FileDAO.update(MongoDBObject("_id" -> new ObjectId(id.stringify)), $set("userMetadataWasModified" -> Some(wasModified)), false, false, WriteConcern.Safe)
  }

  def removeFile(id: UUID){
    get(id) match{
      case Some(file) => {
        if(file.isIntermediate.isEmpty){
          val fileDatasets = datasets.findByFileId(file.id)
          for(fileDataset <- fileDatasets){
            datasets.removeFile(fileDataset.id, id)
            if(!file.xmlMetadata.isEmpty){
              datasets.index(fileDataset.id)
            }
            if(!file.thumbnail_id.isEmpty && !fileDataset.thumbnail_id.isEmpty)
              if(file.thumbnail_id.get == fileDataset.thumbnail_id.get){
                datasets.newThumbnail(fileDataset.id)	        	  
		        	}
  
          }
          for(section <- sections.findByFileId(file.id)){
            sections.removeSection(section)
          }
          for(preview <- previews.findByFileId(file.id)){
            previews.removePreview(preview)
          }
          for(comment <- comments.findCommentsByFileId(id)){
            comments.removeComment(comment)
          }
          for(texture <- threeD.findTexturesByFileId(file.id)){
            ThreeDTextureDAO.removeById(new ObjectId(texture.id.stringify))
          }
          if(!file.thumbnail_id.isEmpty)
            Thumbnail.removeById(new ObjectId(file.thumbnail_id.get))
        }
        FileDAO.removeById(new ObjectId(file.id.stringify))
      }
      case None => Logger.debug("File not found")
    }
  }

  def removeTemporaries(){
    val cal = Calendar.getInstance()
    val timeDiff = play.Play.application().configuration().getInt("rdfTempCleanup.removeAfter")
    cal.add(Calendar.MINUTE, -timeDiff)
    val oldDate = cal.getTime()

    val tmpDir = System.getProperty("java.io.tmpdir")
    val filesep = System.getProperty("file.separator")
    val rdfTmpDir = new java.io.File(tmpDir + filesep + "medici__rdfdumptemporaryfiles")
    if(!rdfTmpDir.exists()){
      rdfTmpDir.mkdir()
    }

    val listOfFiles = rdfTmpDir.listFiles()
    for(currFileDir <- listOfFiles){
      val currFile = currFileDir.listFiles()(0)
      val attrs = Files.readAttributes(FileSystems.getDefault().getPath(currFile.getAbsolutePath()),  classOf[BasicFileAttributes])
      val timeCreated = new Date(attrs.creationTime().toMillis())
      if(timeCreated.compareTo(oldDate) < 0){
        currFile.delete()
        currFileDir.delete()
      }
    }
  }

  def findMetadataChangedFiles(): List[File] = {
    FileDAO.find(MongoDBObject("userMetadataWasModified" -> true)).toList
  }

  def searchAllMetadataFormulateQuery(requestedMetadataQuery: Any): List[File] = {
    Logger.debug("top: "+ requestedMetadataQuery.asInstanceOf[java.util.LinkedHashMap[String,Any]].toString()  )
    var theQuery =  searchMetadataFormulateQuery(requestedMetadataQuery.asInstanceOf[java.util.LinkedHashMap[String,Any]], "all")
    Logger.debug("thequery: "+theQuery.toString)
    FileDAO.find(theQuery).toList
  }


  def searchUserMetadataFormulateQuery(requestedMetadataQuery: Any): List[File] = {
    Logger.debug("top: "+ requestedMetadataQuery.asInstanceOf[java.util.LinkedHashMap[String,Any]].toString()  )
    var theQuery =  searchMetadataFormulateQuery(requestedMetadataQuery.asInstanceOf[java.util.LinkedHashMap[String,Any]], "userMetadata")
    Logger.debug("thequery: "+theQuery.toString)
    FileDAO.find(theQuery).toList
  }

  def searchMetadataFormulateQuery(requestedMap: java.util.LinkedHashMap[String,Any], root: String): MongoDBObject = {
    Logger.debug("req: "+ requestedMap)
    var queryMap = MongoDBList()
    var builder = MongoDBList()
    var orFound = false
    for((reqKey, reqValue) <- requestedMap){
      val keyTrimmed = reqKey.replaceAll("__[0-9]+$","")

      if(keyTrimmed.equals("OR")){
        queryMap.add(MongoDBObject("$and" ->  builder))
        builder = MongoDBList()
        orFound = true
      }
      else{
        var actualKey = keyTrimmed
        if(keyTrimmed.endsWith("__not")){
          actualKey = actualKey.substring(0, actualKey.length()-5)
        }

        if(!root.equals("all")){

          if(!root.equals(""))
            actualKey = root + "." + actualKey

          if(reqValue.isInstanceOf[String]){
            val currValue = reqValue.asInstanceOf[String]
            if(keyTrimmed.endsWith("__not")){
              builder += MongoDBObject(actualKey -> MongoDBObject("$ne" ->  currValue))
            }
            else{
              builder += MongoDBObject(actualKey -> currValue)
            }
          }else{
            //recursive
            if(root.equals("userMetadata")){
              val currValue =  searchMetadataFormulateQuery(reqValue.asInstanceOf[java.util.LinkedHashMap[String,Any]], "")
              val elemMatch = actualKey $elemMatch currValue
              builder.add(elemMatch)
            }
            else{
              val currValue =  searchMetadataFormulateQuery(reqValue.asInstanceOf[java.util.LinkedHashMap[String,Any]], actualKey)
              builder += currValue
            }
          }
        } else {
          var objectForEach = MongoDBList()
          val allRoots = Map(1 -> "userMetadata", 2 -> "metadata", 3 -> "xmlMetadata")
          allRoots.keys.foreach{ i =>
            var tempActualKey = allRoots(i) + "." + actualKey

            if(reqValue.isInstanceOf[String]){
              val currValue = reqValue.asInstanceOf[String]
              if(keyTrimmed.endsWith("__not")){
                objectForEach += MongoDBObject(tempActualKey -> MongoDBObject("$ne" ->  currValue))
              }
              else{
                objectForEach += MongoDBObject(tempActualKey -> currValue)
              }
            }else{
              //recursive
              if(allRoots(i).equals("userMetadata")){
                val currValue =  searchMetadataFormulateQuery(reqValue.asInstanceOf[java.util.LinkedHashMap[String,Any]], "")
                val elemMatch = tempActualKey $elemMatch currValue
                objectForEach.add(elemMatch)
              }
              else{
                val currValue =  searchMetadataFormulateQuery(reqValue.asInstanceOf[java.util.LinkedHashMap[String,Any]], tempActualKey)
                objectForEach += currValue
              }
            }
          }

          builder.add(MongoDBObject("$or" ->  objectForEach))

        }
      }
    }

    if(orFound){
      queryMap.add(MongoDBObject("$and" ->  builder))
      return MongoDBObject("$or" ->  queryMap)
    }
    else if(!builder.isEmpty)  {
      return MongoDBObject("$and" ->  builder)
    }
    else if(!root.equals("")){
      return (root $exists true)
    }
    else{
      return new MongoDBObject()
    }
  }

  def removeOldIntermediates(){
    val cal = Calendar.getInstance()
    val timeDiff = play.Play.application().configuration().getInt("intermediateCleanup.removeAfter")
    cal.add(Calendar.HOUR, -timeDiff)
    val oldDate = cal.getTime()
    val fileList = FileDAO.find($and("isIntermediate" $eq true, "uploadDate" $lt oldDate)).toList
    for(file <- fileList)
      removeFile(file.id)
  }

  /**
   * Update thumbnail used to represent this dataset.
   */
  def updateThumbnail(fileId: UUID, thumbnailId: UUID) {
    FileDAO.update(MongoDBObject("_id" -> new ObjectId(fileId.stringify)),
      $set("thumbnail_id" -> thumbnailId.stringify), false, false, WriteConcern.Safe)
  }
  
  def setNotesHTML(id: UUID, html: String) {
	    FileDAO.update(MongoDBObject("_id" -> new ObjectId(id.stringify)), $set("notesHTML" -> Some(html)), false, false, WriteConcern.Safe)    
  }

}

object FileDAO extends ModelCompanion[File, ObjectId] {
  val dao = current.plugin[MongoSalatPlugin] match {
    case None => throw new RuntimeException("No MongoSalatPlugin");
    case Some(x) => new SalatDAO[File, ObjectId](collection = x.collection("uploads.files")) {}
  }
}


object VersusDAO extends ModelCompanion[Versus,ObjectId]{
    val dao = current.plugin[MongoSalatPlugin] match {
    case None => throw new RuntimeException("No MongoSalatPlugin");
    case Some(x) => new SalatDAO[Versus, ObjectId](collection = x.collection("versus.descriptors")) {}
  }
}

