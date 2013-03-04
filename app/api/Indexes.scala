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
//import api.Authenticated
import play.api.libs.json.Json
import play.api.libs.json.JsArray
import play.api.libs.json.JsNumber
import models.Feature
import models.MultimediaFeatures

/**
 * Index data.
 * 
 * @author Luigi Marini
 *
 */
object Indexes extends Controller {

  def index() = Authenticated {
    Action(parse.json) { request =>
      Logger.debug("Indexing " + request.body)
      (request.body \ "file_id").asOpt[String].map { file_id =>
        MultimediaFeaturesDAO.findOne(MongoDBObject("file_id"->new ObjectId(file_id))) match {
          case Some(mFeatures) => {
            val builder = MongoDBObject.newBuilder
            builder += "file_id" -> new ObjectId(file_id)
            val features = (request.body \ "features").as[List[JsObject]]
            val listBuilder = MongoDBList.newBuilder
            features.map {f =>
            	val featureBuilder = MongoDBObject.newBuilder
                featureBuilder += "representation" -> (f \ "representation").as[String]
                featureBuilder += "descriptor" -> (f \ "descriptor").as[List[Double]]
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
            val doc = MultimediaFeatures(file_id = new ObjectId(file_id), features = features)
            MultimediaFeaturesDAO.save(doc)
            Logger.debug("Features created: " + doc)
            Ok(toJson(Map("id"->doc.id.toString)))
          }
        }
      }.getOrElse {
        BadRequest(toJson("Missing parameter [file_id]"))
      }
    }
  }
}