package services.mongodb

import java.net.URL

import com.mongodb.casbah.commons.conversions.MongoConversionHelper
import com.novus.salat.transformers.CustomTransformer
import com.novus.salat.{TypeHintFrequency, StringTypeHintStrategy, Context}
import play.api.{Logger, Play}
import play.api.Play.current
import models.UUIDTransformer
import org.bson.{BSON, Transformer}

/**
 * MongoDB context configuration.
 *
 * @author Luigi Marini
 *
 */
object MongoContext {

  implicit val context = new Context {
      Logger.debug("Loading custom mongodb context")
      val name = "global"
      override val typeHintStrategy = StringTypeHintStrategy(when = TypeHintFrequency.Always,
        typeHint = "_typeHint")
      registerCustomTransformer(UUIDTransformer)
      registerCustomTransformer(URLTransformer)
      registerGlobalKeyOverride(remapThis = "id", toThisInstead = "_id")
      registerClassLoader(Play.classloader)
    }

  /**
   * Casbah URL serializer.
   */
  trait URLSerializer extends MongoConversionHelper {
    private val transformer = new Transformer {
      Logger.trace("Encoding a URL.")

      def transform(o: AnyRef): AnyRef = o match {
        case url: URL => url.toString
        case _ => o
      }
    }

    override def register() {
      Logger.debug("Setting up URL Serializer")

      BSON.addEncodingHook(classOf[URL], transformer)

      super.register()
    }
  }

  /**
   * Casbah URL deserializer.
   */
  trait URLDeserializer extends MongoConversionHelper {

    private val transformer = new Transformer {
      Logger.trace("Decoding URL.")

      def transform(o: AnyRef): AnyRef = o match {
        case url: String => {
          new URL(url)
        }
        case _ => {
          o
        }
      }
    }

    override def register() {
      Logger.trace("Hooking up URL deserializer")

      BSON.addDecodingHook(classOf[URL], transformer)

      super.register()
    }
  }

  object RegisterURLDeserializer extends URLDeserializer {
    Logger.trace("Registering URL deserializer")
    def apply() = super.register()
  }

  object RegisterURLSerializer extends URLSerializer {
    Logger.trace("Registering URL serializer")
    def apply() = super.register()
  }

  RegisterURLDeserializer()
  RegisterURLSerializer()


  /**
   * Salat URL transformers
   */
  object URLTransformer extends CustomTransformer[URL, String] {
    def deserialize(url: String) = {
      Logger.trace("Deserializing String to URL :" + url)
      new URL(url)
    }

    def serialize(url: URL) = {
      Logger.trace("Serializing URL to String :" + url)
      url.toString
    }
  }
}