package services.mongodb

import models.Institution
import services.InstitutionService
import com.novus.salat.dao.{ModelCompanion, SalatDAO}
import MongoContext.context
import play.api.Play.current
import play.Logger
import com.mongodb.casbah.commons.MongoDBObject
import com.mongodb.casbah.Imports._

/**
 * @author Will Hennessy
 */
class MongoDBInstitutionService extends InstitutionService {

  override def getAllInstitutions(): List[String] = {
    var allinstitutions = Institution.dao.find(MongoDBObject()).map(_.name).toList
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
  val dao = current.plugin[MongoSalatPlugin] match {
    case None => throw new RuntimeException("No MongoSalatPlugin");
    case Some(x) => new SalatDAO[Institution, ObjectId](collection = x.collection("institutions")) {}
  }
}
