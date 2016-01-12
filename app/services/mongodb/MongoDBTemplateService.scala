/**
 *
 */
package services.mongodb

import api.Permission
import api.Permission.Permission
import com.novus.salat.dao.{ModelCompanion, SalatDAO}
import com.mongodb.casbah.Imports._
import com.mongodb.casbah.WriteConcern
import models._
import com.mongodb.casbah.commons.MongoDBObject
import java.text.SimpleDateFormat
import org.bson.types.ObjectId
import play.api.Logger
import util.Formatters
import scala.collection.mutable.ListBuffer
import scala.util.Try
import services._
import javax.inject.{Singleton, Inject}
import scala.util.Failure
import scala.Some
import scala.util.Success
import MongoContext.context
import play.api.Play._


/**
 * Use Mongodb to store collections.
 *
 * @author Constantinos Sophocleous
 *
 */
@Singleton
class MongoDBTemplateService @Inject()  extends TemplateService {
  /**
   * Count all collections
   */
  def insert(template : Template): Option[String] = {
    Template.insert(template).map(_.toString)
  }

  def get(id : UUID) : Option[Template] = {
    Template.findOneById(new ObjectId(id.stringify))
  }

  def list() : List[Template] = {
    Template.findAll().toList
  }

}

object Template extends ModelCompanion[Template, ObjectId] {
  val dao = current.plugin[MongoSalatPlugin] match {
    case None => throw new RuntimeException("No MongoSalatPlugin");
    case Some(x) => new SalatDAO[Template, ObjectId](template = x.template("templates")) {}
  }
}
