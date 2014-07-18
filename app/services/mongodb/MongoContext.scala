package services.mongodb

import com.novus.salat.{TypeHintFrequency, StringTypeHintStrategy, Context}
import play.api.{Logger, Play}
import play.api.Play.current
import models.UUIDTransformer

/**
 * Salat context configuration.
 *
 * @author Luigi Marini
 */
object MongoContext {

  implicit val context = new Context {
      Logger.debug("Loading custom mongodb context")
      val name = "global"
      override val typeHintStrategy = StringTypeHintStrategy(when = TypeHintFrequency.Always,
        typeHint = "_typeHint")
      registerCustomTransformer(UUIDTransformer)
      registerGlobalKeyOverride(remapThis = "id", toThisInstead = "_id")
      registerClassLoader(Play.classloader)
    }
}