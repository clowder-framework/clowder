/**
 *
 */
package models

import com.novus.salat.dao._
import com.novus.salat.annotations._
import com.mongodb.casbah.Imports._
import com.novus.salat.{TypeHintFrequency, StringTypeHintStrategy, Context}
import play.api.Play
import play.api.Play.current

/**
 * Salat context configuration.
 * 
 * @author Luigi Marini
 *
 */
object MongoContext {
  
  implicit val context = {
    val context = new Context {
      val name = "global"
//      override val typeHintStrategy = StringTypeHintStrategy(when = TypeHintFrequency.WhenNecessary, typeHint = "_t")
      override val typeHintStrategy = StringTypeHintStrategy(when = TypeHintFrequency.Always,
            typeHint = "_typeHint")

    }
    context.registerGlobalKeyOverride(remapThis = "id", toThisInstead = "_id")
    context.registerClassLoader(Play.classloader)
    context
  }
}