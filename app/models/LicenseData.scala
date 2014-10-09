package models

import com.novus.salat.dao.ModelCompanion
import com.mongodb.casbah.MongoConnection
import services.mongodb.MongoContext.context
import com.novus.salat.dao.SalatDAO
import org.bson.types.ObjectId
import play.api.Play.current
import services.mongodb.MongoSalatPlugin

/**
 * case class to handle specific license information. Currently attached to individual Datasets and Files.  
 */
case class LicenseData (
        id: UUID = UUID.generate,
        m_licenseType: String = "license1",
        m_licenseUrl: String = "",
        m_licenseText: String = "All Rights Reserved",
        m_rightsHolder: String = "", 
        m_ccAllowCommercial: Boolean = false,
        m_ccAllowDerivative: Boolean = false,
        m_ccRequireShareAlike: Boolean = false,
        m_allowDownload: Boolean = false
)
