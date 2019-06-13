package services.mongodb

import java.util.Date

import com.mongodb.casbah.Imports.$set
import com.mongodb.casbah.WriteConcern
import com.mongodb.casbah.commons.MongoDBObject
import services.mongodb.MongoContext.context
import com.novus.salat.dao.{ModelCompanion, SalatDAO}
import org.bson.types.ObjectId
import models.{StandardVocab, UUID}
import javax.inject.{Inject, Singleton}
import services.StandardVocabularyService
import play.api.Play._


@Singleton
class MongoDBStandardVocabularyService @Inject() extends StandardVocabularyService {

  def create(terms: List[String]) = {
    val vocabulary = StandardVocab(terms=terms)
    StandardVocabulary.insert(vocabulary)
    vocabulary
  }

  def retrieve()  = {
    StandardVocabulary.findAll().toList
  }

  def retrieve(id: UUID)  = {
    StandardVocabulary.findOneById(new ObjectId(id.stringify))
  }

  def update(id: UUID, terms: List[String]) = {
    StandardVocabulary.update(
      q=MongoDBObject("_id" -> new ObjectId(id.stringify)),
      o=$set("terms" -> terms, "lastModified" -> new Date()),
      upsert=false,
      multi=false,
      wc=WriteConcern.Safe)
    this.retrieve(id)
  }

  def delete(id: UUID) = {
    StandardVocabulary.removeById(new ObjectId(id.stringify))
  }
}

object StandardVocabulary extends ModelCompanion[StandardVocab, ObjectId] {
  val dao = current.plugin[MongoSalatPlugin] match {
    case None => throw new RuntimeException("No MongoSalatPlugin");
    case Some(x) => new SalatDAO[StandardVocab, ObjectId](collection = x.collection("metadata.vocabularies")) {}
  }
}