package services.mongodb

import com.mongodb.casbah.Imports._
import securesocial.core.Identity
import services.mongodb.MongoContext.context
import com.mongodb.casbah.commons.MongoDBObject

import com.novus.salat.dao.{SalatMongoCursor, ModelCompanion, SalatDAO}
import org.bson.types.ObjectId
import models._
import javax.inject.{Singleton, Inject}


import com.mongodb.casbah.WriteConcern
import services.{VocabularyTermService, VocabularyService, UserService}
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
  val dao = current.plugin[MongoSalatPlugin] match {
    case None => throw new RuntimeException("No MongoSalatPlugin");
    case Some(x) => new SalatDAO[VocabularyTerm, ObjectId](collection = x.collection("vocabularyterms")) {}
  }
}
