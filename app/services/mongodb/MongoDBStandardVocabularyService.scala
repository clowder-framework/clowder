package services.mongodb

import java.util.Date

import com.mongodb.casbah.Imports.$set
import com.mongodb.casbah.WriteConcern
import com.mongodb.casbah.commons.MongoDBObject
import javax.inject.{Inject, Singleton}
import models.{StandardVocab, UUID}
import org.bson.types.ObjectId
import salat.dao.{ModelCompanion, SalatDAO}
import services.mongodb.MongoContext.context
import services.{DI, StandardVocabularyService}


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
  val COLLECTION = "metadata.vocabularies"
  val mongos: MongoStartup = DI.injector.getInstance(classOf[MongoStartup])
  val dao = new SalatDAO[StandardVocab, ObjectId](collection = mongos.collection(COLLECTION)) {}
}