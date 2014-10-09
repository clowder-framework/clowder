package services

import models.AppConfiguration

/**
 * Application wide configuration options.
 *
 * Created by lmarini on 2/21/14.
 */
trait AppConfigurationService {

  val themes = "bootstrap/bootstrap.css" ::
    "bootstrap-amelia.min.css" ::
    "bootstrap-simplex.min.css" :: Nil

  def getDefault(): Option[AppConfiguration]

  def setTheme(theme: String)

  def getTheme(): String
  
  def addAdmin(newAdminEmail: String)
  
  def removeAdmin(adminEmail: String)
  
  def adminExists(adminEmail: String): Boolean
}
