package services


import models.{UUID, Vocabulary}

import scala.util.Try

/**
  * Created by todd_n on 2/9/16.
  */
trait VocabularyService {

  def count(): Long

  def insert(vocabulary : Vocabulary) : Option[String]

  def get(id : UUID) : Option[Vocabulary]

  def delete(id: UUID) : Try[Unit]

  def list() : List[Vocabulary]

  def addToSpace(vocabId: UUID, spaceId : UUID) : Try[Unit]

  def removeFromSpace(vocabId : UUID, spaceId : UUID) : Try[Unit]

}
