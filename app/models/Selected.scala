package models

import org.bson.types.ObjectId
import com.novus.salat.dao.{ModelCompanion, SalatDAO}
import services.mongodb.MongoContext.context
import play.api.Play.current
import play.api.Logger
import com.mongodb.casbah.commons.MongoDBObject
import com.mongodb.casbah.WriteConcern
import com.mongodb.casbah.Imports._
import services.mongodb.MongoSalatPlugin


/**
 * Selected items.
 * 
 * @author Luigi Marini
 *
 */
case class Selected (
    id: ObjectId = new ObjectId,
    user: String,
    datasets: List[String] = List.empty
    )
    
