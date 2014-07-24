/**
 *
 */
package controllers

import java.util.regex.Pattern
import scala.Array.canBuildFrom
import scala.collection.JavaConversions
import scala.io.Source
import org.reflections.Reflections
import org.reflections.scanners.ResourcesScanner
import org.reflections.util.ConfigurationBuilder
import models.Previewer
import play.api.Logger
import play.api.Play
import play.api.Play.current
import play.api.libs.json.Json
import play.api.mvc.Action
import play.api.mvc.Controller
import securesocial.core.SecureSocial
import api.WithPermission
import api.Permission

/**
 * Previewers.
 *
 * @author Luigi Marini
 * @author Rob Kooper
 *
 */
object Previewers extends Controller with SecuredController {
  def list = SecuredAction(parse.anyContent, authorization=WithPermission(Permission.ShowFile)) { implicit request =>
    Ok(views.html.previewers(findPreviewers))
  }

  def findPreviewers(): Array[Previewer] = {
    val configuration = ConfigurationBuilder.build("public", new ResourcesScanner())
    val reflections = new Reflections(configuration)
    val previewers = JavaConversions.asScalaSet(reflections.getResources(Pattern.compile("package.json")))

    var result = Array[Previewer]()
    for (previewer <- previewers) {
      Play.resourceAsStream(previewer) match {
        case Some(stream) => {
          val json = Json.parse(Source.fromInputStream(stream).mkString)
          result +:= Previewer((json \ "name").as[String],
            previewer.replace("public/", "").replace("/package.json", ""),
            (json \ "main").as[String],
            (json \ "contentType").as[List[String]]
          )
        }
        case None => {
          Logger.warn("Thought I saw previewer " + previewer)
        }
      }
    }
    return result
  }
}
