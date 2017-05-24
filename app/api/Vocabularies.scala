package api

import java.util.Date
import javax.inject.{Inject, Singleton}

import models.{VocabularyTerm, ResourceRef, UUID, Vocabulary}
import play.api.Logger
import play.api.libs.json.JsValue
import play.api.libs.json._
import play.api.libs.json.{JsObject, JsValue,JsArray}
import play.api.libs.json.Json.toJson
import play.api.mvc.BodyParsers.parse
import services.{VocabularyTermService, SpaceService, VocabularyService, UserService}

import scala.collection.immutable.List
import scala.collection.mutable.ListBuffer
import scala.util.Success


@Singleton
class Vocabularies @Inject() (vocabularyService: VocabularyService, vocabularyTermService : VocabularyTermService, userService : UserService, spaces: SpaceService) extends ApiController {

  def get(id: UUID) = PermissionAction(Permission.ViewVocabulary, Some(ResourceRef(ResourceRef.vocabulary, id))) { implicit request =>
    vocabularyService.get(id) match {
      case Some(vocab) => Ok(jsonVocabulary(vocab))
      case None => BadRequest(toJson("vocabulary not found"))
    }
  }

  def jsonVocabulary(vocabulary: Vocabulary): JsValue = {
    val terms = getVocabularyTerms(vocabulary)
    val author = vocabulary.author.get.identityId.userId
    Json.obj("id" -> vocabulary.id.stringify, "author" -> author, "name" -> vocabulary.name, "terms" -> terms, "keys" -> vocabulary.keys.mkString(","),"tags"->vocabulary.tags.mkString(","), "description" -> vocabulary.description, "isPublic" -> vocabulary.isPublic.toString, "spaces" -> vocabulary.spaces.mkString(","))
  }

  def getVocabularyTerms(vocabulary: Vocabulary): List[VocabularyTerm] = {
    var vocab_terms: ListBuffer[VocabularyTerm] = ListBuffer.empty
    if (!vocabulary.terms.isEmpty) {
      for (term <- vocabulary.terms) {
        val current_term = vocabularyTermService.get(term) match {
          case Some(vocab_term) => {
            vocab_terms += vocab_term
          }
        }
      }
    }

    vocab_terms.toList
  }

  def getByAuthor() = PrivateServerAction { implicit request =>
    val user = request.user

    user match {
      case Some(identity) => {
        val result = vocabularyService.getByAuthor(identity)
        Ok(toJson(result))
      }
      case None => BadRequest("No user matches that user")
    }
  }

  def getPublicVocabularies() = PrivateServerAction { implicit request =>
    val user = request.user

    user match {
      case Some(identity) => {
        val result = vocabularyService.listAll().filter( (v : Vocabulary) => (v.isPublic)).map((v : Vocabulary)=>jsonVocabulary(v))
        Ok(toJson(result))
      }
      case None => BadRequest("No public vocabularies found")
    }
  }

  def getAllTagsOfAllVocabularies() = PrivateServerAction {implicit request =>
    val user = request.user

    var allTags = scala.collection.mutable.Set[String]()

    user match {
      case Some(identity) => {
        val all_vocabularies : List[Vocabulary] = vocabularyService.listAll()
        for (each_vocab <- all_vocabularies){
          try {
            val current_tags = each_vocab.tags.toSet[String]
            allTags ++= current_tags
          } catch  {
            case e: Exception => Logger.debug("no name provided")
          }

        }
        Ok(toJson(allTags.toList))

      }
      case None => BadRequest("Bad request")
    }
  }

  def getByName(name: String) = PrivateServerAction { implicit request =>
    val user = request.user

    user match {
      case Some(identity) => {
        val result = vocabularyService.getByName(name).map((v: Vocabulary) => jsonVocabulary(v))
        Ok(toJson(result))
      }
      case None => BadRequest("No user matches that user")
    }
  }

  def getByNameAndAuthor(name: String) = PrivateServerAction { implicit request =>
    val user = request.user

    user match {
      case Some(identity) => {
        val result = vocabularyService.getByAuthorAndName(identity, name)
        Ok(toJson(result))
      }
      case None => BadRequest("No user matches that user")
    }
  }

  def list() = PrivateServerAction { implicit request =>
    val user = request.user
    val all_vocabs = vocabularyService.listAll()
    val vocabs = all_vocabs.filter(( v : Vocabulary) =>( v.author.get.identityId.userId == user.get.identityId.userId )).map((v: Vocabulary) => jsonVocabulary(v))

    Ok(toJson(vocabs))
  }

  def listAll() = PrivateServerAction { implicit request =>
    val user = request.user
    val all_vocabs = vocabularyService.listAll().map((v: Vocabulary) => jsonVocabulary(v))

    Ok(toJson(all_vocabs))
  }

  def editVocabulary(id : UUID) = AuthenticatedAction(parse.json) {implicit request =>
    val user = request.user
    user match {
      case Some(identity) => {
        val new_name = (request.body \ "name").asOpt[String].getOrElse("")
        //val keys = (request.body \ "keys").asOpt[String].getOrElse("")
        val new_description = (request.body \ "description").asOpt[String].getOrElse("")
        val new_tags = (request.body \ "tags").asOpt[String].getOrElse("")
        //parse a list of vocabterm from the json here
        val new_request_terms = (request.body \ "terms").asOpt[List[JsValue]].getOrElse(List.empty)
        var new_terms: ListBuffer[UUID] = ListBuffer.empty

        for (each_term <- new_request_terms) {
          val key = (each_term \ "key").asOpt[String].getOrElse("")
          val units = (each_term \ "units").asOpt[String].getOrElse("")
          val default_value = (each_term \ "default_value").asOpt[String].getOrElse("")
          val description = (each_term \ "description").asOpt[String].getOrElse("")
          val current_vocabterm: VocabularyTerm = VocabularyTerm(key = key, author = Option(identity), created = new Date(), units = units, default_value = default_value, description = description)

          vocabularyTermService.insert(current_vocabterm) match {
            case Some(id) => {
              Logger.debug("Vocabulary Term inserted")
              new_terms += (UUID(id))
            }
            case None => Logger.error("Could not insert vocabulary term")
          }
        }
        vocabularyService.get(id) match {
          case Some(vocabulary) => {
            vocabularyService.updateName(id,new_name )
            vocabularyService.updateDescription(id, new_description)
            vocabularyService.updateTags(id,new_tags.split(",").toList )
            for (each_new_term <- new_terms){
              vocabularyService.addVocabularyTerm(id, each_new_term)
            }
            Ok("Vocabulary with id " + id + " edited")
          }
          case None => BadRequest("No vocabulary with id " + id + " exists")
        }
      }
      case None => BadRequest("No user supplied")
    }
  }

  def createVocabularyFromJson(isPublic: Boolean) = AuthenticatedAction(parse.json) { implicit request =>
    val user = request.user
    var t : Vocabulary = null

    user match {
      case Some(identity) => {
        val name = (request.body \ "name").asOpt[String].getOrElse("")
        val keys = (request.body \ "keys").asOpt[String].getOrElse("")
        val description = (request.body \ "description").asOpt[String].getOrElse("")
        val tags = (request.body \ "tags").asOpt[String].getOrElse("")
        //parse a list of vocabterm from the json here
        val request_terms = (request.body \ "terms").asOpt[List[JsValue]].getOrElse(List.empty)
        var terms: ListBuffer[UUID] = ListBuffer.empty

        for (each_term <- request_terms) {
          val key = (each_term \ "key").asOpt[String].getOrElse("")
          val units = (each_term \ "units").asOpt[String].getOrElse("")
          val default_value = (each_term \ "default_value").asOpt[String].getOrElse("")
          val description = (each_term \ "description").asOpt[String].getOrElse("")
          val current_vocabterm: VocabularyTerm = VocabularyTerm(key = key, author = Option(identity), created = new Date(), units = units, default_value = default_value, description = description)

          vocabularyTermService.insert(current_vocabterm) match {
            case Some(id) => {
              Logger.debug("Vocabulary Term inserted")
              terms += (UUID(id))
            }
            case None => Logger.error("Could not insert vocabulary term")
          }
        }

        t = Vocabulary(author = Some(identity), created = new Date(), name = name, keys = keys.split(",").toList,tags = tags.split(",").toList, description = description, isPublic = isPublic, terms = terms.toList)

        vocabularyService.insert(t) match {
          case Some(id) => {
            Ok(toJson(Map("id" -> id)))
          }
          case None => BadRequest("could not create or save vocabulary")
        }

      }
      case None => BadRequest("no user supplied")
    }

  }

  private def deleteOldTerms(vocabId : UUID): Unit = {
    vocabularyService.get(vocabId) match {
      case Some(vocabulary) => {
        val terms : List[UUID] = vocabulary.terms
        for (term <- terms){
          vocabularyTermService.get(term) match {
            case Some(vocab_term) => {
              vocabularyTermService.delete(vocab_term.id)
              vocabularyService.removeVocabularyTermId(vocabId, vocab_term.id)
            }
            case None => {
              vocabularyService.removeVocabularyTermId(vocabId, term)
            }
          }
        }
      }
      case None => BadRequest("no vocabulary with id " + vocabId )
    }
  }

  def createVocabularyFromForm() = PermissionAction(Permission.CreateVocabulary)(parse.multipartFormData) { implicit request =>
    val user = request.user
    val formName = request.body.asFormUrlEncoded.getOrElse("name", null)
    val formKeys = request.body.asFormUrlEncoded.getOrElse("keys", null)
    val formDescription = request.body.asFormUrlEncoded.getOrElse("description", null)

    var name: String = ""
    if (formName != null) {
      try {
        name = formName(0)
      } catch {
        case e: Exception => Logger.debug("no name provided")
      }
    }
    var keys = List.empty[String]
    if (formKeys != null) {
      keys = formKeys(0).split(',').toList
    }
    var description : String = ""
    if (formDescription != null) {
      description = formDescription(0)
    }
    var v: Vocabulary = null

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


  def removeVocabulary(id: UUID) = PermissionAction(Permission.DeleteVocabulary, Some(ResourceRef(ResourceRef.vocabulary, id))) { implicit request =>
    vocabularyService.get(id) match {
      case Some(vocab) => {
        deleteOldTerms(vocab.id)
        vocabularyService.delete(vocab.id)
      }
      case None => Logger.error("No vocabulary exists for id " + id)
    }
    Ok(toJson((Map("status" -> "success"))))
  }

  def addToSpace(vocabId: UUID, spaceId: UUID) = PermissionAction(Permission.EditVocabulary, Some(ResourceRef(ResourceRef.vocabulary, vocabId))) { implicit request =>
    vocabularyService.get(vocabId) match {
      case Some(vocab) => {
        spaces.get(spaceId) match {
          case Some(space) => {
            vocabularyService.addToSpace(vocabId, spaceId)
            vocabularyService.get(vocabId) match {
              case Some(vocab) => Ok(jsonVocabulary(vocab))
              case None => BadRequest("could not add " + vocabId + " to space " + spaceId)
            }
          }
          case None => BadRequest("no space matches" + spaceId)
        }
      }
      case None => BadRequest("no vocabulary matches  " + vocabId)
    }
  }

  def removeFromSpace(vocabId: UUID, spaceId: UUID) = PermissionAction(Permission.EditVocabulary, Some(ResourceRef(ResourceRef.vocabulary, vocabId))) { implicit request =>
    vocabularyService.get(vocabId) match {
      case Some(vocab) => {
        spaces.get(spaceId) match {
          case Some(space) => {
            vocabularyService.removeFromSpace(vocabId, spaceId)
            vocabularyService.get(vocabId) match {
              case Some(vocab) => Ok(jsonVocabulary(vocab))
              case None => BadRequest("could not remove " + vocabId + " from space " + spaceId)
            }
          }
          case None => BadRequest("no space matches" + spaceId)
        }
      }
      case None => BadRequest("no vocabulary matches  " + vocabId)
    }
  }


  def getBySingleTag(tag : String) = PrivateServerAction(parse.json) { implicit request =>
    val user = request.user
    var tags  : ListBuffer[String] = ListBuffer.empty[String]
    tags += tag

    user match {
      case Some(identity) => {
        if (!tag.isEmpty) {
          Ok(toJson(vocabularyService.findByTag(tags.toList,false)))
        }
        else {
          Ok(toJson("no description provided"))
        }
      }
      case None => BadRequest(toJson("not found"))
    }
  }

  def getByTag(containsAll: Boolean) = PrivateServerAction(parse.json) { implicit request =>
    val user = request.user
    val tag = (request.body \ "tag").asOpt[String].getOrElse("").split(',').toList

    user match {
      case Some(identity) => {
        if (!tag.isEmpty) {
          Ok(toJson(vocabularyService.findByTag(tag, containsAll)))
        }
        else {
          Ok(toJson("no description provided"))
        }
      }
      case None => BadRequest(toJson("not found"))
    }
  }

}
