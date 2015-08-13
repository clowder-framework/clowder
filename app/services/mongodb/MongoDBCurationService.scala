package services.mongodb

import javax.inject.{Inject, Singleton}

import com.novus.salat.dao.{SalatDAO, ModelCompanion}
import models.{CurationObj, ProjectSpace, SpaceInvite}
import org.bson.types.ObjectId
import play.api.Play._
import MongoContext.context
import models.{User, UUID, Collection, Dataset}
import services.CurationService
import util.Direction._
import java.util.Date
import play.api.Logger
import com.mongodb.casbah.commons.MongoDBObject
import com.mongodb.casbah.Imports._

/**
 * Created by yanzhao3 on 8/13/15.
 */
@Singleton
class MongoDBCurationService  @Inject()  extends CurationService{
  def insert(curation: CurationObj): Option[String] = {
    Logger.debug("insert a new CO")
    CurationDAO.insert(curation).map(_.toString)
  }



  def get(id: UUID) = {None}



  def update(model: CurationObj) = {}

  def delete(id: UUID) = {}


def count(filter: Option[String] = None): Long = {1}

  def list(order: Option[String] = None, direction: Direction=DESC,
           start: Option[String] = None, limit: Integer = 20,
           filter: Option[String] = None): List[CurationObj]  = List.empty



def getNext(order: Option[String], direction: Direction, start: Date, limit: Integer = 20,
filter: Option[String]): Option[String] = {None}

def getPrev(order: Option[String], direction: Direction, start: Date, limit: Integer = 20,
filter: Option[String]): Option[String] = {None}

}

/**
 * Salat CurationObj model companion.
 */
object CurationDAO extends ModelCompanion[CurationObj, ObjectId] {
  val dao = current.plugin[MongoSalatPlugin] match {
    case None => throw new RuntimeException("No MongoSalatPlugin");
    case Some(x) => new SalatDAO[CurationObj, ObjectId](collection = x.collection("curationObjs")) {}
  }
}