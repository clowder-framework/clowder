/**
 *
 */
package services.mongodb

import services.{ElasticsearchPlugin, CollectionService, DatasetService}
import models._
import com.mongodb.casbah.commons.MongoDBObject
import java.text.SimpleDateFormat
import play.api.Logger
import Transformation.LidoToCidocConvertion
import java.util.{Date, ArrayList}
import java.io._
import org.apache.commons.io.FileUtils
import org.json.JSONObject
import play.api.libs.json.JsValue
import play.api.libs.json.Json._
import javax.inject.{Singleton, Inject}
import com.mongodb.casbah.WriteConcern
import com.mongodb.util.JSON
import jsonutils.JsonUtil
import com.mongodb.casbah.Imports._
import scala.collection.mutable.ListBuffer
import play.api.Play._
import scala.Some
import scala.util.parsing.json.JSONArray
import models.File
import collection.JavaConverters._
import scala.collection.JavaConversions._

/**
 * Use Mongodb to store datasets.
 * 
 * @author Luigi Marini
 *
 */
@Singleton
class MongoDBDatasetService  @Inject() (collections: CollectionService)  extends DatasetService {

  /**
   * List all datasets in the system.
   */
  def listDatasets(): List[Dataset] = {
    (for (dataset <- Dataset.find(MongoDBObject())) yield dataset).toList
  }

  /**
   * List all datasets in the system in reverse chronological order.
   */
  def listDatasetsChronoReverse(): List[Dataset] = {
    val order = MongoDBObject("created"-> -1)
    Dataset.findAll.sort(order).toList
  }

  /**
   * List datasets after a specified date.
   */
  def listDatasetsAfter(date: String, limit: Int): List[Dataset] = {
    val order = MongoDBObject("created"-> -1)
    if (date == "") {
      Dataset.findAll.sort(order).limit(limit).toList
    } else {
      val sinceDate = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss").parse(date)
      Logger.info("After " + sinceDate)
      Dataset.find("created" $lt sinceDate).sort(order).limit(limit).toList
    }
  }

  /**
   * List datasets before a specified date.
   */
  def listDatasetsBefore(date: String, limit: Int): List[Dataset] = {
    var order = MongoDBObject("created"-> -1)
    if (date == "") {
      Dataset.findAll.sort(order).limit(limit).toList
    } else {
      order = MongoDBObject("created"-> 1)
      val sinceDate = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss").parse(date)
      Logger.info("Before " + sinceDate)
      var datasetList = Dataset.find("created" $gt sinceDate).sort(order).limit(limit + 1).toList.reverse
      datasetList = datasetList.filter(_ != datasetList.last)
      datasetList
    }
  }

  /**
   * List all datasets inside a collection.
   */
  def listInsideCollection(collectionId: String) : List[Dataset] =  {
    Collection.findOneById(new ObjectId(collectionId)) match{
      case Some(collection) => {
        val list = for (dataset <- listDatasetsChronoReverse; if(isInCollection(dataset,collection))) yield dataset
        return list
      }
      case None =>{
        return List.empty
      }
    }
  }
  def isInCollection(dataset: Dataset, collection: Collection): Boolean = {
    for(collDataset <- collection.datasets){
      if(collDataset.id == dataset.id)
        return true
    }
    return false
  }

  /**
   * Get dataset.
   */
  def get(id: String): Option[Dataset] = {
    Dataset.findOneById(new ObjectId(id))
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
  def getFileId(datasetId: String, filename: String): Option[String] = {
    get(datasetId) match {
      case Some(dataset) => {
        for (file <- dataset.files) {
          if (file.filename.equals(filename)) {
            return Some(file.id.toString)
          }
        }
        Logger.error("File does not exist in dataset" + datasetId); return None
      }
      case None => { Logger.error("Error getting dataset" + datasetId); return None }
    }
  }

  def modifyRDFOfMetadataChangedDatasets(){
    val changedDatasets = findMetadataChangedDatasets()
    for(changedDataset <- changedDatasets){
      modifyRDFUserMetadata(changedDataset.id.toString)
    }
  }

  def modifyRDFUserMetadata(id: String, mappingNumber: String="1") = {
    services.Services.rdfSPARQLService.removeDatasetFromUserGraphs(id)
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

        services.Services.rdfSPARQLService.addFromFile(id, resultFileConnected, "dataset")
        resultFileConnected.delete()

        services.Services.rdfSPARQLService.addDatasetToGraph(id, "rdfCommunityGraphName")

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
      "created" -> dataset.created.toString, "thumbnail" -> datasetThumbnail))
  }

  def isInCollection(datasetId: String, collectionId: String): Boolean = {

    Collection.findOneById(new ObjectId(collectionId)) match {
      case Some(col) => {
        for (d <- col.datasets) {
          if (d.id == new ObjectId(datasetId))
            return true
        }
        return false
      }
      case None => {
        return false
      }
    }
  }

  def updateThumbnail(datasetId: String, thumbnailId: String) {
    Dataset.dao.collection.update(MongoDBObject("_id" -> new ObjectId(datasetId)),
      $set("thumbnail_id" -> new ObjectId(thumbnailId)), false, false, WriteConcern.Safe)
  }

  def createThumbnail(datasetId: String) {
    Dataset.findOneById(new ObjectId(datasetId)) match {
      case Some(dataset) => {
        val files = dataset.files map {
          f => files.get(f.id.toString).getOrElse(None)
        }
        for (file <- files) {
          if (file.isInstanceOf[models.File]) {
            val theFile = file.asInstanceOf[models.File]
            if (!theFile.thumbnail_id.isEmpty) {
              Dataset.update(MongoDBObject("_id" -> new ObjectId(datasetId)), $set("thumbnail_id" -> theFile.thumbnail_id.get), false, false, WriteConcern.Safe)
              return
            }
          }
        }
        Dataset.update(MongoDBObject("_id" -> new ObjectId(datasetId)), $set("thumbnail_id" -> None), false, false, WriteConcern.Safe)
      }
      case None => Logger.debug(s"Dataset $datasetId not found")
    }
  }

  def selectNewThumbnailFromFiles(datasetId: String) {
    Dataset.findOneById(new ObjectId(datasetId)) match {
      case Some(dataset) => {
        // TODO cleanup
        val files = dataset.files.map(f => files.get(f.id.toString).getOrElse(None))
        for (file <- files) {
          if (file.isInstanceOf[File]) {
            val theFile = file.asInstanceOf[File]
            if (!theFile.thumbnail_id.isEmpty) {
              Dataset.update(MongoDBObject("_id" -> new ObjectId(datasetId)), $set("thumbnail_id" -> theFile.thumbnail_id.get), false, false, WriteConcern.Safe)
              return
            }
          }
        }
        Dataset.update(MongoDBObject("_id" -> new ObjectId(datasetId)), $set("thumbnail_id" -> None), false, false, WriteConcern.Safe)
      }
      case None => Logger.debug("No dataset found with id " + datasetId)
    }
  }

  def findOneByFileId(file_id: ObjectId): Option[Dataset] = {
    Dataset.dao.findOne(MongoDBObject("files._id" -> file_id))
  }

  def findByFileId(file_id: ObjectId): List[Dataset] = {
    Dataset.dao.find(MongoDBObject("files._id" -> file_id)).toList
  }

  def findNotContainingFile(file_id: ObjectId): List[Dataset] = {
    val listContaining = findByFileId(file_id)
    (for (dataset <- Dataset.find(MongoDBObject())) yield dataset).toList.filterNot(listContaining.toSet)
  }

  def findByTag(tag: String): List[Dataset] = {
    Dataset.dao.find(MongoDBObject("tags.name" -> tag)).toList
  }

  def getMetadata(id: String): Map[String, Any] = {
    Dataset.dao.collection.findOne(MongoDBObject("_id" -> new ObjectId(id)), MongoDBObject("metadata" -> 1)) match {
      case None => Map.empty
      case Some(x) => {
        x.getAs[DBObject]("metadata").get.toMap.asScala.asInstanceOf[scala.collection.mutable.Map[String, Any]].toMap
      }
    }
  }

  def getUserMetadata(id: String): scala.collection.mutable.Map[String, Any] = {
    Dataset.dao.collection.findOne(MongoDBObject("_id" -> new ObjectId(id)), MongoDBObject("userMetadata" -> 1)) match {
      case None => new scala.collection.mutable.HashMap[String, Any]
      case Some(x) => {
        val returnedMetadata = x.getAs[DBObject]("userMetadata").get.toMap.asScala.asInstanceOf[scala.collection.mutable.Map[String, Any]]
        returnedMetadata
      }
    }
  }

  def getUserMetadataJSON(id: String): String = {
    Dataset.dao.collection.findOneByID(new ObjectId(id)) match {
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

  def getTechnicalMetadataJSON(id: String): String = {
    Dataset.dao.collection.findOneByID(new ObjectId(id)) match {
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

  def getXMLMetadataJSON(id: String): String = {
    Dataset.dao.collection.findOneByID(new ObjectId(id)) match {
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

  def addMetadata(id: String, json: String) {
    Logger.debug(s"Adding metadata to dataset " + id + " : " + json)
    val md = JSON.parse(json).asInstanceOf[DBObject]
    Dataset.dao.collection.findOne(MongoDBObject("_id" -> new ObjectId(id)), MongoDBObject("metadata" -> 1)) match {
      case None => {
        Dataset.update(MongoDBObject("_id" -> new ObjectId(id)), $set("metadata" -> md), false, false, WriteConcern.Safe)
      }
      case Some(x) => {
        x.getAs[DBObject]("metadata") match {
          case Some(map) => {
            val union = map.asInstanceOf[DBObject] ++ md
            Dataset.update(MongoDBObject("_id" -> new ObjectId(id)), $set("metadata" -> union), false, false, WriteConcern.Safe)
          }
          case None => Map.empty
        }
      }
    }
  }

  def addXMLMetadata(id: String, fileId: String, json: String) {
    Logger.debug("Adding XML metadata to dataset " + id + " from file " + fileId + ": " + json)
    val md = JsonUtil.parseJSON(json).asInstanceOf[java.util.LinkedHashMap[String, Any]].toMap
    Dataset.update(MongoDBObject("_id" -> new ObjectId(id)), $addToSet("datasetXmlMetadata" -> DatasetXMLMetadata.toDBObject(DatasetXMLMetadata(md, fileId))), false, false, WriteConcern.Safe)
  }

  def removeXMLMetadata(id: String, fileId: String) {
    Logger.debug("Removing XML metadata belonging to file " + fileId + " from dataset " + id + ".")
    Dataset.update(MongoDBObject("_id" -> new ObjectId(id)), $pull("datasetXmlMetadata" -> MongoDBObject("fileId" -> fileId)), false, false, WriteConcern.Safe)
  }

  def addUserMetadata(id: String, json: String) {
    Logger.debug("Adding/modifying user metadata to dataset " + id + " : " + json)
    val md = com.mongodb.util.JSON.parse(json).asInstanceOf[DBObject]
    Dataset.update(MongoDBObject("_id" -> new ObjectId(id)), $set("userMetadata" -> md), false, false, WriteConcern.Safe)
  }

  def addTags(id: String, userIdStr: Option[String], eid: Option[String], tags: List[String]) {
    Logger.debug("Adding tags to dataset " + id + " : " + tags)
    // TODO: Need to check for the owner of the dataset before adding tag

    val dataset = get(id).get
    val existingTags = dataset.tags.filter(x => userIdStr == x.userId && eid == x.extractor_id).map(_.name)
    val createdDate = new Date
    tags.foreach(tag => {
      // Only add tags with new values.
      if (!existingTags.contains(tag)) {
        val tagObj = Tag(id = new ObjectId, name = tag, userId = userIdStr, extractor_id = eid, created = createdDate)
        Dataset.update(MongoDBObject("_id" -> new ObjectId(id)), $addToSet("tags" -> Tag.toDBObject(tagObj)), false, false, WriteConcern.Safe)
      }
    })
  }

  def setUserMetadataWasModified(id: String, wasModified: Boolean) {
    Dataset.update(MongoDBObject("_id" -> new ObjectId(id)), $set("userMetadataWasModified" -> Some(wasModified)), false, false, WriteConcern.Safe)
  }

  def findMetadataChangedDatasets(): List[Dataset] = {
    Dataset.find(MongoDBObject("userMetadataWasModified" -> true)).toList
  }

  def removeTag(id: String, tagId: String) {
    Logger.debug("Removing tag " + tagId)
    val result = Dataset.update(MongoDBObject("_id" -> new ObjectId(id)), $pull("tags" -> MongoDBObject("_id" -> new ObjectId(tagId))), false, false, WriteConcern.Safe)
  }

  def removeTags(id: String, userIdStr: Option[String], eid: Option[String], tags: List[String]) {
    Logger.debug("Removing tags in dataset " + id + " : " + tags + ", userId: " + userIdStr + ", eid: " + eid)
    val dataset = get(id).get
    val existingTags = dataset.tags.filter(x => userIdStr == x.userId && eid == x.extractor_id).map(_.name)
    Logger.debug("existingTags after user and extractor filtering: " + existingTags.toString)
    // Only remove existing tags.
    tags.intersect(existingTags).map {
      tag =>
        Dataset.update(MongoDBObject("_id" -> new ObjectId(id)), $pull("tags" -> MongoDBObject("name" -> tag)), false, false, WriteConcern.Safe)
    }
  }

  def removeAllTags(id: String) {
    Dataset.update(MongoDBObject("_id" -> new ObjectId(id)), $set("tags" -> List()), false, false, WriteConcern.Safe)
  }

  // ---------- Tags related code ends ------------------

  /**
   * Check recursively whether a dataset's user-input metadata match a requested search tree.
   */
  def searchUserMetadata(id: String, requestedMetadataQuery: Any): Boolean = {
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
              builder += MongoDBObject(actualKey -> MongoDBObject("$ne" -> currValue))
            }
            else {
              builder += MongoDBObject(actualKey -> currValue)
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
                  objectForEach += MongoDBObject(tempActualKey -> MongoDBObject("$ne" -> currValue))
                }
                else {
                  objectForEach += MongoDBObject(tempActualKey -> currValue)
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
  def searchMetadata(id: String, requestedMap: java.util.LinkedHashMap[String, Any], currentMap: scala.collection.mutable.Map[String, Any]): Boolean = {
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

  def addFile(datasetId: String, file: File) {
    Dataset.update(MongoDBObject("_id" -> new ObjectId(datasetId)), $addToSet("files" -> FileDAO.toDBObject(file)), false, false, WriteConcern.Safe)
    if (!file.xmlMetadata.isEmpty) {
      addXMLMetadata(datasetId, file.id.toString, FileDAO.getXMLMetadataJSON(file.id.toString))
    }
  }

  def addCollection(datasetId: String, collectionId: String) {
    Dataset.update(MongoDBObject("_id" -> new ObjectId(datasetId)), $addToSet("collections" -> collectionId), false, false, WriteConcern.Safe)
  }

  def removeCollection(datasetId: String, collectionId: String) {
    Dataset.update(MongoDBObject("_id" -> new ObjectId(datasetId)), $pull("collections" -> collectionId), false, false, WriteConcern.Safe)
  }

  def removeFile(datasetId: String, fileId: String) {
    Dataset.update(MongoDBObject("_id" -> new ObjectId(datasetId)), $pull("files" -> MongoDBObject("_id" -> new ObjectId(fileId))), false, false, WriteConcern.Safe)
    removeXMLMetadata(datasetId, fileId)
  }

  def newThumbnail(datasetId: String) {
    Dataset.findOneById(new ObjectId(datasetId)) match {
      case Some(dataset) => {
        val files = dataset.files map {
          f => {
            files.get(f.id.toString).getOrElse {
              None
            }
          }
        }
        for (file <- files) {
          if (file.isInstanceOf[models.File]) {
            val theFile = file.asInstanceOf[models.File]
            if (!theFile.thumbnail_id.isEmpty) {
              Dataset.update(MongoDBObject("_id" -> new ObjectId(datasetId)), $set("thumbnail_id" -> theFile.thumbnail_id.get), false, false, WriteConcern.Safe)
              return
            }
          }
        }
        Dataset.update(MongoDBObject("_id" -> new ObjectId(datasetId)), $set("thumbnail_id" -> None), false, false, WriteConcern.Safe)
      }
      case None =>
    }
  }

  def removeDataset(id: String) {
    Dataset.findOneById(new ObjectId(id)) match {
      case Some(dataset) => {
        for (collection <- Collection.listInsideDataset(id))
          Collection.removeDataset(collection.id.toString, dataset)
        for (comment <- Comment.findCommentsByDatasetId(id)) {
          Comment.removeComment(comment)
        }
        for (f <- dataset.files) {
          var notTheDataset = for (currDataset <- findByFileId(f.id) if !dataset.id.toString.equals(currDataset.id.toString)) yield currDataset
          if (notTheDataset.size == 0)
            files.removeFile(f.id.toString)
        }
        Dataset.remove(MongoDBObject("_id" -> dataset.id))
      }
      case None =>
    }
  }

  def index(id: String) {
    Dataset.findOneById(new ObjectId(id)) match {
      case Some(dataset) => {
        var tagListBuffer = new ListBuffer[String]()

        for (tag <- dataset.tags) {
          tagListBuffer += tag.name
        }

        val tagsJson = new JSONArray(tagListBuffer.toList)

        Logger.debug("tagStr=" + tagsJson);

        val comments = for (comment <- Comment.findCommentsByDatasetId(id, false)) yield {
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

        current.plugin[ElasticsearchPlugin].foreach {
          _.index("data", "dataset", id,
            List(("name", dataset.name), ("description", dataset.description), ("tag", tagsJson.toString), ("comments", commentJson.toString), ("usermetadata", usrMd), ("technicalmetadata", techMd), ("xmlmetadata", xmlMd)))
        }
      }
      case None => Logger.error("Dataset not found: " + id)
    }
  }



}