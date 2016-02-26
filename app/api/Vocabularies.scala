package api

import java.util.Date
import javax.inject.{Inject, Singleton}

import com.wordnik.swagger.annotations.{ApiOperation, Api}
import models.{ResourceRef, UUID, Vocabulary}
import play.api.Logger
import play.api.libs.json.JsValue
import play.api.libs.json._
import play.api.libs.json.{JsObject, JsValue}
import play.api.libs.json.Json.toJson
import services.{SpaceService, VocabularyService, UserService}

import scala.collection.immutable.List
import scala.util.Success

/**
  * Created by todd_n on 2/9/16.
  */
@Singleton
class Vocabularies @Inject() (vocabularyService: VocabularyService, userService : UserService, spaces: SpaceService) extends ApiController {

  @ApiOperation(value = "Get vocabulary",
    notes = "This will check for Permission.ViewVocabulary",
    responseClass = "None", httpMethod = "GET")
  def get(id: UUID) = PermissionAction(Permission.ViewVocabulary, Some(ResourceRef(ResourceRef.vocabulary, id))) { implicit request =>
    vocabularyService.get(id) match {
      case Some(vocab) => Ok(jsonVocabulary(vocab))
      case None => BadRequest(toJson("collection not found"))
    }
  }

  @ApiOperation(value = "Get vocabularies by author",
    notes = "",
    responseClass = "None", httpMethod = "GET")
  def getByAuthor() = PrivateServerAction  {implicit request =>

    val user = request.user

    user match {
      case Some(identity) => {
        val result = vocabularyService.getByAuthor(identity)
        Ok(toJson(result))
      }
      case None => BadRequest("No user matches that user")
    }
  }

  @ApiOperation(value = "Get vocabulary by name and author",
    notes = "",
    responseClass = "None", httpMethod = "GET")
  def getByName(name: String) = PrivateServerAction  {implicit request =>

    val user = request.user

    user match {
      case Some(identity) => {
        val result = vocabularyService.getByName(name)
        Ok(toJson(result))
      }
      case None => BadRequest("No user matches that user")
    }
  }

  @ApiOperation(value = "Get vocabulary by name and author",
    notes = "",
    responseClass = "None", httpMethod = "GET")
  def getByNameAndAuthor(name: String) = PrivateServerAction  {implicit request =>

    val user = request.user

    user match {
      case Some(identity) => {
        val result = vocabularyService.getByAuthorAndName(identity, name)
        Ok(toJson(result))
      }
      case None => BadRequest("No user matches that user")
    }
  }

  @ApiOperation(value = "List all vocabularies the user can view",
    notes = "This will check for Permission.ViewVocabulary",
    responseClass = "None", httpMethod = "GET")
  def list() = PrivateServerAction { implicit request =>
    val vocabs = vocabularyService.list()
    Ok(toJson(vocabs))
  }

  @ApiOperation(value = "Create a vocabulary",
    notes = "",
    responseClass = "None", httpMethod = "POST")
  def createVocabulary() = PermissionAction(Permission.CreateVocabulary){ implicit request =>
    Ok(toJson(Map("status"->"success")))
  }


  def createVocabularyFromForm() =  PermissionAction(Permission.CreateVocabulary) (parse.multipartFormData){ implicit request =>
    val user = request.user
    var formName = request.body.asFormUrlEncoded.getOrElse("name", null)
    var formKeys = request.body.asFormUrlEncoded.getOrElse("keys",null)
    var formTags = request.body.asFormUrlEncoded.getOrElse("tags",null)
    var formProject = request.body.asFormUrlEncoded.getOrElse("project",null)

    var name : String = ""
    if (formName != null){
      try {
        name = formName(0)
      } catch {
        case e : Exception => Logger.debug("no name provided")
      }
    }
    var keys = List.empty[String]
    if (formKeys != null) {
      keys = formKeys(0).split(',').toList
    }
    var tags = List.empty[String]
    if (formTags != null) {
      tags = formTags(0).split(',').toList
    }
    var v : Vocabulary = null

    user match {
      case Some(identity) => {
        v = Vocabulary(author = Some(identity), created = new Date(), name = name, keys = keys, tags = tags)
        vocabularyService.insert(v) match {
          case Some(id) => {
            Ok(toJson(Map("id" -> id)))
          }
          case None => Ok("insert did not provide id")
        }
      }
      case None => BadRequest("No user")
    }
  }

  def editVocabulary() = {

  }

  @ApiOperation(value = "Delete vocabulary",
    notes = "",
    responseClass = "None", httpMethod = "POST")
  def removeVocabulary(id: UUID) = PermissionAction(Permission.DeleteVocabulary, Some(ResourceRef(ResourceRef.vocabulary, id))){ implicit request =>
    vocabularyService.get(id) match {
      case Some(vocab) => {
        vocabularyService.delete(vocab.id)
      }
    }
    Ok(toJson((Map("status" -> "success"))))
  }

  @ApiOperation(value = "Add vocabulary to space",
    notes = "",
    responseClass = "None", httpMethod = "POST")
  def addToSpace(vocabId: UUID, spaceId : UUID) = PermissionAction(Permission.EditVocabulary, Some(ResourceRef(ResourceRef.vocabulary, vocabId))){ implicit request =>
    vocabularyService.get(vocabId) match {
      case Some(vocab) => {
        spaces.get(spaceId) match {
          case Some(space) => {
            vocabularyService.addToSpace(vocabId,spaceId)
            vocabularyService.get(vocabId) match {
              case Some(vocab) => Ok(jsonVocabulary(vocab))
              case None => BadRequest("could not add " + vocabId + " to space " + spaceId )
            }
          }
          case None => BadRequest("no space matches" + spaceId)
        }
      }
      case None => BadRequest("no vocabulary matches  " + vocabId)
    }
  }

  /*
  @ApiOperation(value = "Remove vocabulary from space",
    notes = "Does not delete the vocabulary.",
    responseClass = "None", httpMethod = "POST")
  def removeFromSpace(vocabId: UUID, spaceId: UUID) = PermissionAction(Permission.EditVocabulary, Some(ResourceRef(ResourceRef.vocabulary, vocabId))){
    vocabularyService.get(vocabId) match {
      case Some(vocab) => {
        spaces.get(spaceId) match {
          case Some(space) => {
            vocabularyService.removeFromSpace(vocabId,spaceId)
            vocabularyService.get(vocabId) match {
              case Some(vocab) => jsonVocabulary(vocab)
              case None => BadRequest("unable to remove " + vocabId + " from space " + spaceId)
            }
          }
          case None => BadRequest("no space matches" + spaceId)
        }
      }
      case None => BadRequest("no vocabulary matches  " + vocabId)
    }
  }
  */

  def jsonVocabulary(vocabulary : Vocabulary): JsValue = {
    toJson(Map("id" -> vocabulary.id.toString, "name" -> vocabulary.name.toString, "keys" -> vocabulary.keys.toString))
  }

}
