package services.mongodb

import java.io.InputStream
import com.mongodb.casbah.commons.MongoDBObject
import com.mongodb.casbah.Imports._
import com.mongodb.casbah.gridfs.GridFS
import services.MultimediaQueryService
import com.novus.salat.dao.{SalatMongoCursor, ModelCompanion, SalatDAO}
import MongoContext.context
import play.api.Play.current
import models.{UUID, TempFile, MultimediaFeatures}
import scala.Some
import play.api.libs.json.JsObject
import play.api.Logger

class MongoDBMultimediaQueryService extends MultimediaQueryService {

   /**
   * Update thumbnail used to represent this query.
   */
  def updateThumbnail(queryId: UUID, thumbnailId: UUID) {
    TempFileDAO.update(MongoDBObject("_id" -> new ObjectId(queryId.stringify)),
    	//because of a bug in Salat have to explicitely cast to ObjectId
    	$set("thumbnail_id" -> new ObjectId(thumbnailId.stringify)), false, false, WriteConcern.Safe)
  }
  
  
  def save(inputStream: InputStream, filename: String, contentType: Option[String]): Option[TempFile] = {
    val files = current.plugin[MongoSalatPlugin] match {
      case None    => throw new RuntimeException("No MongoSalatPlugin");
      case Some(x) =>  x.gridFS("uploadquery")
    }
    
    // required to avoid race condition on save
    files.db.setWriteConcern(WriteConcern.Safe)
    
    val mongoFile = files.createFile(inputStream)
    Logger.debug("MongoDBMultimediaQueryService.save Uploading file " + filename)
    mongoFile.filename = filename
    var ct = contentType.getOrElse(play.api.http.ContentTypes.BINARY)
    if (ct == play.api.http.ContentTypes.BINARY) {
      ct = play.api.libs.MimeTypes.forFileName(filename).getOrElse(play.api.http.ContentTypes.BINARY)
    }
    mongoFile.contentType = ct
    mongoFile.save
    val oid = mongoFile.getAs[ObjectId]("_id").get
    Some(TempFile(UUID(oid.toString), None, mongoFile.filename.get, mongoFile.uploadDate, mongoFile.contentType.get, mongoFile.length))
  }
  
   def get(id: UUID): Option[(InputStream, String, String, Long)] = {
    
     val queries = GridFS(MultimediaFeaturesDAO.dao.collection.db, "uploadquery")
    
      queries.findOne(MongoDBObject("_id" -> new ObjectId(id.stringify))) match {
      
      case Some(query) => {
        //Logger.debug(query.toString())
        Logger.debug("get: file name: "+query.filename +" Query id ="+ query.id.toString())
        Some(query.inputStream, 
          query.getAs[String]("filename").getOrElse("unknown-name"), 
          query.getAs[String]("contentType").getOrElse("unknown"),
          query.getAs[Long]("length").getOrElse(0))
          }
      case None => None
    }
  }
  
def listFiles(): List[TempFile]={
  (for (file <- TempFileDAO.find(MongoDBObject())) yield file).toList
  }
  
  
  /**
   * Get file metadata.
   */
def getFile(id: UUID): Option[TempFile] = {
    TempFileDAO.findOne(MongoDBObject("_id" -> new ObjectId(id.stringify)))
  }

  /**
   * Store file metadata.
   */
  def storeFileMD(id: UUID, filename: String, contentType: Option[String]): Option[TempFile]={
     val files = current.plugin[MongoSalatPlugin] match {
      case None    => throw new RuntimeException("No MongoSalatPlugin");
      case Some(x) =>  x.gridFS("uploadquery")
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
    mongoFile.save
    val oid = mongoFile.getAs[ObjectId]("_id").get ///getting object id
    //mongoFile._id
    Logger.debug("StoreMD id="+ oid)
     Some(TempFile(UUID(oid.toString), None, mongoFile.filename.get, mongoFile.uploadDate, mongoFile.contentType.get, mongoFile.length))
    
  }

  def findFeatureBySection(sectionId: UUID): Option[MultimediaFeatures] = {
    MultimediaFeaturesDAO.findOne(MongoDBObject("section_id"-> new ObjectId(sectionId.stringify)))
  }

  def updateFeatures(multimediaFeature: MultimediaFeatures, sectionId: UUID, features: List[JsObject]) {
    val builder = MongoDBObject.newBuilder
    builder += "section_id" -> sectionId
    val listBuilder = MongoDBList.newBuilder
    features.map {f =>
      val featureBuilder = MongoDBObject.newBuilder
      featureBuilder += "representation" -> (f \ "representation").as[String]
      featureBuilder += "descriptor" -> (f \ "descriptor").as[List[Double]]
      listBuilder += featureBuilder.result
    }
    multimediaFeature.features.map {f =>
      val featureBuilder = MongoDBObject.newBuilder
      featureBuilder += "representation" -> f.representation
      featureBuilder += "descriptor" -> f.descriptor
      listBuilder += featureBuilder.result
    }
    builder += "features" -> listBuilder.result
    Logger.debug("Features doc " + multimediaFeature.id + " updated")
    MultimediaFeaturesDAO.update(MongoDBObject("_id" -> new ObjectId(multimediaFeature.id.stringify)), builder.result, false, false, WriteConcern.Safe)
  }

  def insert(multimediaFeature: MultimediaFeatures) {
    MultimediaFeaturesDAO.save(multimediaFeature)
  }

  def listAll(): SalatMongoCursor[MultimediaFeatures] = {
    MultimediaFeaturesDAO.find(MongoDBObject())
  }
}

object MultimediaFeaturesDAO extends ModelCompanion[MultimediaFeatures, ObjectId] {
  val dao = current.plugin[MongoSalatPlugin] match {
    case None => throw new RuntimeException("No MongoSalatPlugin");
    case Some(x) => new SalatDAO[MultimediaFeatures, ObjectId](collection = x.collection("multimedia.features")) {}
  }
}
