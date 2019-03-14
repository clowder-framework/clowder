package services

import com.mongodb.WriteResult
import models.{StandardVocab, UUID}
import org.bson.types.ObjectId

trait StandardVocabularyService {

  def create(terms: List[String]): StandardVocab

  def retrieve() : List[StandardVocab]

  def retrieve(id: UUID) : Option[StandardVocab]

  def update(id: UUID, terms : List[String]): Option[StandardVocab]

  def delete(id: UUID) : WriteResult

}
