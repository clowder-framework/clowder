package models

import com.novus.salat.dao.{ModelCompanion, SalatDAO}
import org.bson.types.ObjectId
import MongoContext.context
import play.api.Play.current
import java.io.InputStream
import com.mongodb.casbah.gridfs.GridFS
import com.mongodb.casbah.commons.MongoDBObject
import services.mongodb.MongoSalatPlugin

/**
 * 3D binary geometry files for x3dom.
 *
 * @author Constantinos Sophocleous
 *
 */
case class ThreeDGeometry(
  id: ObjectId = new ObjectId,
  file_id: Option[String] = None,
  filename: Option[String] = None,
  contentType: String,
  level: Option[String],
  length: Long)

