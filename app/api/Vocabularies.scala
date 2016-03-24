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
import play.api.mvc.BodyParsers.parse
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

  @ApiOperation(value = "Get vocabulary by name",
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

  @ApiOperation(value = "Create a vocabulary object",
    notes = "",
    responseClass = "None", httpMethod = "POST")
  def createVocabularyFromJson() = AuthenticatedAction (parse.json) { implicit request =>
    val user = request.user
    var t : Vocabulary = null
    user match {
      case Some(identity) => {
        (request.body \ "keys").asOpt[String] match {
          case Some(keys) => {
            val name = (request.body\"name").asOpt[String].getOrElse("")
            val description = (request.body \ "description").asOpt[String].getOrElse("")
            t = Vocabulary(author = Some(identity), created = new Date(),name = name,keys = keys.split(",").toList , description = description.split(',').toList)

            vocabularyService.insert(t) match {
              case Some(id) => {
                Ok(toJson(Map("id" -> id)))
              }
              case None => BadRequest("could not create vocbabulry")
            }

          }
          case None => BadRequest("no keys supplied")
        }

      }
      case None => BadRequest("no user supplied")
    }
  }


  def createVocabularyFromForm() =  PermissionAction(Permission.CreateVocabulary) (parse.multipartFormData){ implicit request =>
    val user = request.user
    var formName = request.body.asFormUrlEncoded.getOrElse("name", null)
    var formKeys = request.body.asFormUrlEncoded.getOrElse("keys",null)
    var formDescription = request.body.asFormUrlEncoded.getOrElse("description",null)
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
    var description = List.empty[String]
    if (formDescription != null) {
      description = formDescription(0).split(',').toList
    }
    var v : Vocabulary = null

    user match {
      case Some(identity) => {
        v = Vocabulary(author = Some(identity), created = new Date(), name = name, keys = keys, description = description)
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


  @ApiOperation(value = "Add vocabulary to space",
    notes = "",
    responseClass = "None", httpMethod = "POST")
  def removeFromSpace(vocabId: UUID, spaceId : UUID) = PermissionAction(Permission.EditVocabulary, Some(ResourceRef(ResourceRef.vocabulary, vocabId))){ implicit request =>
    vocabularyService.get(vocabId) match {
      case Some(vocab) => {
        spaces.get(spaceId) match {
          case Some(space) => {
            vocabularyService.removeFromSpace(vocabId,spaceId)
            vocabularyService.get(vocabId) match {
              case Some(vocab) => Ok(jsonVocabulary(vocab))
              case None => BadRequest("could not remove " + vocabId + " from space " + spaceId )
            }
          }
          case None => BadRequest("no space matches" + spaceId)
        }
      }
      case None => BadRequest("no vocabulary matches  " + vocabId)
    }
  }

  @ApiOperation(value = "Gets tags of a file", notes = "Returns a list of strings, List[String].", responseClass = "None", httpMethod = "GET")
  def getDescription(id: UUID) = PermissionAction(Permission.ViewVocabulary, Some(ResourceRef(ResourceRef.vocabulary, id))) { implicit request =>
    Logger.info("Getting tags for vocabulary with id " + id)
    if (UUID.isValid(id.stringify)) {
      vocabularyService.get(id) match {
        case Some(vocab) => Ok(Json.obj("id" -> vocab.id.toString, "name" -> vocab.name,
          "description" -> Json.toJson(vocab.description.toString)))
        case None => {
          Logger.error("The vocabulary with id " + id + " is not found.")
          NotFound(toJson("The vocabulary with id " + id + " is not found."))
        }
      }
    } else {
      Logger.error("The given id " + id + " is not a valid ObjectId.")
      BadRequest(toJson("The given id " + id + " is not a valid ObjectId."))
    }
  }

  @ApiOperation(value = "List all vocabularies the user can view with description",
    notes = "This will check for Permission.ViewVocabulary",
    responseClass = "None", httpMethod = "POST")
  def findByDescription() = PrivateServerAction (parse.multipartFormData) {implicit request=>
    val user = request.user
    val formDescription = request.body.asFormUrlEncoded.getOrElse("description",null)

    var description = List.empty[String]
    if (formDescription != null) {
      description = formDescription(0).split(',').toList
    }

    user match {
      case Some(identity) => {
        if (!description.isEmpty){
          Ok(toJson(vocabularyService.findByDescription(description)))
        }
        else {
          Ok(toJson("no description provided"))
        }
      }
      case None => BadRequest(toJson("not found"))
    }
  }


  def jsonVocabulary(vocabulary : Vocabulary): JsValue = {
    toJson(Map("id" -> vocabulary.id.toString, "name" -> vocabulary.name, "keys" -> vocabulary.keys.mkString(","), "description" -> vocabulary.description.mkString(",")))
  }

}
