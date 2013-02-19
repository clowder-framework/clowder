/**
 *
 */
package controllers
import play.api.mvc.Controller
import securesocial.core.SecureSocial
import models.Previewer
import play.api.Play
import play.api.Logger
import play.api.mvc.Action
import models.Previewer

/**
 * Previewers.
 * 
 * @author Luigi Marini
 *
 */
object Previewers extends Controller with SecureSocial {
	def list = Action {
	  import play.api.Play.current
      val previewers = Play.getFile("public/javascripts/previewers").listFiles().map(
          f => Previewer(f.getAbsolutePath(), routes.Assets.at(f.getName()).url)
	  )
	  previewers.map(p => {
            Logger.info("Previewer found " + p.id + " / " + p.url)
          })
	Ok
	}
}