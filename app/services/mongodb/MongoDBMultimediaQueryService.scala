package services.mongodb

import java.io.InputStream
import java.util.concurrent.TimeUnit

import com.mongodb.casbah.Imports._
import com.mongodb.casbah.commons.MongoDBObject
import com.mongodb.casbah.gridfs.GridFS
import com.novus.salat.dao.{ModelCompanion, SalatDAO, SalatMongoCursor}
import edu.illinois.ncsa.isda.lsva.ImageDescriptors.FeatureType
import edu.illinois.ncsa.isda.lsva.ImageMeasures
import models.{MultimediaDistance, MultimediaFeatures, TempFile, UUID}
import play.api.Logger
import play.api.Play.current
import play.api.libs.json.JsObject
import services.MultimediaQueryService
import services.mongodb.MongoContext.context

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

  def addMultimediaDistance(d: MultimediaDistance): Unit = {
    MultimediaDistanceDAO.save(d)
  }

  def searchMultimediaDistances(querySectionId: String, representation: String, limit: Int): List[MultimediaDistance] = {
    MultimediaDistanceDAO.find(MongoDBObject("source_section"->new ObjectId(querySectionId),"representation"->representation))
      .sort(MongoDBObject("distance" -> 1)).limit(limit).toList
  }

  def recomputeAllDistances(): Unit = {
    // drop existing distances
    Logger.debug("Dropping mongo collection multimedia.distances")
    MultimediaDistanceDAO.dao.collection.drop()
    totalTime {
      Logger.debug("Precomputing distances")
      val outer = listAll()
      // don't let the cursor time out
      outer.underlying.addOption(com.mongodb.Bytes.QUERYOPTION_NOTIMEOUT)
      while (outer.hasNext) {
        val source = outer.next
        time {
          computeDistances(source)
        }
      }
      outer.close()
      Logger.debug("Done precomputing distances")
    }
  }

  def computeDistances(source: MultimediaFeatures): Unit = {
    Logger.debug("Computing feature distances for section " + source.section_id.get)
    val inner = listAll()
    // don't let the cursor time out
    inner.underlying.addOption(com.mongodb.Bytes.QUERYOPTION_NOTIMEOUT)
    while (inner.hasNext) {
      val target = inner.next
      Logger.trace("Target section = " + target.section_id.get)
      source.features.foreach { fs =>
        if (source.section_id != target.section_id) {
          target.features.find(_.representation == fs.representation) match {
            case Some(ft) => {
              val distance = ImageMeasures.getDistance(FeatureType.valueOf(fs.representation),
                fs.descriptor.toArray, ft.descriptor.toArray)
              if (!distance.isNaN()) {
                // skip distance 0 for now, lot's of edge histogram distances are coming back 0
                if (distance == 0) {
                  Logger.debug(s"Skipping ${fs.representation} distance ${source.section_id.get} -> ${target.section_id.get} = $distance")
                } else {
                  addMultimediaDistance(
                    MultimediaDistance(source.section_id.get, target.section_id.get, fs.representation, distance))
                  addMultimediaDistance(
                    MultimediaDistance(target.section_id.get, source.section_id.get, fs.representation, distance)) // Adding reverse distance to complete the matrix
                  Logger.trace(s"Distance ${source.section_id.get} -> ${target.section_id.get} = $distance")
                }
              } else {
                Logger.error("Distance = NaN")
              }
            }
            case None => Logger.error(s"Feature ${fs.representation} not found for section ${target.section_id}")
          }
        }
      }
    }
    inner.close()
  }

  def time[R](block: => R): R = {
    val t0 = System.nanoTime()
    val result = block    // call-by-name
    val t1 = System.nanoTime()
    Logger.debug("Elapsed time: " + TimeUnit.NANOSECONDS.toSeconds((t1 - t0)) + "s")
    result
  }

  def totalTime[R](block: => R): R = {
    val t0 = System.nanoTime()
    val result = block    // call-by-name
    val t1 = System.nanoTime()
    Logger.debug("Total lapsed time: " + TimeUnit.NANOSECONDS.toSeconds((t1 - t0)) + "s")
    result
  }

}

object MultimediaFeaturesDAO extends ModelCompanion[MultimediaFeatures, ObjectId] {
  val dao = current.plugin[MongoSalatPlugin] match {
    case None => throw new RuntimeException("No MongoSalatPlugin");
    case Some(x) => new SalatDAO[MultimediaFeatures, ObjectId](collection = x.collection("multimedia.features")) {}
  }
}

object MultimediaDistanceDAO extends ModelCompanion[MultimediaDistance, ObjectId] {
  val dao = current.plugin[MongoSalatPlugin] match {
    case None => throw new RuntimeException("No MongoSalatPlugin");
    case Some(x) => new SalatDAO[MultimediaDistance, ObjectId](collection = x.collection("multimedia.distances")) {}
  }
}
