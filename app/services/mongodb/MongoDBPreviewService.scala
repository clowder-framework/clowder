package services.mongodb

import org.bson.types.ObjectId
import services.{ByteStorageService, TileService, FileService, PreviewService}
import com.mongodb.casbah.commons.MongoDBObject
import java.io.{InputStreamReader, BufferedReader, InputStream}
import play.api.Logger
import com.mongodb.casbah.gridfs.GridFS
import models._
import org.apache.http.impl.client.DefaultHttpClient
import org.apache.http.client.methods.HttpPost
import org.apache.http.entity.mime.{HttpMultipartMode, MultipartEntity}
import org.apache.http.entity.mime.content.StringBody
import java.nio.charset.Charset
import org.apache.http.util.EntityUtils
import com.novus.salat.dao.{ModelCompanion, SalatDAO}
import MongoContext.context
import play.api.Play.current
import com.mongodb.casbah.Imports._
import play.api.libs.json.JsValue
import javax.inject.{Inject, Singleton}
import models.Preview
import scala.Some
import play.api.libs.json.JsObject
import com.mongodb.casbah.commons.TypeImports.ObjectId
import com.mongodb.casbah.WriteConcern
import collection.JavaConverters._

/**
 * Created by lmarini on 2/17/14.
 */
@Singleton
class MongoDBPreviewService @Inject()(files: FileService, tiles: TileService, storage: ByteStorageService) extends PreviewService {

  def get(previewId: UUID): Option[Preview] = {
    PreviewDAO.findOneById(new ObjectId(previewId.stringify))
  }

  def setIIPReferences(id: UUID, iipURL: String, iipImage: String, iipKey: String) {
    PreviewDAO.update(MongoDBObject("_id" -> new ObjectId(id.stringify)), $set("iipURL" -> Some(iipURL), "iipImage" -> Some(iipImage), "iipKey" -> Some(iipKey)), false, false, WriteConcern.Safe)
  }

  def findByFileId(id: UUID): List[Preview] = {
    PreviewDAO.find(MongoDBObject("file_id" -> new ObjectId(id.stringify))).toList
  }

  def findBySectionId(id: UUID): List[Preview] = {
    PreviewDAO.find(MongoDBObject("section_id" -> new ObjectId(id.stringify))).toList
  }

  def findByDatasetId(id: UUID): List[Preview] = {
    PreviewDAO.find(MongoDBObject("dataset_id" -> new ObjectId(id.stringify))).toList
  }

  def findByCollectionId(id: UUID): List[Preview] = {
    PreviewDAO.find(MongoDBObject("collection_id" -> new ObjectId(id.stringify))).toList
  }

  /**
   * Save blob.
   */
  def save(inputStream: InputStream, filename: String, contentType: Option[String]): String = {
    // create the element to hold the metadata
    val files = current.plugin[MongoSalatPlugin] match {
      case None    => {
        Logger.error("No MongoSalatPlugin")
        return ""
      }
      case Some(x) =>  x.gridFS("previews")
    }

    // required to avoid race condition on save
    files.db.setWriteConcern(WriteConcern.Safe)

    // use a special case if the storage is in mongo as well
    val usemongo = current.configuration.getBoolean("medici2.mongodb.storePreviews").getOrElse(storage.isInstanceOf[MongoDBByteStorage])
    val mongoFile = if (usemongo) {
      // leverage of the fact that we save in mongo
      val x = files.createFile(inputStream)
      val id = UUID(x.getAs[ObjectId]("_id").get.toString)
      x.put("path", id)
      x
    } else {
      // write empty array
      val x = files.createFile(Array[Byte]())
      val id = UUID(x.getAs[ObjectId]("_id").get.toString)

      // save the bytes, metadata goes to mongo
      x.put("path", storage.save(inputStream, "previews", id))
      x
    }

    var ct = contentType.getOrElse(play.api.http.ContentTypes.BINARY)
    if (ct == play.api.http.ContentTypes.BINARY) {
      ct = play.api.libs.MimeTypes.forFileName(filename).getOrElse(play.api.http.ContentTypes.BINARY)
    }
    mongoFile.filename = filename
    mongoFile.contentType = ct
    mongoFile.save
    mongoFile.getAs[ObjectId]("_id").get.toString
  }

  /**
   * Get blob.
   */
  def getBlob(id: UUID): Option[(InputStream, String, String, Long)] = {
    val files = GridFS(PreviewDAO.dao.collection.db, "previews")
    files.findOne(MongoDBObject("_id" -> new ObjectId(id.stringify))) match {
      case Some(file) => {
        // use a special case if the storage is in mongo as well
        val usemongo = current.configuration.getBoolean("medici2.mongodb.storePreviews").getOrElse(storage.isInstanceOf[MongoDBByteStorage])
        val inputStream = if (usemongo) {
          file.inputStream
        } else {
          file.getAs[String]("path") match {
            case Some(path) => {
              storage.load(path, "previews") match {
                case Some(is) => is
                case None => return None
              }
            }
            case None => return None
          }
        }
        Some((inputStream,
          file.getAs[String]("filename").getOrElse("unknown-name"),
          file.getAs[String]("contentType").getOrElse("unknown"),
          file.getAs[Long]("length").getOrElse(0l)))
      }
      case None => None
    }
  }

  /**
   * Add annotation to 3D model preview.
   */
  def annotation(id: UUID, annotation: ThreeDAnnotation) {
    PreviewDAO.dao.update(MongoDBObject("_id" -> new ObjectId(id.stringify)), $addToSet("annotations" -> ThreeDAnnotation.toDBObject(annotation)), false, false, WriteConcern.Safe)
  }

  def findAnnotation(preview_id: UUID, x_coord: String, y_coord: String, z_coord: String): Option[ThreeDAnnotation] = {
    PreviewDAO.dao.findOneById(new ObjectId(preview_id.stringify)) match {
      case Some(preview) => {
        for (annotation <- preview.annotations) {
          if (annotation.x_coord.equals(x_coord) && annotation.y_coord.equals(y_coord) && annotation.z_coord.equals(z_coord))
            return Option(annotation)
        }
        return None
      }
      case None => return None
    }
  }

  def updateAnnotation(preview_id: UUID, annotation_id: UUID, description: String) {
    PreviewDAO.dao.findOneById(new ObjectId(preview_id.stringify)) match {
      case Some(preview) => {
        //var newAnnotations = List.empty[ThreeDAnnotation]
        for (annotation <- preview.annotations) {
          if (annotation.id.toString().equals(annotation_id)) {
            PreviewDAO.update(MongoDBObject("_id" -> new ObjectId(preview_id.stringify), "annotations._id" -> new ObjectId(annotation.id.stringify)), $set("annotations.$.description" -> description), false, false, WriteConcern.Safe)
            return
          }
        }
        return
      }
      case None => return
    }
  }


  def listAnnotations(preview_id: UUID): List[ThreeDAnnotation] = {
    PreviewDAO.dao.findOneById(new ObjectId(preview_id.stringify)) match {
      case Some(preview) => {
        return preview.annotations
      }
      case None => return List.empty
    }
  }

  def removePreview(p: Preview) {
    for (tile <- tiles.get(p.id)) {
      tiles.remove(tile.id)
    }
    // for IIP server references, also delete the files being referenced on the IIP server they reside
    if (!p.iipURL.isEmpty) {
      val httpclient = new DefaultHttpClient()
      val httpPost = new HttpPost(p.iipURL.get + "/deleteFile.php")
      val entity = new MultipartEntity(HttpMultipartMode.BROWSER_COMPATIBLE)
      entity.addPart("key", new StringBody(p.iipKey.get, "text/plain",
        Charset.forName("UTF-8")))
      entity.addPart("file", new StringBody(p.iipImage.get, "text/plain",
        Charset.forName("UTF-8")))
      httpPost.setEntity(entity)
      val imageUploadResponse = httpclient.execute(httpPost)
      Logger.info(imageUploadResponse.getStatusLine().toString())

      val dirEntity = imageUploadResponse.getEntity()
      Logger.info("IIP server: " + EntityUtils.toString(dirEntity))
    }

    if (!p.filename.isEmpty)
    // for oni previews, read the ONI frame references from the preview file and remove them
      if (p.filename.get.endsWith(".oniv")) {
        val theFile = getBlob(p.id)
        val frameRefReader = new BufferedReader(new InputStreamReader(theFile.get._1))
        var fileData = new StringBuilder()
        var currLine = frameRefReader.readLine()
        while (currLine != null) {
          fileData.append(currLine)
          currLine = frameRefReader.readLine()
        }
        frameRefReader.close()
        val frames = fileData.toString().split(",", -1)
        var i = 0
        for (i <- 0 to frames.length - 2) {
          PreviewDAO.remove(MongoDBObject("_id" -> new ObjectId(frames(i))))
        }
        //same for PTM file map references
      } else if (p.filename.get.endsWith(".ptmmaps")) {
        val theFile = getBlob(p.id)
        val frameRefReader = new BufferedReader(new InputStreamReader(theFile.get._1))
        var currLine = frameRefReader.readLine()
        while (currLine != null) {
          if (!currLine.equals(""))
            PreviewDAO.remove(MongoDBObject("_id" -> new ObjectId(currLine.substring(currLine.indexOf(": ") + 2))))
          currLine = frameRefReader.readLine()
        }
        frameRefReader.close()
      }

    // finally delete the actual file
    val usemongo = current.configuration.getBoolean("medici2.mongodb.storePreviews").getOrElse(storage.isInstanceOf[MongoDBByteStorage])
    if (usemongo) {
      val files = GridFS(PreviewDAO.dao.collection.db, "previews")
      files.remove(new ObjectId(p.id.stringify))
    } else {
      storage.delete(p.id.stringify, "previews")
    }
  }

  def attachToFile(previewId: UUID, fileId: UUID, extractorId: Option[String], json: JsValue) {
    json match {
      case JsObject(fields) => {
        Logger.debug("attachToFile: extractorId is '" + extractorId.toString + "'.")
        // "extractor_id" is stored at the top level of "Preview".  Remove it from the "metadata" field to avoid dup.
        val metadata = (fields.toMap - "extractor_id").flatMap(tuple => MongoDBObject(tuple._1 -> tuple._2.as[String]))
        PreviewDAO.dao.collection.update(MongoDBObject("_id" -> new ObjectId(previewId.stringify)),
          $set("metadata" -> metadata, "file_id" -> new ObjectId(fileId.stringify), "extractor_id" -> extractorId),
          false, false, WriteConcern.Safe)
        Logger.debug("Updating previews.files " + previewId + " with " + metadata)
      }
      case _ => Logger.error(s"Received something else: $json")
    }
  }

  def attachToCollection(previewId: UUID, collectionId: UUID, previewType: String, extractorId: Option[String], json: JsValue) {
    json match {
      case JsObject(fields) => {
        Logger.debug("attachToCollection: extractorId is '" + extractorId.toString + "'.")
        // "extractor_id" is stored at the top level of "Preview".  Remove it from the "metadata" field to avoid dup.
        val metadata = (fields.toMap - "extractor_id").flatMap(tuple => MongoDBObject(tuple._1 -> tuple._2.as[String]))
        PreviewDAO.dao.collection.update(MongoDBObject("_id" -> new ObjectId(previewId.stringify)),
          $set("metadata" -> metadata, "collection_id" -> new ObjectId(collectionId.stringify),
            "extractor_id" -> extractorId, "preview_type" -> previewType),
          false, false, WriteConcern.Safe)
        Logger.debug("Updating previews.collections " + previewId + " with " + metadata)
      }
      case _ => Logger.error(s"Received something else: $json")
    }
  }

  def updateMetadata(previewId: UUID, json: JsValue) {
    json match {
      case JsObject(fields) => {
        val metadata = fields.toMap.flatMap(tuple => MongoDBObject(tuple._1 -> tuple._2.as[String]))
        // TODO figure out a way to do it all together
        // aways update metadata
        PreviewDAO.dao.collection.update(
          MongoDBObject("_id" -> new ObjectId(previewId.stringify)),
          $set("metadata" -> metadata),
          false, false, WriteConcern.Safe)
        // update section_id if it exists
        if (metadata.contains("section_id")) {
          val section_id = metadata("section_id").asInstanceOf[String]
          Logger.debug("Updating previews.files " + previewId + " with section_id=" + section_id)
          PreviewDAO.dao.collection.update(
            MongoDBObject("_id" -> new ObjectId(previewId.stringify)),
            $set("section_id" -> new ObjectId(section_id)),
            false, false, WriteConcern.Safe)
        }
        // update file_id if it exists
        if (metadata.contains("file_id")) {
          val file_id = metadata("file_id").asInstanceOf[String]
          Logger.debug("Updating previews.files " + previewId + " with file_id=" + file_id)
          PreviewDAO.dao.collection.update(
            MongoDBObject("_id" -> new ObjectId(previewId.stringify)),
            $set("file_id" -> new ObjectId(file_id)),
            false, false, WriteConcern.Safe)
        }
	      Logger.debug("Updating previews.files " + previewId + " with " + metadata)
      }
      case _ => Logger.error("Expected a JSObject")
    }
  }
  
  /**
   * Get metadata from the mongo db as a map. 
   * 
   */
   def getMetadata(id: UUID): scala.collection.immutable.Map[String,Any] = {
    PreviewDAO.dao.collection.findOneByID(new ObjectId(id.stringify)) match {
      case None => new scala.collection.immutable.HashMap[String,Any]
      case Some(x) => {
        val returnedMetadata = x.getAs[DBObject]("metadata").get.toMap.asScala.asInstanceOf[scala.collection.mutable.Map[String,Any]].toMap
        returnedMetadata
      }
    }
  }
  
    def getExtractorId(id: UUID):Option[String] = {     
      var extractor_id = getMetadata(id)("extractor_id") match{
	  	case ex_id=> {
	  		Logger.debug("MongoDBPreviewService: metadata for preview " + id + " contains extractor id = " + ex_id)
	  		Some(ex_id.toString)
	    }
	  	case none =>{
	  		Logger.debug("MongoDBPreviewService: metadata  for preview " + id + " DOES NOT contain extractor id")
	  		None
	  	}	              
      }
      extractor_id
   }
    
}

object PreviewDAO extends ModelCompanion[Preview, ObjectId] {
  val dao = current.plugin[MongoSalatPlugin] match {
    case None => throw new RuntimeException("No MongoSalatPlugin");
    case Some(x) => new SalatDAO[Preview, ObjectId](collection = x.collection("previews.files")) {}
  }
}

