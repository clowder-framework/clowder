package models

import java.util.Date
import org.bson.types.ObjectId
import com.novus.salat.dao.{ModelCompanion, SalatDAO}
import services.mongodb.{MongoContext, MongoSalatPlugin}
import MongoContext.context
import play.api.Play.current
import services.mongodb.MongoSalatPlugin

/**
 * Temporary files used when uploading query images for image based searches.
 *
 * TODO change name of collection to be more generic so it can be reused in other places?
 */
case class TempFile(
  id: ObjectId = new ObjectId,
  path: Option[String] = None,
  filename: String,
  uploadDate: Date,
  contentType: String,
  length: Long = 0)


