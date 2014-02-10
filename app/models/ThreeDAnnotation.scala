package models

import services.MongoSalatPlugin
import com.novus.salat.dao.{ModelCompanion, SalatDAO}
import org.bson.types.ObjectId
import MongoContext.context
import play.api.Play.current
import java.io.InputStream
import play.api.Logger
import com.mongodb.casbah.gridfs.GridFS
import com.mongodb.casbah.commons.MongoDBObject
import com.mongodb.casbah.WriteConcern
import com.mongodb.casbah.Imports._

/**
 * 3D textures for x3dom generated from obj models.
 * 
 * @author Constantinos Sophocleous
 *
 */
case class ThreeDAnnotation (    
	x_coord: String,
	y_coord: String,
	z_coord: String,
	description: String = "",
	id: ObjectId = new ObjectId
)

object ThreeDAnnotation extends ModelCompanion[ThreeDAnnotation, ObjectId] {

  val dao = current.plugin[MongoSalatPlugin] match {
		case None    => throw new RuntimeException("No MongoSalatPlugin");
		case Some(x) =>  new SalatDAO[ThreeDAnnotation, ObjectId](collection = x.collection("previews.files.annotations")) {}
	}
      
}