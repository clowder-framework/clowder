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
class MongoDBTemplateService @Inject() (userService: UserService)  extends TemplateService {

  def count(): Long = {
    Template.count( MongoDBObject())
  }

  def insert(template : Template): Option[String] = {
    Template.insert(template).map(_.toString)
  }

  def get(id : UUID) : Option[Template] = {
    Template.findOneById(new ObjectId(id.stringify))
  }

  def list() : List[Template] = {
    Template.findAll().toList
  }

  def delete(id: UUID) =  Try {
    Template.findOneById(new ObjectId(id.stringify)) match {
      case Some(t) => {
        Template.remove(MongoDBObject("_id" -> new ObjectId(t.id.stringify)))
        Success
      }
      case None => Success
    }

  }

  def update(id: UUID, )

}


object Template extends ModelCompanion[Template, ObjectId] {
  val dao = current.plugin[MongoSalatPlugin] match {
    case None => throw new RuntimeException("No MongoSalatPlugin");
    case Some(x) => new SalatDAO[Template, ObjectId](collection = x.collection("templates")) {}
  }
}

