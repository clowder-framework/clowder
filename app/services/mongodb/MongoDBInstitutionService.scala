package services.mongodb

import com.mongodb.casbah.Imports._
import com.mongodb.casbah.commons.MongoDBObject
import models.{Institution => ClowderInstitution}
import salat.dao.{ModelCompanion, SalatDAO}
import services.mongodb.MongoContext.context
import services.{DI, InstitutionService}

/**
 * Use mongodb to store institutions.
 */
class MongoDBInstitutionService extends InstitutionService {

  // TODO: Revisit this for removal - is it called in views?
  override def getAllInstitutions(): List[String] = {
    var allinstitutions = Institution.dao.find(MongoDBObject()).sort(orderBy = MongoDBObject("name" -> 1)).map(_.name).toList
    allinstitutions match {
      case x :: xs => allinstitutions
      case nil => List("")
    }
  }

  override def addNewInstitution(institution: String) = {
    Institution.insert(new ClowderInstitution(institution));
  }

}


object Institution extends ModelCompanion[ClowderInstitution, ObjectId] {
  val COLLECTION = "institutions"
  val mongos: MongoStartup = DI.injector.getInstance(classOf[MongoStartup])
  val dao = new SalatDAO[ClowderInstitution, ObjectId](collection = mongos.collection(COLLECTION)) {}
}
