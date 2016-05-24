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
      m_licenseType = configuration.getString("medici2.license.type").getOrElse("license1"),
      m_licenseUrl = configuration.getString("medici2.license.url").getOrElse(""),
      m_licenseText = configuration.getString("medici2.license.text").getOrElse("All Rights Reserved"),
      m_rightsHolder = configuration.getString("medici2.license.holder").getOrElse(""),
      m_ccAllowCommercial = configuration.getBoolean("medici2.license.commercial").getOrElse(false),
      m_ccAllowDerivative = configuration.getBoolean("medici2.license.derivative").getOrElse(false),
      m_ccRequireShareAlike = configuration.getBoolean("medici2.license.share").getOrElse(false),
      m_allowDownload = configuration.getBoolean("medici2.license.download").getOrElse(false))
  }
}
