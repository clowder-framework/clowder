package services

import models.{VocabularyTerm, UUID}

import scala.util.Try

/**
  * Created by todd_n on 2/9/16.
  */
trait VocabularyTermService {

  def count(): Long

  def insert(vocabularyTerm : VocabularyTerm) : Option[String]

  def get(id : UUID) : Option[VocabularyTerm]

  def delete(id: UUID) : Try[Unit]

  def listAll() : List[VocabularyTerm]


}
