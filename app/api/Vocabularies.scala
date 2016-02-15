package api

import javax.inject.{Inject, Singleton}

import com.wordnik.swagger.annotations.Api
import models.{ResourceRef, UUID, Vocabulary}
import play.api.libs.json.JsValue
import play.api.libs.json._
import play.api.libs.json.{JsObject, JsValue}
import play.api.libs.json.Json.toJson
import services.{VocabularyService, UserService}

/**
  * Created by todd_n on 2/9/16.
  */
@Singleton
class Vocabularies @Inject() (vocabularyService: VocabularyService, userService : UserService)extends ApiController {

  def get(id: UUID) = PermissionAction(Permission.ViewVocabulary, Some(ResourceRef(ResourceRef.vocabulary, id))) { implicit request =>
    vocabularyService.get(id) match {
      case Some(vocab) => Ok(jsonVocabulary(vocab))
      case None => BadRequest(toJson("collection not found"))
    }
  }

  def list() = {

  }

  def createVocabulary() = {

  }

  def editVocabulary() = {

  }

  def delete() = {

  }

  def jsonVocabulary(vocabulary : Vocabulary): JsValue = {
    toJson(Map("id" -> vocabulary.id.toString, "name" -> vocabulary.name.toString, "keys" -> vocabulary.keys.toString))
  }

}
