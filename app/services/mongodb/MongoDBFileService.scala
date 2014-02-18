package services.mongodb

import services.{DatasetService, ElasticsearchPlugin, FileService}
import models._
import com.mongodb.casbah.commons.MongoDBObject
import java.text.SimpleDateFormat
import com.mongodb.casbah.Imports._
import securesocial.core.Identity
import play.api.Play._
import scala.collection.mutable.ListBuffer
import Transformation.LidoToCidocConvertion
import java.util.{Calendar, Date, ArrayList}
import java.io._
import org.apache.commons.io.FileUtils
import org.json.JSONObject
import play.api.libs.json.{Json, JsValue}
import com.mongodb.util.JSON
import java.nio.file.{FileSystems, Files}
import java.nio.file.attribute.BasicFileAttributes
import collection.JavaConverters._
import scala.collection.JavaConversions._
import play.api.Logger
import play.api.libs.json.JsArray
import scala.Some
import scala.util.parsing.json.JSONArray
import models.File
import play.api.libs.json.JsObject
import javax.inject.{Inject, Singleton}


/**
 * Use mongo for both metadata and blobs.
 * 
 * @author Luigi Marini
 *
 */
@Singleton
class MongoDBFileService @Inject() (datasets: DatasetService)  extends FileService with GridFSDB {

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

  /**
   * Store file metadata.
   */
  def storeFileMD(id: String, filename: String, contentType: Option[String], author: Identity): Option[File] = {
    val files = current.plugin[MongoSalatPlugin] match {
      case None => throw new RuntimeException("No MongoSalatPlugin");
      case Some(x) => x.gridFS("uploads")
    }

    // required to avoid race condition on save
    files.db.setWriteConcern(WriteConcern.Safe)

    val mongoFile = files.createFile(Array[Byte]())
    mongoFile.filename = filename
    var ct = contentType.getOrElse(play.api.http.ContentTypes.BINARY)
    if (ct == play.api.http.ContentTypes.BINARY) {
      ct = play.api.libs.MimeTypes.forFileName(filename).getOrElse(play.api.http.ContentTypes.BINARY)
    }
    mongoFile.contentType = ct
    mongoFile.put("path", id)
    mongoFile.put("author", SocialUserDAO.toDBObject(author))
    mongoFile.save
    val oid = mongoFile.getAs[ObjectId]("_id").get

    // FIXME Figure out why SalatDAO has a race condition with gridfs
    //    Logger.debug("FILE ID " + oid)
    //    val file = FileDAO.findOne(MongoDBObject("_id" -> oid))
    //    file match {
    //      case Some(id) => Logger.debug("FILE FOUND")
    //      case None => Logger.error("NO FILE!!!!!!")
    //    }

    Some(File(oid, None, mongoFile.filename.get, author, mongoFile.uploadDate, mongoFile.contentType.get, mongoFile.length))
  }

  def index(id: String) {
    get(id) match {
      case Some(file) => {
        var tagListBuffer = new ListBuffer[String]()

        for (tag <- file.tags) {
          tagListBuffer += tag.name
        }

        val tagsJson = new JSONArray(tagListBuffer.toList)

        Logger.debug("tagStr=" + tagsJson);

        val comments = for (comment <- Comment.findCommentsByFileId(id)) yield {
          comment.text
        }
        val commentJson = new JSONArray(comments)

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
          fileDsId = fileDsId + dataset.id.toString + "  "
          fileDsName = fileDsName + dataset.name + "  "
        }

        current.plugin[ElasticsearchPlugin].foreach {
          _.index("data", "file", id,
            List(("filename", file.filename), ("contentType", file.contentType), ("datasetId", fileDsId), ("datasetName", fileDsName), ("tag", tagsJson.toString), ("comments", commentJson.toString), ("usermetadata", usrMd), ("technicalmetadata", techMd), ("xmlmetadata", xmlMd)))
        }
      }
      case None => Logger.error("File not found: " + id)
    }
  }

  def modifyRDFOfMetadataChangedFiles() {
    val changedFiles = findMetadataChangedFiles()
    for (changedFile <- changedFiles) {
      modifyRDFUserMetadata(changedFile.id.toString)
    }
  }


  def modifyRDFUserMetadata(id: String, mappingNumber: String = "1") = {
    services.Services.rdfSPARQLService.removeFileFromGraphs(id, "rdfCommunityGraphName")
    get(id) match {
      case Some(file) => {
        val theJSON = getUserMetadataJSON(id)
        val fileSep = System.getProperty("file.separator")
        val tmpDir = System.getProperty("java.io.tmpdir")
        var resultDir = tmpDir + fileSep + "medici__rdfuploadtemporaryfiles" + fileSep + new ObjectId().toString
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

        services.Services.rdfSPARQLService.addFromFile(id, resultFileConnected, "file")
        resultFileConnected.delete()

        services.Services.rdfSPARQLService.addFileToGraph(id, "rdfCommunityGraphName")

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

  def getXMLMetadataJSON(id: String): String = {
    FileDAO.dao.collection.findOneByID(new ObjectId(id)) match {
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

  def removeTags(id: String, userIdStr: Option[String], eid: Option[String], tags: List[String]) {
    Logger.debug("Removing tags in file " + id + " : " + tags + ", userId: " + userIdStr + ", eid: " + eid)
    val file = get(id).get
    val existingTags = file.tags.filter(x => userIdStr == x.userId && eid == x.extractor_id).map(_.name)
    Logger.debug("existingTags after user and extractor filtering: " + existingTags.toString)
    // Only remove existing tags.
    tags.intersect(existingTags).map {
      tag =>
        FileDAO.update(MongoDBObject("_id" -> new ObjectId(id)), $pull("tags" -> MongoDBObject("name" -> tag)), false, false, WriteConcern.Safe)
    }
  }

  def addMetadata(fileId: String, metadata: JsValue) {
    val doc = JSON.parse(Json.stringify(metadata)).asInstanceOf[DBObject]
    FileDAO.update(MongoDBObject("_id" -> new ObjectId(fileId)), $addToSet("metadata" -> doc), false, false, WriteConcern.Safe)
  }

  def get(id: String): Option[File] = {
    get(id) match {
      case Some(file) => {
        val previews = PreviewDAO.findByFileId(file.id)
        val sections = SectionDAO.findByFileId(file.id)
        val sectionsWithPreviews = sections.map { s =>
          val p = PreviewDAO.findOne(MongoDBObject("section_id"->s.id))
          s.copy(preview = p)
        }
        Some(file.copy(sections = sectionsWithPreviews, previews = previews))
      }
      case None => None
    }
  }

  def listOutsideDataset(dataset_id: String): List[File] = {
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
  def getMetadata(id: String): scala.collection.immutable.Map[String,Any] = {
    FileDAO.dao.collection.findOneByID(new ObjectId(id)) match {
      case None => new scala.collection.immutable.HashMap[String,Any]
      case Some(x) => {
        val returnedMetadata = x.getAs[DBObject]("metadata").get.toMap.asScala.asInstanceOf[scala.collection.mutable.Map[String,Any]].toMap
        returnedMetadata
      }
    }
  }

  def getUserMetadata(id: String): scala.collection.mutable.Map[String,Any] = {
    FileDAO.dao.collection.findOneByID(new ObjectId(id)) match {
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


  def getUserMetadataJSON(id: String): String = {
    FileDAO.dao.collection.findOneByID(new ObjectId(id)) match {
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

  def getTechnicalMetadataJSON(id: String): String = {
    FileDAO.dao.collection.findOneByID(new ObjectId(id)) match {
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
   * write it back to the versus_descriptors field of "metadata" to monoDB
   *
   */
  def addVersusMetadata(id: String, json: JsValue) {

    Logger.debug("Adding metadata to file " + id + " : " + json)

    var jsonlist = json.as[List[JsObject]] // read json as list of JSON objects

    var addmd = jsonlist.map {
      list =>
        Logger.debug("extraction_id=" + list \ ("extraction_id"))
        Logger.debug("adapter_name=" + list \ ("adapter_name"))
        Logger.debug("extractor_name=" + list \ ("extractor_name"))
        Logger.debug("descriptor=" + list \ ("descriptor"))
        (list \ ("extraction_id"), list \ ("adapter_name"), list \ ("extractor_name"), list \ ("descriptor"))
    } /* to access into the list of json objects and convert as list of tuples*/

    val doc = com.mongodb.util.JSON.parse(json.toString)

    FileDAO.dao.collection.findOneByID(new ObjectId(id)) match {
      case Some(x) => {

        x.getAs[DBObject]("metadata") match {
          case None => {
            Logger.debug("No metadata field found: Adding meta data field")
            FileDAO.dao.collection.update(MongoDBObject("_id" -> new ObjectId(id)), $set("metadata.versus_descriptors" -> doc), false, false, WriteConcern.Safe)

          }
          case Some(map) => {

            Logger.debug("metadata found ")

            val returnedMetadata = com.mongodb.util.JSON.serialize(x.getAs[DBObject]("metadata").get)
            Logger.debug("retmd: " + returnedMetadata)

            val retmd = Json.toJson(returnedMetadata)
            var ksize = map.keySet().size()
            Logger.debug("Contains Keys versus descriptors: " + map.containsKey("versus_descriptors"))
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

            FileDAO.dao.collection.update(MongoDBObject("_id" -> new ObjectId(id)), $set("metadata" -> com.mongodb.util.JSON.parse(jobj.toString)), false, false, WriteConcern.Safe)

          }
        }

      }
      case None => {
        Logger.error("Error getting file" + id)

      }
    }
  }

  /*convert list of JsObject to JsArray*/
  def getJsonArray(list: List[JsObject]): JsArray = {
    list.foldLeft(JsArray())((acc, x) => acc ++ Json.arr(x))
  }


  def addUserMetadata(id: String, json: String) {
    Logger.debug("Adding/modifying user metadata to file " + id + " : " + json)
    val md = com.mongodb.util.JSON.parse(json).asInstanceOf[DBObject]
    FileDAO.update(MongoDBObject("_id" -> new ObjectId(id)), $set("userMetadata" -> md), false, false, WriteConcern.Safe)
  }

  def addXMLMetadata(id: String, json: String) {
    Logger.debug("Adding/modifying XML file metadata to file " + id + " : " + json)
    val md = com.mongodb.util.JSON.parse(json).asInstanceOf[DBObject]
    FileDAO.update(MongoDBObject("_id" -> new ObjectId(id)), $set("xmlMetadata" -> md), false, false, WriteConcern.Safe)
  }


  def findByTag(tag: String): List[File] = {
    FileDAO.find(MongoDBObject("tags.name" -> tag)).toList
  }

  def findIntermediates(): List[File] = {
    FileDAO.find(MongoDBObject("isIntermediate" -> true)).toList
  }

  // ---------- Tags related code starts ------------------
  // Input validation is done in api.Files, so no need to check again.
  def addTags(id: String, userIdStr: Option[String], eid: Option[String], tags: List[String]) {
    Logger.debug("Adding tags to file " + id + " : " + tags)
    val file = get(id).get
    val existingTags = file.tags.filter(x => userIdStr == x.userId && eid == x.extractor_id).map(_.name)
    val createdDate = new Date
    tags.foreach(tag => {
      // Only add tags with new values.
      if (!existingTags.contains(tag)) {
        val tagObj = Tag(id = new ObjectId, name = tag, userId = userIdStr, extractor_id = eid, created = createdDate)
        FileDAO.update(MongoDBObject("_id" -> new ObjectId(id)), $addToSet("tags" -> Tag.toDBObject(tagObj)), false, false, WriteConcern.Safe)
      }
    })
  }

  def removeAllTags(id: String) {
    FileDAO.update(MongoDBObject("_id" -> new ObjectId(id)), $set("tags" -> List()), false, false, WriteConcern.Safe)
  }
  // ---------- Tags related code ends ------------------

  def comment(id: String, comment: Comment) {
    FileDAO.update(MongoDBObject("_id" -> new ObjectId(id)), $addToSet("comments" -> Comment.toDBObject(comment)), false, false, WriteConcern.Safe)
  }

  def setIntermediate(id: String){
    FileDAO.update(MongoDBObject("_id" -> new ObjectId(id)), $set("isIntermediate" -> Some(true)), false, false, WriteConcern.Safe)
  }

  def renameFile(id: String, newName: String){
    FileDAO.update(MongoDBObject("_id" -> new ObjectId(id)), $set("filename" -> newName), false, false, WriteConcern.Safe)
  }

  def setContentType(id: String, newType: String){
    FileDAO.update(MongoDBObject("_id" -> new ObjectId(id)), $set("contentType" -> newType), false, false, WriteConcern.Safe)
  }

  def setUserMetadataWasModified(id: String, wasModified: Boolean){
    FileDAO.update(MongoDBObject("_id" -> new ObjectId(id)), $set("userMetadataWasModified" -> Some(wasModified)), false, false, WriteConcern.Safe)
  }

  def removeFile(id: String){
    get(id) match{
      case Some(file) => {
        if(file.isIntermediate.isEmpty){
          val fileDatasets = datasets.findByFileId(file.id)
          for(fileDataset <- fileDatasets){
            datasets.removeFile(fileDataset.id.toString(), id)
            if(!file.xmlMetadata.isEmpty){
              datasets.index(fileDataset.id.toString())
            }
            if(!file.thumbnail_id.isEmpty && !fileDataset.thumbnail_id.isEmpty)
              if(file.thumbnail_id.get == fileDataset.thumbnail_id.get)
                datasets.newThumbnail(fileDataset.id.toString())
          }
          for(section <- SectionDAO.findByFileId(file.id)){
            SectionDAO.removeSection(section)
          }
          for(preview <- PreviewDAO.findByFileId(file.id)){
            PreviewDAO.removePreview(preview)
          }
          for(comment <- Comment.findCommentsByFileId(id)){
            Comment.removeComment(comment)
          }
          for(texture <- ThreeDTextureDAO.findTexturesByFileId(file.id)){
            ThreeDTextureDAO.remove(MongoDBObject("_id" -> texture.id))
          }
          if(!file.thumbnail_id.isEmpty)
            Thumbnail.remove(MongoDBObject("_id" -> file.thumbnail_id.get))
        }
        remove(MongoDBObject("_id" -> file.id))
      }
      case None =>
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
        }else{
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
      removeFile(file.id.toString())
  }

  /**
   * Update thumbnail used to represent this dataset.
   */
  def updateThumbnail(fileId: String, thumbnailId: String) {
    FileDAO.update(MongoDBObject("_id" -> new ObjectId(fileId)),
      $set("thumbnail_id" -> new ObjectId(thumbnailId)), false, false, WriteConcern.Safe)
  }

}
