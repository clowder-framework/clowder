package services.mongodb

import com.mongodb.casbah.WriteConcern
import com.mongodb.casbah.commons.MongoDBObject
import com.mongodb.casbah.commons.TypeImports.ObjectId
import javax.inject.Singleton
import models._
import play.api.Logger
import play.api.libs.json.{JsString, JsValue}
import salat.dao.{ModelCompanion, SalatDAO}
import services.mongodb.MongoContext.context
import services.{ContextLDService, DI}
/**
 * MongoDB implementation of ContextLD service
 * 
 */

@Singleton
class MongoDBContextLDService extends ContextLDService{

  /** Add context for metadata **/
  def addContext(contextName: JsString, contextld: JsValue): UUID = {
    val mid = ContextLDDAO.insert(new ContextLD(UUID.generate, contextName, contextld), WriteConcern.Safe)
    UUID(mid.get.toString())
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
  val COLLECTION = "contextld"
  val mongos: MongoStartup = DI.injector.getInstance(classOf[MongoStartup])
  val dao = new SalatDAO[ContextLD, ObjectId](collection = mongos.collection(COLLECTION)) {}
}