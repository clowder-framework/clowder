package services.mongodb

import services.{TileService, FileService, PreviewService}
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
/**
 * Created by lmarini on 2/17/14.
 */
@Singleton
class MongoDBPreviewService @Inject()(files: FileService, tiles: TileService) extends PreviewService {

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

  /**
   * Save blob.
   */
  def save(inputStream: InputStream, filename: String, contentType: Option[String]): String = {
    val files = current.plugin[MongoSalatPlugin] match {
      case None => throw new RuntimeException("No MongoSalatPlugin");
      case Some(x) => x.gridFS("previews")
    }
    val mongoFile = files.createFile(inputStream)
    Logger.debug("Uploading file " + filename)
    mongoFile.filename = filename
    var ct = contentType.getOrElse(play.api.http.ContentTypes.BINARY)
    if (ct == play.api.http.ContentTypes.BINARY) {
      ct = play.api.libs.MimeTypes.forFileName(filename).getOrElse(play.api.http.ContentTypes.BINARY)
    }
    mongoFile.contentType = ct
    mongoFile.save
    mongoFile.getAs[ObjectId]("_id").get.toString
  }

  /**
   * Get blob.
   */
  def getBlob(id: UUID): Option[(InputStream, String, String, Long)] = {
    val files = GridFS(SocialUserDAO.dao.collection.db, "previews")
    files.findOne(MongoDBObject("_id" -> new ObjectId(id.stringify))) match {
      case Some(file) => Some(file.inputStream,
        file.getAs[String]("filename").getOrElse("unknown-name"),
        file.getAs[String]("contentType").getOrElse("unknown"),
        file.getAs[Long]("length").getOrElse(0))
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
      TileDAO.remove(MongoDBObject("_id" -> new ObjectId(tile.id.stringify)))
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

    PreviewDAO.remove(MongoDBObject("_id" -> new ObjectId(p.id.stringify)))
  }

  def attachToFile(previewId: UUID, fileId: UUID, extractorId: Option[UUID], json: JsValue) {
    json match {
      case JsObject(fields) => {
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

  def updateMetadata(previewId: UUID, json: JsValue) {
    json match {
      case JsObject(fields) => {
        val metadata = fields.toMap.flatMap(tuple => MongoDBObject(tuple._1 -> tuple._2.as[String]))
        PreviewDAO.dao.collection.update(MongoDBObject("_id" -> new ObjectId(previewId.stringify)),
          $set("metadata" -> metadata, "section_id" -> new ObjectId(metadata("section_id").asInstanceOf[String]), 
        		  //the next line adds file_id as string to Preview. Used by Versus.
	              "file_id"->metadata("file_id").asInstanceOf[String]),
	              
	              false, false, WriteConcern.Safe)      
	              
	              Logger.debug("Updating previews.files " + previewId + " with " + metadata)
      }
      case _ => Logger.error("Expected a JSObject")
    }
  }
}

object PreviewDAO extends ModelCompanion[Preview, ObjectId] {
  val dao = current.plugin[MongoSalatPlugin] match {
    case None => throw new RuntimeException("No MongoSalatPlugin");
    case Some(x) => new SalatDAO[Preview, ObjectId](collection = x.collection("previews.files")) {}
  }
}
