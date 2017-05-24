package api

import java.util.Date
import javax.inject.{Inject, Singleton}

import models.{ResourceRef, UUID, Vocabulary, VocabularyTerm}
import play.api.Logger
import play.api.libs.json.Json.toJson
import play.api.libs.json.{JsValue, _}
import services.{VocabularyTermService, SpaceService, UserService, VocabularyService}

import scala.collection.immutable.List


@Singleton
class VocabularyTerms @Inject()(vocabularyTermService: VocabularyTermService, userService : UserService) extends ApiController {

  def get(id: UUID) = PermissionAction(Permission.ViewVocabularyTerm, Some(ResourceRef(ResourceRef.vocabularyterm, id))) { implicit request =>
    vocabularyTermService.get(id) match {
      case Some(vocabterm) => Ok(jsonVocabularyTerm(vocabterm))
      case None => BadRequest(toJson("vocabularyterm not found"))
    }
  }

  def createVocabTermFromJson = AuthenticatedAction (parse.json) { implicit request =>
    val user = request.user
    user match {
      case Some(identity) => {
        Ok("")
      }
      case None => Ok("")
    }
  }


  def list() = PrivateServerAction { implicit request =>
    val vocabs = vocabularyTermService.listAll()
    Ok(toJson(vocabs))
  }



  def removeVocabularyTerm(id: UUID) = PermissionAction(Permission.DeleteVocabularyTerm, Some(ResourceRef(ResourceRef.vocabularyterm, id))){ implicit request =>
    vocabularyTermService.get(id) match {
      case Some(vocabterm) => {
        vocabularyTermService.delete(vocabterm.id)
      }
    }
    Ok(toJson((Map("status" -> "success"))))
  }


  def jsonVocabularyTerm(vocabularyTerm : VocabularyTerm) : JsValue = {
    toJson(Map("id"->vocabularyTerm.id.toString,"key"->vocabularyTerm.key))
  }

}
