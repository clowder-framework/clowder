package util

import models.{UUID, LicenseData}
import play.api.Play._

/**
 * Helper class to create the correct license data objects
 *
 */
object License {
  def fromAppConfig(): LicenseData = {
    new LicenseData(
      id = UUID.generate(),
      m_licenseType = configuration.getString("clowder.license.type").getOrElse("license1"),
      m_licenseUrl = configuration.getString("clowder.license.url").getOrElse(""),
      m_licenseText = configuration.getString("clowder.license.text").getOrElse("All Rights Reserved"),
      m_rightsHolder = configuration.getString("clowder.license.holder").getOrElse(""),
      m_ccAllowCommercial = configuration.getBoolean("clowder.license.commercial").getOrElse(false),
      m_ccAllowDerivative = configuration.getBoolean("clowder.license.derivative").getOrElse(false),
      m_ccRequireShareAlike = configuration.getBoolean("clowder.license.share").getOrElse(false),
      m_allowDownload = configuration.getBoolean("clowder.license.download").getOrElse(false))
  }
}
