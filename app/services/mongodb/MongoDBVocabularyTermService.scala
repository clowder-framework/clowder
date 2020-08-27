package services.mongodb

import com.mongodb.casbah.Imports._
import securesocial.core.Identity
import services.mongodb.MongoContext.context
import com.mongodb.casbah.commons.MongoDBObject
import salat.dao.{ModelCompanion, SalatDAO, SalatMongoCursor}
import org.bson.types.ObjectId
import models._
import javax.inject.{Inject, Singleton}
import com.mongodb.casbah.WriteConcern
import services.{DI, UserService, VocabularyService, VocabularyTermService}
import play.api.Play._

import scala.util.{Success, Try}


@Singleton
class MongoDBVocabularyTermService @Inject() (userService: UserService) extends VocabularyTermService {

  def count(): Long = {
    VocabularyTerm.count(MongoDBObject())
  }

  def insert(vocabularyTerm: VocabularyTerm): Option[String] = {
    VocabularyTerm.insert(vocabularyTerm).map(_.toString)
  }

  def listAll(): List[VocabularyTerm] = {
    VocabularyTerm.findAll().toList
  }

  def get(id: UUID): Option[VocabularyTerm] = {
    VocabularyTerm.findOneById(new ObjectId(id.stringify))
  }

  def delete(id: UUID) = Try {
    VocabularyTerm.findOneById(new ObjectId(id.stringify)) match {
      case Some(vocabterm) => {
        VocabularyTerm.remove(MongoDBObject("_id" -> new ObjectId(vocabterm.id.stringify)))
        Success
      }
      case None => Success
    }
  }
}

object VocabularyTerm extends ModelCompanion[VocabularyTerm, ObjectId] {
  val COLLECTION = "vocabularyterms"
  val mongos: MongoStartup = DI.injector.getInstance(classOf[MongoStartup])
  val dao = new SalatDAO[VocabularyTerm, ObjectId](collection = mongos.collection(COLLECTION)) {}
}
