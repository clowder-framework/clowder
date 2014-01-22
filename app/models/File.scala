/**
 *
 */
package models

import java.util.Date
import org.bson.types.ObjectId
import com.novus.salat.dao.{ModelCompanion, SalatDAO}
import MongoContext.context
import play.api.Play.current
import services.MongoSalatPlugin
import com.mongodb.casbah.commons.MongoDBObject
import com.mongodb.casbah.WriteConcern
import com.mongodb.casbah.Imports._
import collection.JavaConverters._
import scala.collection.JavaConversions._
import securesocial.core.Identity
import play.api.Logger
import java.util.Calendar
import java.nio.file.Files
import java.nio.file.attribute.BasicFileAttributes
import java.nio.file.FileSystems

import scala.util.parsing.json.JSON
import play.api.libs.json.Json
import play.api.libs.json.JsValue
import play.api.libs.json.JsObject
import play.api.libs.json.JsArray


/**
 * Uploaded files.
 * 
 * @author Luigi Marini
 *
 */
case class File(
    id: ObjectId = new ObjectId,    
    path: Option[String] = None, 
    filename: String,
    author: Identity,
    uploadDate: Date, 
    contentType: String,
    length: Long = 0,
    showPreviews: String = "DatasetLevel",
    sections: List[Section] = List.empty,
    previews: List[Preview] = List.empty,
    tags: List[Tag] = List.empty,
    metadata: List[Map[String, Any]] = List.empty,
	thumbnail_id: Option[String] = None,
	isIntermediate: Option[Boolean] = None,
	userMetadata: Map[String, Any] = Map.empty,
	xmlMetadata: Map[String, Any] = Map.empty
)

object FileDAO extends ModelCompanion[File, ObjectId] {
  // TODO RK handle exception for instance if we switch to other DB
  val dao = current.plugin[MongoSalatPlugin] match {
    case None    => throw new RuntimeException("No MongoSalatPlugin");
    case Some(x) =>  new SalatDAO[File, ObjectId](collection = x.collection("uploads.files")) {}
  }
  
  def get(id: String): Option[File] = {
    dao.findOneById(new ObjectId(id)) match {
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
    Dataset.findOneById(new ObjectId(dataset_id)) match{
        case Some(dataset) => {
          val list = for (file <- findAll(); if(!isInDataset(file,dataset) && !file.isIntermediate.getOrElse(false))) yield file
          return list.toList
        }
        case None =>{
          return findAll.toList
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
		  dao.collection.findOneByID(new ObjectId(id)) match {
		  case None => new scala.collection.immutable.HashMap[String,Any]
		  case Some(x) => {
			  val returnedMetadata = x.getAs[DBObject]("metadata").get.toMap.asScala.asInstanceOf[scala.collection.mutable.Map[String,Any]].toMap
					  returnedMetadata
		  }
	  }
  }
    
  def getUserMetadata(id: String): scala.collection.mutable.Map[String,Any] = {
    dao.collection.findOneByID(new ObjectId(id)) match {
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
    dao.collection.findOneByID(new ObjectId(id)) match {
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
    dao.collection.findOneByID(new ObjectId(id)) match {
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
  
  def getXMLMetadataJSON(id: String): String = {
    dao.collection.findOneByID(new ObjectId(id)) match {
      case None => "{}"
      case Some(x) => {
        x.getAs[DBObject]("xmlMetadata") match{
          case Some(y)=>{
	    	val returnedMetadata = com.mongodb.util.JSON.serialize(x.getAs[DBObject]("xmlMetadata").get)
			returnedMetadata
          }
          case None => "{}"
		}
      }
    }
  }

  
  /* add versus descriptors to the metadata
   * 
   * Reads descriptors received from versus( in the response body)  as list of JSON objects 
   * Each JSON object has four fields <extraction_id,adapter_name,extractor_name,descriptor>
   * Parse each json object based on the field/key name and obtain the values and combine them as a tuple  
   * Obtain the existing versus descriptors in the "metadata" as list of tuples (extraction_id,adapter_name,extractor_name,descriptor)
   * merge with the tuples obtained from descriptors received from versus
   * write it back to the versus_descriptors field of "metadata" to monoDB
   *
   *  */

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
    dao.update(MongoDBObject("_id" -> new ObjectId(id)), $set("userMetadata" -> md), false, false, WriteConcern.Safe)
  }
  
  def addXMLMetadata(id: String, json: String) {
    Logger.debug("Adding/modifying XML file metadata to file " + id + " : " + json)
    val md = com.mongodb.util.JSON.parse(json).asInstanceOf[DBObject]
    dao.update(MongoDBObject("_id" -> new ObjectId(id)), $set("xmlMetadata" -> md), false, false, WriteConcern.Safe)
  }
  

  def findByTag(tag: String): List[File] = {
    dao.find(MongoDBObject("tags.name" -> tag)).toList
  }
  
  def findIntermediates(): List[File] = {
    dao.find(MongoDBObject("isIntermediate" -> true)).toList
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
        dao.collection.update(MongoDBObject("_id" -> new ObjectId(id)), $addToSet("tags" -> Tag.toDBObject(tagObj)), false, false, WriteConcern.Safe)
      }
    })
  }

  def removeTags(id: String, userIdStr: Option[String], eid: Option[String], tags: List[String]) {
    Logger.debug("Removing tags in file " + id + " : " + tags + ", userId: " + userIdStr + ", eid: " + eid)
    val file = get(id).get
    val existingTags = file.tags.filter(x => userIdStr == x.userId && eid == x.extractor_id).map(_.name)
    Logger.debug("existingTags after user and extractor filtering: " + existingTags.toString)
    // Only remove existing tags.
    tags.intersect(existingTags).map { tag =>
      dao.collection.update(MongoDBObject("_id" -> new ObjectId(id)), $pull("tags" -> MongoDBObject("name" -> tag)), false, false, WriteConcern.Safe)
    }
  }

  def removeAllTags(id: String) {
    dao.collection.update(MongoDBObject("_id" -> new ObjectId(id)), $set("tags" -> List()), false, false, WriteConcern.Safe)
  }
  // ---------- Tags related code ends ------------------

  def comment(id: String, comment: Comment) {
    dao.update(MongoDBObject("_id" -> new ObjectId(id)), $addToSet("comments" -> Comment.toDBObject(comment)), false, false, WriteConcern.Safe)
  }
  
  def setIntermediate(id: String){
    dao.update(MongoDBObject("_id" -> new ObjectId(id)), $set("isIntermediate" -> Some(true)), false, false, WriteConcern.Safe)
  }
  
  def removeFile(id: String){
    dao.findOneById(new ObjectId(id)) match{
      case Some(file) => {
        if(file.isIntermediate.isEmpty){
        	val fileDatasets = Dataset.findByFileId(file.id)
        	for(fileDataset <- fileDatasets){
	        	Dataset.removeFile(fileDataset.id.toString(), id)
	        	if(!file.xmlMetadata.isEmpty){
	            	Dataset.index(fileDataset.id.toString())
		      	}
	        	if(!file.thumbnail_id.isEmpty && !fileDataset.thumbnail_id.isEmpty)
		        	if(file.thumbnail_id.get == fileDataset.thumbnail_id.get)
		        	  Dataset.newThumbnail(fileDataset.id.toString())
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
        FileDAO.remove(MongoDBObject("_id" -> file.id))
      }
      case None =>
    }    
  }
  
  def removeTemporaries(){
    val cal = Calendar.getInstance()
    val timeDiff = play.Play.application().configuration().getInt("rdfTempCleanup.removeAfter")
    cal.add(Calendar.MINUTE, -timeDiff)
    val oldDate = cal.getTime()    
    
    val folder = new java.io.File(play.api.Play.configuration.getString("rdfdumptemporary.dir").getOrElse(""))
    val listOfFiles = folder.listFiles()
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
  
  def searchAllMetadataFormulateQuery(requestedMetadataQuery: Any): List[File] = {
    Logger.debug("top: "+ requestedMetadataQuery.asInstanceOf[java.util.LinkedHashMap[String,Any]].toString()  )
    
    var theQuery =  searchMetadataFormulateQuery(requestedMetadataQuery.asInstanceOf[java.util.LinkedHashMap[String,Any]], "all")
    Logger.debug("thequery: "+theQuery.toString)    
    var fileList = dao.find(theQuery).toList
       
    return fileList
  }
  
  
  def searchUserMetadataFormulateQuery(requestedMetadataQuery: Any): List[File] = {
    Logger.debug("top: "+ requestedMetadataQuery.asInstanceOf[java.util.LinkedHashMap[String,Any]].toString()  )
    var theQuery =  searchMetadataFormulateQuery(requestedMetadataQuery.asInstanceOf[java.util.LinkedHashMap[String,Any]], "userMetadata")
    Logger.debug("thequery: "+theQuery.toString)
    
    val fileList = dao.find(theQuery).toList
    return fileList
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
    queryMap.add(builder.result)
    
    if(orFound){
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
    for(removeFile <- fileList)
      FileDAO.removeFile(removeFile.id.toString())
  }
  
}
