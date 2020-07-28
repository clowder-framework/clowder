package models

import org.bson.types.ObjectId
import salat.dao.{ModelCompanion, SalatDAO}
import services.mongodb.MongoContext.context
import play.api.Play.current
import play.api.Logger
import com.mongodb.casbah.commons.MongoDBObject
import com.mongodb.casbah.WriteConcern
import com.mongodb.casbah.Imports._
import services.mongodb.MongoSalatPlugin


/**
 * Selected items.
 */
case class Selected (
    id: ObjectId = new ObjectId,
    user: String,
    datasets: List[String] = List.empty
    )
    
