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
import services.{VocabularyService, UserService}
import play.api.Play._
import scala.util.{Success, Try}

/**
  * Created by todd_n on 2/9/16.
  */
@Singleton
class MongoDBVocabularyService @Inject() (userService: UserService) extends VocabularyService {

  def count() : Long = {
    Vocabulary.count( MongoDBObject())
  }

  def insert(vocabulary : Vocabulary) : Option[String] = {
    Vocabulary.insert(vocabulary).map(_.toString)
  }

  def list() : List[Vocabulary] = {
    Vocabulary.findAll().toList
  }

  def get(id: UUID) : Option[Vocabulary] = {
    Vocabulary.findOneById(new ObjectId(id.stringify))
  }

  def getByName(name : String) : List[Vocabulary] = {
    Vocabulary.dao.find(MongoDBObject("name"->name)).toList
  }

  def getByAuthorAndName(author : Identity, name : String) : List[Vocabulary] = {
    val list = Vocabulary.findAll().toList
    val byAuthorName = list.filter(p => (p.author.get.identityId == author.identityId ) && (p.name == name))
    return byAuthorName
  }

  def delete(id : UUID) = Try {
    Vocabulary.findOneById(new ObjectId(id.stringify)) match {
      case Some(vocab) => {
        Vocabulary.remove(MongoDBObject("_id" -> new ObjectId(vocab.id.stringify)))
        Success
      }
      case None => Success
    }
  }

  def addToSpace(vocabId: UUID, spaceId: UUID) = Try{
    val result = Vocabulary.update(
      MongoDBObject("_id" -> new ObjectId(vocabId.stringify)),
      $addToSet("spaces" -> Some(new ObjectId(spaceId.stringify))),
      false, false)
  }

  def removeFromSpace(vocabId: UUID, spaceId : UUID) = Try {
    val result = Vocabulary.update(
      MongoDBObject("_id" -> new ObjectId(vocabId.stringify)),
      $pull("spaces" -> Some(new ObjectId(spaceId.stringify))),
      false, false)
  }


}

object Vocabulary extends ModelCompanion[Vocabulary, ObjectId] {
  val dao = current.plugin[MongoSalatPlugin] match {
    case None => throw new RuntimeException("No MongoSalatPlugin");
    case Some(x) => new SalatDAO[Vocabulary, ObjectId](collection = x.collection("vocabularies")) {}
  }
}
