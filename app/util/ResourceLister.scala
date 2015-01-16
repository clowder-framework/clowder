package util

import java.util.regex.Pattern

import org.reflections.Reflections
import org.reflections.scanners.ResourcesScanner
import org.reflections.util.{FilterBuilder, ClasspathHelper, ConfigurationBuilder}
import play.api.Logger

import scala.collection.JavaConversions

/**
 * @author Rob Kooper
 */
object ResourceLister {
  /**
   * Search the classpath for files with the given path and filename. The path
   * should be specified with a . instead of the path separator, allowing it to
   * find files inside a jar file as well.
   */
  def listFiles(path: String, regex: String): List[String] = {
    try {
      val startTime = System.currentTimeMillis()

      // Create the default resource checker
      val configBuilder = new ConfigurationBuilder()
        .filterInputsBy(new FilterBuilder().include(path + ".*"))
        .setUrls(ClasspathHelper.forClassLoader())
        .setScanners(new ResourcesScanner())

      // Find all resources
      val reflections = new Reflections(configBuilder)
      val files = JavaConversions.asScalaSet(reflections.getResources(Pattern.compile(regex))).toList.sorted
      //Logger.debug("Found " + files.length + " files in " + (System.currentTimeMillis() - startTime) + " ms.")
      files
    } catch {
      case t:Throwable => {
        Logger.error("Could not list files.", t)
        List.empty[String]
      }
    }
  }
}
