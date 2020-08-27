package services.mongodb

import com.mongodb.casbah.Imports._
import com.mongodb.casbah.commons.MongoDBObject
import models.Institution
import salat.dao.{ModelCompanion, SalatDAO}
import services.mongodb.MongoContext.context
import services.{DI, InstitutionService}

/**
 * Use mongodb to store institutions.
 */
class MongoDBInstitutionService extends InstitutionService {

  override def getAllInstitutions(): List[String] = {
    var allinstitutions = Institution.dao.find(MongoDBObject()).sort(orderBy = MongoDBObject("name" -> 1)).map(_.name).toList
    allinstitutions match {
      case x :: xs => allinstitutions
      case nil => List("")
    }
  }

  override def addNewInstitution(institution: String) = {
    Institution.insert(new Institution(institution));
  }

}


object Institution extends ModelCompanion[Institution, ObjectId] {
  val COLLECTION = "institutions"
  val mongos: MongoStartup = DI.injector.getInstance(classOf[MongoStartup])
  val dao = new SalatDAO[Institution, ObjectId](collection = mongos.collection(COLLECTION)) {}
}
