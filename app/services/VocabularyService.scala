package services


import com.novus.salat.dao.SalatMongoCursor
import models.{VocabularyTerm, UUID, Vocabulary}
import securesocial.core.Identity

import scala.util.Try


trait VocabularyService {

  def count(): Long

  def insert(vocabulary : Vocabulary) : Option[String]

  def get(id : UUID) : Option[Vocabulary]

  def updateName(vocabId : UUID, name : String)

  def updateDescription(vocabId: UUID, description : String)

  def updateTags(vocabId : UUID, tags : List[String])

  def getByName(name : String ) : List[Vocabulary]

  def getByAuthor(author: Identity) : List[Vocabulary]

  def getByAuthorAndName(author : Identity, name : String) : List[Vocabulary]

  def delete(id: UUID) : Try[Unit]

  def listAll() : List[Vocabulary]

  def addToSpace(vocabId: UUID, spaceId : UUID) : Try[Unit]

  def removeFromSpace(vocabId : UUID, spaceId : UUID) : Try[Unit]

  def findByTag(tag : List[String], containsAll : Boolean) : List[Vocabulary]

  def addVocabularyTerm(vocabId: UUID, vocabTermId : UUID) : Try[Unit]

  def removeVocabularyTermId(vocabId : UUID, vocabTermId : UUID) : Try[Unit]

  def makePublic(vocabId : UUID) : Try[Unit]

  def makePrivate(vocabId : UUID) : Try[Unit]

}
