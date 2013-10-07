/**
 *
 */
package models

import org.bson.types.ObjectId
import com.novus.salat.dao.{ModelCompanion, SalatDAO}
import MongoContext.context
import play.api.Play.current
import play.api.Logger
import com.mongodb.casbah.commons.MongoDBObject
import com.mongodb.casbah.WriteConcern
import com.mongodb.casbah.Imports._
import services.MongoSalatPlugin


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
    
object SelectedDAO extends ModelCompanion[Selected, ObjectId] {
  val dao = current.plugin[MongoSalatPlugin] match {
    case None    => throw new RuntimeException("No MongoSalatPlugin");
    case Some(x) =>  new SalatDAO[Selected, ObjectId](collection = x.collection("selected")) {}
  }
  
  def add(dataset: String, user: String) {
    val query = MongoDBObject("user" -> user)
    val update = $addToSet("datasets" -> dataset)
//    val result = dao.collection.update(query, update, upsert=true)
    val updated = dao.collection.findAndModify(
      query = query,
      update = update,
      upsert = true,
      fields = null,
      sort = null,
      remove = false,
      returnNew = true
    )
    Logger.debug("Selected updated " +  updated)
  }
  
  def remove(dataset: String, user: String) {
    val query = MongoDBObject("user" -> user)
    val update = $pull("datasets" -> dataset)
    val result = dao.collection.update(query, update, upsert=true)
  }
  
  def get(user: String): List[Dataset] = {
    dao.findOne(MongoDBObject("user"->user)) match {
      case Some(selected) => selected.datasets.flatMap(x => Dataset.findOneByID(new ObjectId(x)))
      case None => List.empty
    }
  }
}