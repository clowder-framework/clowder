package services.mongodb

import services.ContextLDService
import org.bson.types.ObjectId
import com.mongodb.casbah.commons.MongoDBObject
import play.api.Logger
import models._
import com.novus.salat.dao.{ModelCompanion, SalatDAO}
import MongoContext.context
import play.api.Play.current
import com.mongodb.casbah.Imports._
import play.api.libs.json.JsValue
import javax.inject.{Inject, Singleton}
import models.Preview
import scala.Some
import com.mongodb.casbah.commons.TypeImports.ObjectId
import com.mongodb.casbah.WriteConcern
import play.api.libs.json.Json
import services.MetadataService
import play.api.libs.json.JsString
/**
 * MongoDB implementation of ContextLD service
 * @author Smruti Padhy
 * 
 */

@Singleton
class MongoDBContextLDService extends ContextLDService{

  /** Add context for metadata **/
  def addContext(contextName: JsString, contextld: JsValue): UUID = {
    val mid = ContextLDDAO.insert(new ContextLD(UUID.generate, contextName, contextld), WriteConcern.Safe)
    val id = UUID(mid.get.toString())
    id
  }

  /** Get context  **/
  def getContextById(id: UUID): Option[JsValue] = {
    Logger.debug("mongo context id: "+ id)
    val contextld = ContextLDDAO.findOne(MongoDBObject("_id" -> new ObjectId(id.stringify)))
    contextld.map(_.context)
  }

  /** Get context by name **/
  def getContextByName(contextName: String) = {
    val contextByName = ContextLDDAO.findOne(MongoDBObject(("contextName.value") -> contextName))
    contextByName.map(_.context)
  }

  /** Remove context **/
  def removeContext(id: UUID) = {
    ContextLDDAO.removeById(new ObjectId(id.stringify), WriteConcern.Safe)
  }

  /** Update context **/
  def updateContext(context: ContextLD) = {}
}

object ContextLDDAO extends ModelCompanion[ContextLD, ObjectId] {
  // TODO RK handle exception for instance if we switch to other DB
  val dao = current.plugin[MongoSalatPlugin] match {
    case None => throw new RuntimeException("No MongoSalatPlugin")
    case Some(x) => new SalatDAO[ContextLD, ObjectId](collection = x.collection("contextld")) {}
  }
}