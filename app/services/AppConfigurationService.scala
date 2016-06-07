package services

import java.text.SimpleDateFormat
import java.util.Date

import org.apache.commons.io.IOUtils
import util.ResourceLister

/**
 * Application wide configuration options. This class contains the service definition
 * and can be used to store application configuration options. See also AppConfiguration
 * for specific configuration options.
 *
 */
trait AppConfigurationService {
  /** Adds an additional value to the property with the specified key. */
  def addPropertyValue(key: String, value: AnyRef)

  /** Removes the value from the property with the specified key. */
  def removePropertyValue(key: String, value: AnyRef)

  /** Checks to see if the value is part of the property with the specified key. */
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
  def getProperty[objectType <: AnyRef](key: String, default: objectType): objectType = {
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

/**
 * Object to handle some common configuration options.
 */
object AppConfiguration {
  val appConfig: AppConfigurationService = DI.injector.getInstance(classOf[AppConfigurationService])

  // ----------------------------------------------------------------------

  /** Set the default theme */
  def setTheme(theme: String) = {
    if (themes.contains(theme))
      appConfig.setProperty("theme", theme)
  }

  /** Get the default theme */
  def getTheme: String = {
    val theme = appConfig.getProperty[String]("theme", "simplex.min.css")
    if (themes.contains(theme)) {
      theme
    } else {
      "simplex.min.css"
    }
  }

  /** Get list of available themes */
  def themes: List[String] = {
    ResourceLister.listFiles("public.stylesheets.themes", ".*.css")
      .map(s => s.replaceAll(".*.themes.", ""))
  }

  // ----------------------------------------------------------------------

  /** Set the display name (subtitle) */
  def setDisplayName(displayName: String) = appConfig.setProperty("display.name", displayName)

  /** Get the display name (subtitle) */
  def getDisplayName: String = appConfig.getProperty("display.name", "Clowder")

  // ----------------------------------------------------------------------

  /** Set the welcome message */
  def setWelcomeMessage(welcomeMessage: String) = appConfig.setProperty("welcome.message", welcomeMessage)

  /** Get the welcome message */
  def getWelcomeMessage: String = appConfig.getProperty("welcome.message", "Welcome to Clowder, " +
    "a scalable data repository where you can share, organize and analyze data.")

  // ----------------------------------------------------------------------

  /** Set the google analytics code */
  def setGoogleAnalytics(gacode: String) = appConfig.setProperty("google.analytics", gacode)

  /** Get the welcome message */
  def getGoogleAnalytics: String = appConfig.getProperty("google.analytics", "")

  // ----------------------------------------------------------------------

  /** Set the Sensors title */
  def setSensorsTitle(sensorsTitle: String) = appConfig.setProperty("sensors.title", sensorsTitle)

  /** Get the welcome message */
  def getSensorsTitle: String = appConfig.getProperty("sensors.title", "Sensors")

  // ----------------------------------------------------------------------

  /** Set the Sensor title */
  def setSensorTitle(sensorTitle: String) = appConfig.setProperty("sensor.title", sensorTitle)

  /** Get the welcome message */
  def getSensorTitle: String = appConfig.getProperty("sensor.title", "Sensor")

  // ----------------------------------------------------------------------
  /** Set the Parameters title */
  def setParametersTitle(parametersTitle: String) = appConfig.setProperty("parameters.title", parametersTitle)

  /** Get the welcome message */
  def getParametersTitle: String = appConfig.getProperty("parameters.title", "Parameters")

  // ----------------------------------------------------------------------

  /** Set the Parameter title */
  def setParameterTitle(parameterTitle: String) = appConfig.setProperty("parameter.title", parameterTitle)

  /** Get the welcome message */
  def getParameterTitle: String = appConfig.getProperty("parameter.title", "Parameter")

  // ----------------------------------------------------------------------
  // Terms of Services
  // ----------------------------------------------------------------------
  lazy val defaultToSDate = new SimpleDateFormat("yyyy-MM-dd").parse("2016-06-06")

  /** Set the Terms of Services */
  def setTermsOfServicesText(tos: String) = {
    if (tos == "") {
      setTermsOfServicesVersionDate(defaultToSDate)
    } else {
      setTermsOfServicesVersionDate(new Date())
    }
    appConfig.setProperty("tos.text", tos)
  }

  def setDefaultTermsOfServicesVersion() = {
    if (isDefaultTermsOfServices && getTermsOfServicesVersionDate != defaultToSDate) {
      setTermsOfServicesVersionDate(defaultToSDate)
    }
  }

  /** Get the Terms of Services */
  def getTermsOfServicesTextRaw: String = appConfig.getProperty("tos.text", "")

  def getTermsOfServicesText: String = {
    val tos = appConfig.getProperty("tos.text", "") match {
      case "" => {
        play.api.Play.current.resourceAsStream("/public/tos.txt") match {
          case Some(inp) => {
            IOUtils.toString(inp, "UTF-8")
          }
          case None => "missing Terms of Services"
        }
      }
      case x:String => x
      case _ => "missing Terms of Services"
    }
    tos.replace("@@NAME", getDisplayName)
  }

  def isTermOfServicesHtml: Boolean = {
    appConfig.getProperty("tos.html") == Some(true)
  }

  def setTermOfServicesHtml(html: Boolean) = appConfig.setProperty("tos.html", Boolean.box(html))

  def isDefaultTermsOfServices: Boolean = appConfig.getProperty("tos.text", "") == ""

  def acceptedTermsOfServices(date: Option[Date]) = date.exists(_.after(getTermsOfServicesVersionDate))

  /** Set the version of the Terms of Services and returns the version */
  def setTermsOfServicesVersionDate(date: Date) = {
    DI.injector.getInstance(classOf[UserService]).newTermsOfServices()
    appConfig.setProperty("tos.version", date)
  }

  /** get the version of the Terms of Services */
  def getTermsOfServicesVersionDate: Date = appConfig.getProperty("tos.version", new Date())

  def getTermsOfServicesVersionString: String = {
    if (isDefaultTermsOfServices) {
      new SimpleDateFormat("yyyy-MM-dd").format(appConfig.getProperty("tos.version", new Date()))
    } else {
      appConfig.getProperty("tos.version", new Date()).toString
    }
  }
}
