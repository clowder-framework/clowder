package services.mongodb

import com.novus.salat.dao.{ModelCompanion, SalatDAO}
import org.bson.types.ObjectId
import models._
import javax.inject.{Singleton, Inject}
import com.mongodb.casbah.commons.MongoDBObject
import com.mongodb.casbah.Imports._
import com.mongodb.casbah.WriteConcern
import com.mongodb.casbah.commons.MongoDBObject
import services.{VocabularyService, UserService}

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

  def delete(id : UUID) = Try {
    get(id) match {
      case Some(vocab) => {
        Vocabulary.remove(MongoDBObject("_id" -> new ObjectId(vocab.id.stringify)))
        Success
      }
      case None => Success
    }
  }


}

object Vocabulary extends ModelCompanion[Vocabulary, ObjectId] {
  val dao = current.plugin[MongoSalatPlugin] match {
    case None => throw new RuntimeException("No MongoSalatPlugin");
    case Some(x) => new SalatDAO[Vocabulary, ObjectId](collection = x.collection("vocabularies")) {}
  }
}
