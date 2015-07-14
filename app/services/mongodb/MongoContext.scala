package services.mongodb

import com.novus.salat.transformers.CustomTransformer
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
      registerCustomTransformer(JodaDateTimeTransformer)
      registerGlobalKeyOverride(remapThis = "id", toThisInstead = "_id")
      registerClassLoader(Play.classloader)
    }

  // joda.time to Date and vice versa
  object JodaDateTimeTransformer extends CustomTransformer[org.joda.time.DateTime, java.util.Date] {
    def deserialize(date: java.util.Date) = {
      new org.joda.time.DateTime(date.getTime)
    }

    def serialize(date: org.joda.time.DateTime) = {
      date.toDate
    }
  }
}
