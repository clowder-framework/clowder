package util

import java.io.{File, FileFilter}
import java.util.regex.Pattern

import org.reflections.Reflections
import org.reflections.scanners.ResourcesScanner
import org.reflections.util.ConfigurationBuilder
import play.api.{Logger, Play}
import play.api.Play.current

import scala.collection.JavaConversions

/**
 * @author Rob Kooper
 */
object ResourceLister {
  def listFiles(path: String, regex: String): List[String] = {
    try {
      val configuration = ConfigurationBuilder.build("", new ResourcesScanner())
      val reflections = new Reflections(configuration)
      val files = JavaConversions.asScalaSet(reflections.getResources(Pattern.compile(regex)))
        .filter(s => s.startsWith(path))
        .toList
        .sorted
      Logger.debug("Found files : " + files)
      files
    } catch {
      case t:Throwable => {
        Logger.error("Could not list files.", t)
        List.empty[String]
      }
    }
  }
}
