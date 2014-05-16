package models

import com.novus.salat.dao.ModelCompanion
import com.mongodb.casbah.MongoConnection
import services.mongodb.MongoContext.context
import com.novus.salat.dao.SalatDAO
import org.bson.types.ObjectId
import play.api.Play.current
import services.mongodb.MongoSalatPlugin

case class LicenseData (
        id: UUID = UUID.generate,
        m_licenseType: String = "license1",
        m_licenseUrl: String = "",
        m_licenseText: String = "All Rights Reserved",
        m_rightsHolder: String = "", 
        m_ccAllowCommercial: Boolean = false,
        m_ccAllowDerivative: Boolean = false,
        m_ccRequireShareAlike: Boolean = false,
        m_allowDl: Boolean = false
)

object LicenseData extends ModelCompanion[LicenseData, ObjectId] {
//  val collection = MongoConnection()("test-alt")("licensedata")
//  val dao = new SalatDAO[LicenseData, ObjectId](collection = collection) {}
  val dao = current.plugin[MongoSalatPlugin] match {
    case None => throw new RuntimeException("No MongoSalatPlugin");
    case Some(x) => new SalatDAO[LicenseData, ObjectId](collection = x.collection("licensedata")) {}
  }
}
