package models

import com.novus.salat.dao.{ModelCompanion, SalatDAO}
import MongoContext.context
import play.api.Play.current
import java.io.InputStream
import play.api.Logger
import com.mongodb.casbah.gridfs.GridFS
import com.mongodb.casbah.commons.MongoDBObject
import com.mongodb.casbah.WriteConcern
import com.mongodb.casbah.Imports._
import org.apache.http.impl.client.DefaultHttpClient
import org.apache.http.client.methods.HttpPost
import org.apache.http.entity.mime.MultipartEntity
import org.apache.http.entity.mime.HttpMultipartMode
import org.apache.http.entity.mime.content.StringBody
import java.nio.charset.Charset
import org.apache.http.util.EntityUtils
import java.io.BufferedReader
import java.io.InputStreamReader
import services.mongodb.MongoSalatPlugin

/**
 * Preview bytes and metadata.
 *
 * @author Luigi Marini
 */
case class Preview(
  id: ObjectId = new ObjectId,
  file_id: Option[String] = None,
  section_id: Option[String] = None,
  dataset_id: Option[String] = None,
  filename: Option[String] = None,
  contentType: String,
  annotations: List[ThreeDAnnotation] = List.empty,
  length: Long,
  extractor_id: Option[String] = None,
  iipURL: Option[String] = None,
  iipImage: Option[String] = None,
  iipKey: Option[String] = None)

object PreviewDAO extends ModelCompanion[Preview, ObjectId] {
  val dao = current.plugin[MongoSalatPlugin] match {
    case None => throw new RuntimeException("No MongoSalatPlugin");
    case Some(x) => new SalatDAO[Preview, ObjectId](collection = x.collection("previews.files")) {}
  }
}





