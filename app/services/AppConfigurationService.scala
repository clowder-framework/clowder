package services

import play.Logger
import util.ResourceLister

/**
 * Application wide configuration options.
 *
 * Created by lmarini on 2/21/14.
 */
trait AppConfigurationService {
  def addPropertyValue(key: String, value: AnyRef)

  def removePropertyValue(key: String, value: AnyRef)

  def hasPropertyValue(key: String, value: AnyRef): Boolean

  /**
   * Gets the configuration property with the specified key. If the key is not found
   * it wil return None.
   */
  def getProperty[objectType <: AnyRef](key: String): Option[objectType]

  /**
   * Gets the configuration property with the specified key. If the key is not found
   * it wil return the default value (empty string if not specified).
   */
  def getProperty[objectType <: AnyRef](key: String, default:objectType): objectType = {
    getProperty[objectType](key) match {
      case Some(x) => x
      case None => default
    }
  }

  /**
   * Sets the configuration property with the specified key to the specified value. If the
   * key already existed it will return the old value, otherwise it returns None.
   */
  def setProperty(key: String, value: AnyRef): Option[AnyRef]

  /**
   * Remove the configuration property with the specified key and returns the value if any
   * was set, otherwise it will return None.
   */
  def removeProperty(key: String): Option[AnyRef]
}

object AppConfiguration {
  lazy val themes = ResourceLister.listFiles("public/stylesheets/themes/", ".*.css")
    .filter(s => s.contains("/themes/"))
    .map(s => s.replaceAll(".*/themes/", ""))

  val appConfig: AppConfigurationService = DI.injector.getInstance(classOf[AppConfigurationService])

  // ----------------------------------------------------------------------

  /** Set the default theme */
  def setTheme(theme: String) = appConfig.setProperty("theme", theme)

  /** Get the default theme */
  def getTheme: String = appConfig.getProperty[String]("theme", "simplex.min.css")

  // ----------------------------------------------------------------------

  /** Set the display name (subtitle) */
  def setDisplayName(displayName: String) = appConfig.setProperty("display.name", displayName)

  /** Get the display name (subtitle) */
  def getDisplayName: String = appConfig.getProperty("display.name", "Medici 2.0")

  // ----------------------------------------------------------------------

  /** Set the welcome message */
  def setWelcomeMessage(welcomeMessage: String) = appConfig.setProperty("welcome.message", welcomeMessage)

  /** Get the welcome message */
  def getWelcomeMessage: String = appConfig.getProperty("welcome.message", "Welcome to Medici 2.0, " +
    "a scalable data repository where you can share, organize and analyze data.")

  // ----------------------------------------------------------------------

  def addAdmin(admin: String) = appConfig.addPropertyValue("admins", admin)

  def removeAdmin(admin: String) = appConfig.removePropertyValue("admins", admin)

  def checkAdmin(admin: String) = appConfig.hasPropertyValue("admins", admin)

  /** Get list of all admins */
  def getAdmins: List[String] = appConfig.getProperty[List[String]]("admins", List.empty[String])

  /** Set the default admins if not yet set */
  def setDefaultAdmins() = {
    if (!appConfig.getProperty[List[String]]("admins").isDefined) {
      val x = play.Play.application().configuration().getString("initialAdmins")
      if (x != "") {
        appConfig.setProperty("admins", x.trim.split("\\s*,\\s*").toList)
      }
    }
  }
}
