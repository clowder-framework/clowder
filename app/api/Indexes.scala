/**
 *
 */
package api

import play.api.mvc.Controller
import play.api.mvc.Action
import play.api.libs.json.JsObject
import com.mongodb.casbah.Imports._
import play.api.libs.json.Json._
import models.MultimediaFeaturesDAO
import play.api.Logger
import play.api.libs.json.Json
import play.api.libs.json.JsArray
import play.api.libs.json.JsNumber
import models.Feature
import models.MultimediaFeatures
import models.PreviewDAO
import play.api.Play.current
import services.RabbitmqPlugin
import services.ExtractorMessage

/**
 * Index data.
 * 
 * @author Luigi Marini
 *
 */
object Indexes extends Controller {

  /**
   * Submit section, preview, file for indexing.
   */
  def index() = Authenticated {
    Action(parse.json) { request =>
//      Logger.debug("Add feature to multimedia index " + request.body)
      (request.body \ "section_id").asOpt[String].map { section_id =>
      	  (request.body \ "preview_id").asOpt[String].map { preview_id =>
      	    PreviewDAO.findOneById(new ObjectId(preview_id)) match {
      	      case Some(p) =>
	      	    // TODO RK need to replace unknown with the server name
	            val key = "unknown." + "index."+ p.contentType.replace(".", "_").replace("/", ".")
	            // TODO RK : need figure out if we can use https
	            val host = "http://" + request.host + request.path.replaceAll("api/indexes$", "")
	            val id = p.id.toString
	            current.plugin[RabbitmqPlugin].foreach{
	              _.extract(ExtractorMessage(id, host, key, Map("section_id"->section_id)))}
	            Ok(toJson("success"))
      	      case None => BadRequest(toJson("Missing parameter [preview_id]"))
            }
      	   
      	  }.getOrElse {
      		BadRequest(toJson("Missing parameter [preview_id]"))
      	  }
      }.getOrElse {
        BadRequest(toJson("Missing parameter [section_id]"))
      }
    }
  }
  
  /**
   * Add feature to index.
   */
  def features() = Authenticated {
    Action(parse.json) { request =>
      (request.body \ "section_id").asOpt[String].map { section_id =>
        MultimediaFeaturesDAO.findOne(MongoDBObject("section_id"->new ObjectId(section_id))) match {
          case Some(mFeatures) => {
            val builder = MongoDBObject.newBuilder
            builder += "section_id" -> new ObjectId(section_id)
            val features = (request.body \ "features").as[List[JsObject]]
            val listBuilder = MongoDBList.newBuilder
            features.map {f =>
            	val featureBuilder = MongoDBObject.newBuilder
                featureBuilder += "representation" -> (f \ "representation").as[String]
                featureBuilder += "descriptor" -> (f \ "descriptor").as[List[Double]]
                listBuilder += featureBuilder.result
            }
            mFeatures.features.map {f =>
            	val featureBuilder = MongoDBObject.newBuilder
                featureBuilder += "representation" -> f.representation
                featureBuilder += "descriptor" -> f.descriptor
                listBuilder += featureBuilder.result
            }
            builder += "features" -> listBuilder.result
            Logger.debug("Features doc " + mFeatures.id + " updated")
            MultimediaFeaturesDAO.update(MongoDBObject("_id" -> mFeatures.id), builder.result, false, false, WriteConcern.Safe)
            Ok(toJson(Map("id"->mFeatures.id.toString)))
          }
          case None => {
            val jsFeatures = (request.body \ "features").as[List[JsObject]]
            val features = jsFeatures.map {f =>
            	Feature((f \ "representation").as[String], (f \ "descriptor").as[List[Double]])
            }
            val doc = MultimediaFeatures(section_id = Some(new ObjectId(section_id)), features = features)
            MultimediaFeaturesDAO.save(doc)
//            Logger.debug("Features created: " + doc)
            Ok(toJson(Map("id"->doc.id.toString)))
          }
        }
      }.getOrElse {
        BadRequest(toJson("Missing parameter [section_id]"))
      }
    }
  }
}
