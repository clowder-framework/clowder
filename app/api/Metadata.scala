package api

import api.Permission.Permission
import java.net.{URL, URLEncoder}
import java.util.Date

import controllers.Utils
import javax.inject.{Inject, Singleton}
import models.{ResourceRef, UUID, UserAgent, _}
import org.elasticsearch.action.search.SearchResponse
import org.apache.commons.lang.WordUtils
import play.api.Play.current
import play.api.Logger
import play.api.Play._
import play.api.libs.json.Json._
import play.api.libs.json._
import play.api.libs.ws.WS
import play.api.mvc.Result
import services._
import play.api.i18n.Messages
import play.api.libs.json.JsValue

import scala.collection.JavaConversions._
import scala.collection.JavaConverters._
import scala.collection.mutable.ListBuffer
import scala.concurrent.Future

/**
 * Manipulate generic metadata.
 */
@Singleton
class Metadata @Inject() (
  spaces: SpaceService,
  metadataService: MetadataService,
  contextService: ContextLDService,
  userService: UserService,
  datasets: DatasetService,
  files: FileService,
  curations: CurationService,
  vocabs: StandardVocabularyService,
  events: EventService,
  spaceService: SpaceService,
  routing: ExtractorRoutingService) extends ApiController {

  def getDefinitions() = PermissionAction(Permission.ViewDataset) {
    implicit request =>
      val vocabularies = metadataService.getDefinitions()
      Ok(toJson(vocabularies))
  }

  def getDefinitionsDistinctName() = PermissionAction(Permission.ViewDataset) {
    implicit request =>
      implicit val user = request.user
      val vocabularies = metadataService.getDefinitionsDistinctName(user)
      Ok(toJson(vocabularies))
  }

  /** Get set of metadata fields containing filter substring for autocomplete */
  def getAutocompleteName(query: String) = PermissionAction(Permission.ViewDataset) { implicit request =>
    implicit val user = request.user

    var listOfTerms = ListBuffer.empty[String]

    // First, get regular vocabulary matches
    val definitions = metadataService.getDefinitionsDistinctName(user)
    for (md_def <- definitions) {
      val currVal = (md_def.json \ "label").as[String]
      if (currVal.toLowerCase startsWith query.toLowerCase) {
        listOfTerms.append("metadata." + currVal)
      }
    }

    // Next get Elasticsearch metadata fields if plugin available
    current.plugin[ElasticsearchPlugin] match {
      case Some(plugin) => {
        val mdTerms = plugin.getAutocompleteMetadataFields(query)
        for (term <- mdTerms) {
          // e.g. "metadata.http://localhost:9000/clowder/api/extractors/terra.plantcv.angle",
          //      "metadata.Jane Doe.Alternative Title"
          if (!(listOfTerms contains term))
            listOfTerms.append(term)
        }
        Ok(toJson(listOfTerms.distinct))
      }
      case None => {
        BadRequest("Elasticsearch plugin is not enabled")
      }
    }
  }

  def getDefinitionsByDataset(id: UUID) = PermissionAction(Permission.AddMetadata, Some(ResourceRef(ResourceRef.dataset, id))) { implicit request =>
    implicit val user = request.user
    datasets.get(id) match {
      case Some(dataset) => {
        val metadataDefinitions = collection.mutable.HashSet[models.MetadataDefinition]()
        dataset.spaces.foreach { spaceId =>
          spaceService.get(spaceId) match {
            case Some(space) => metadataService.getDefinitions(Some(space.id)).foreach { definition => metadataDefinitions += definition }
            case None =>
          }
        }
        if (dataset.spaces.length == 0) {
          metadataService.getDefinitions().foreach { definition => metadataDefinitions += definition }
        }
        Ok(toJson(metadataDefinitions.toList))
      }
      case None => BadRequest(toJson("The request dataset does not exist"))
    }
  }

  def getMetadataDefinition(id: UUID) = PermissionAction(Permission.AddMetadata) {
    metadataService.getDefinition(id) match {
      case Some(metadata) => {
        Ok(toJson(metadata))
      }
      case None => BadRequest("not found this metadata definition: " + id)
    }
  }

  def getDefinition(id: UUID) = PermissionAction(Permission.AddMetadata).async { implicit request =>
    implicit val context = scala.concurrent.ExecutionContext.Implicits.global
    val foo = for {
      md <- metadataService.getDefinition(id)
      url <- (md.json \ "definitions_url").asOpt[String]
      // If original request had a Cookie header, copy it to the proxied request
      cookies <- request.headers.get("Cookie")
    } yield {
      WS.url(url).withHeaders(COOKIE -> cookies).get().map(response => (Status(response.status))(response.body.trim))
    }
    foo.getOrElse {
      Future(InternalServerError)
    }
  }

  def getUrl(inUrl: String) = PermissionAction(Permission.AddMetadata).async { implicit request =>
    // Use java.net.URI instead of URLDecoder.decode to decode the path.
    import java.net.URI
    Logger.debug("Metadata getUrl: inUrl = '" + inUrl + "'.")
    // Replace " " with "+", otherwise the decoded URL might contain spaces and break Ws.url(url).
    val url = new URI(inUrl).getPath().replaceAll(" ", "+")
    Logger.debug("Metadata getUrl decoded: url = '" + url + "'.")
    implicit val context = scala.concurrent.ExecutionContext.Implicits.global
    WS.url(url).get().map {
      response => Ok(response.body.trim)
    }
  }

  def addDefinitionToSpace(spaceId: UUID) = PermissionAction(Permission.EditSpace, Some(ResourceRef(ResourceRef.space, spaceId)))(parse.json) { implicit request =>
    implicit val user = request.user
    user match {
      case Some(u) => {
        var body = request.body
        if ((body \ "label").asOpt[String].isDefined && (body \ "type").asOpt[String].isDefined) {
          var uri = (body \ "uri").asOpt[String].getOrElse("")
          spaceService.get(spaceId) match {
            case Some(space) => {
              // assign a default uri if not specified
              if (uri == "") {
                // http://clowder.ncsa.illinois.edu/metadata/{uuid}#CamelCase
                uri = play.Play.application().configuration().getString("metadata.uri.prefix") + "/" + space.id.stringify + "#" + WordUtils.capitalize((body \ "label").as[String]).replaceAll("\\s", "")
                body = body.as[JsObject] + ("uri" -> Json.toJson(uri))
              }
              addDefinitionHelper(uri, body, Some(space.id), u, Some(space))
              Ok(JsObject(Seq("status" -> JsString("ok"))))
            }
            case None => BadRequest("The space does not exist")
          }
        } else {
          BadRequest(toJson("Invalid resource Type"))
        }
      }
      case None => BadRequest(toJson("Invalid user"))
    }
  }

  def addDefinition() = ServerAdminAction(parse.json) {
    implicit request =>
      request.user match {
        case Some(user) => {
          var body = request.body
          if ((body \ "label").asOpt[String].isDefined && (body \ "type").asOpt[String].isDefined) {
            var uri = (body \ "uri").asOpt[String].getOrElse("")
            // assign a default uri if not specified
            if (uri == "") {
              // http://clowder.ncsa.illinois.edu/metadata#CamelCase
              uri = play.Play.application().configuration().getString("metadata.uri.prefix") + "#" + WordUtils.capitalize((body \ "label").as[String]).replaceAll("\\s", "")
              body = body.as[JsObject] + ("uri" -> Json.toJson(uri))
            }
            addDefinitionHelper(uri, body, None, user, None)

            // Now, propagate global definition to all spaces
            // NOTE: argument list is REQUIRED when Configuration.permissions="private"
            val spaceList = spaceService.listAccess(0, Set[Permission](Permission.ViewSpace), request.user, request.user.fold(false)(_.superAdminMode), true, false, false)
            spaceList.foreach(space => {
              // assign a default uri if not specified
              if (uri == "") {
                // http://clowder.ncsa.illinois.edu/metadata/{uuid}#CamelCase
                uri = play.Play.application().configuration().getString("metadata.uri.prefix") + "/" + space.id.stringify + "#" + WordUtils.capitalize((body \ "label").as[String]).replaceAll("\\s", "")
                body = body.as[JsObject] + ("uri" -> Json.toJson(uri))
              }
              addDefinitionHelper(uri, body, Some(space.id), user, Some(space))
            })
            Ok(JsObject(Seq("status" -> JsString("ok"))))
          } else {
            BadRequest(toJson("Invalid Resource type"))
          }
        }
        case None => BadRequest(toJson("Invalid user"))
      }
  }

  // Return all standard vocabularies
  def getVocabularies() = AuthenticatedAction(parse.empty) {
    implicit request =>
      request.user match {
        case None => BadRequest(toJson("Invalid user"))
        case Some(u) => {
          val vocabList = vocabs.retrieve()
          Ok(toJson(vocabList))
        }
      }
  }

  // Given a vocab ID, look up and return the terms list of
  // the matching standard vocabulary
  def getVocabularyTerms(id: UUID) = AuthenticatedAction(parse.empty) {
    implicit request =>
      request.user match {
        case None => BadRequest(toJson("Invalid user"))
        case Some(u) => {
          vocabs.retrieve(id) match {
            case None => BadRequest(toJson("No standard vocabulary found with ID: " + id.stringify))
            case Some(vocab) => {
              Ok(toJson(vocab.terms))
            }
          }
        }
      }
  }

  // Given a vocab ID, look up and return the matching
  // standard vocabulary
  def getVocabulary(id: UUID) = AuthenticatedAction(parse.empty) {
    implicit request =>
      request.user match {
        case None => BadRequest(toJson("Invalid user"))
        case Some(u) => {
          vocabs.retrieve(id) match {
            case None => BadRequest(toJson("No standard vocabulary found with ID: " + id.stringify))
            case Some(v) => {
              Ok(toJson(v))
            }
          }
        }
      }
  }

  // Given a list of terms, create a new standard vocabulary from the list
  // Expects a JSON array of Strings as the request body
  def createVocabulary() = AuthenticatedAction(parse.json) {
    implicit request =>
      request.user match {
        case None => BadRequest(toJson("Invalid user"))
        case Some(u) => {
          val terms: List[String] = request.body.asOpt[List[String]]
            .getOrElse(List.empty)
            .map(term => term.trim())
            .filter(term => !term.isEmpty)
          if (terms.isEmpty()) {
            BadRequest(toJson("Empty terms list is not allowed"))
          } else {
            val vocabulary = vocabs.create(terms)
            Ok(toJson(vocabulary))
          }
        }
      }
  }

  // Given an ID, replace the entire terms list of a standard vocabulary
  // Expects a JSON array of Strings as the request body
  def updateVocabulary(id: UUID) = AuthenticatedAction(parse.json) {
    implicit request =>
      request.user match {
        case None => BadRequest(toJson("Invalid user"))
        case Some(u) => {
          vocabs.retrieve(id) match {
            case None => NotFound(toJson("No standard vocabulary found with ID: " + id.stringify))
            case Some(v) => {
              // Update and return vocabulary
              val terms: List[String] = request.body.asOpt[List[String]]
                .getOrElse(List.empty)
                .map(term => term.trim())
                .filter(term => !term.isEmpty)
              if (terms.isEmpty()) {
                BadRequest(toJson("Empty terms list is not allowed"))
              } else {
                val vocabulary = vocabs.update(id, terms)
                Ok(toJson(vocabulary))
              }
            }
          }
        }
      }
  }

  // Given an ID, delete the standard vocabulary with that ID
  def deleteVocabulary(id: UUID) = AuthenticatedAction(parse.empty) {
    implicit request =>
      request.user match {
        case None => BadRequest(toJson("Invalid user"))
        case Some(u) => {
          vocabs.retrieve(id) match {
            case None => NotFound(toJson("No standard vocabulary found with ID: " + id.stringify))
            case Some(v) => {
              vocabs.delete(id)
              Ok(toJson(v))
            }
          }
        }
      }
  }

  // On GUI, URI is not required, however URI is required in DB. a default one
  // will be generated when needed.
  private def addDefinitionHelper(uri: String, body: JsValue, spaceId: Option[UUID], user: User, space: Option[ProjectSpace]): Result = {
    metadataService.getDefinitionByUriAndSpace(uri, space map { _.id.toString() }) match {
      case Some(metadata) => BadRequest(toJson("Metadata definition with same uri exists."))
      case None => {
        val definition = MetadataDefinition(json = body, spaceId = spaceId)
        metadataService.addDefinition(definition)
        space match {
          case Some(s) => {
            events.addObjectEvent(Some(user), s.id, s.name, "added_metadata_space")
          }
          case None => {
            events.addEvent(new Event(user.getMiniUser, None, None, None, None, None, "added_metadata_instance", new Date()))
          }
        }
        Ok(JsObject(Seq("status" -> JsString("ok"))))
      }
    }
  }

  def editDefinition(id: UUID, spaceId: Option[String]) = AuthenticatedAction(parse.json) {
    implicit request =>
      request.user match {
        case Some(user) => {
          val body = request.body
          if ((body \ "label").asOpt[String].isDefined && (body \ "type").asOpt[String].isDefined && (body \ "uri").asOpt[String].isDefined) {
            val uri = (body \ "uri").as[String]
            metadataService.getDefinitionByUriAndSpace(uri, spaceId) match {
              case Some(metadata) => if (metadata.id != id) {
                BadRequest(toJson("Metadata definition with same uri exists."))
              } else {

                metadata.spaceId match {
                  case Some(spaceId) if Permission.checkPermission(Some(user), Permission.EditSpace, Some(ResourceRef(ResourceRef.space, spaceId))) => {
                    metadataService.editDefinition(id, body)
                    spaceService.get(spaceId) match {
                      case Some(s) => events.addObjectEvent(Some(user), s.id, s.name, "edit_metadata_space")
                      case None =>
                    }
                    Ok(JsObject(Seq("status" -> JsString("ok"))))
                  }
                  case None if Permission.checkServerAdmin(Some(user)) => {
                    metadataService.editDefinition(id, body)
                    events.addEvent(new Event(user.getMiniUser, None, None, None, None, None, "edit_metadata_instance", new Date()))
                    Ok(JsObject(Seq("status" -> JsString("ok"))))
                  }
                  case _ => {
                    Unauthorized(" Not Authorized")
                  }
                }

              }
              case None => {
                metadataService.editDefinition(id, body)
                Ok(JsObject(Seq("status" -> JsString("ok"))))
              }
            }
          } else {
            BadRequest(toJson("Invalid resource type"))
          }

        }
        case None => BadRequest(toJson("Invalid user"))
      }
  }

  def deleteDefinition(id: UUID) = AuthenticatedAction { implicit request =>
    implicit val user = request.user
    user match {
      case Some(user) => {
        metadataService.getDefinition(id) match {
          case Some(md) => {

            md.spaceId match {
              case Some(spaceId) if Permission.checkPermission(Some(user), Permission.EditSpace, Some(ResourceRef(ResourceRef.space, spaceId))) => {
                metadataService.deleteDefinition(id)
                spaceService.get(spaceId) match {
                  case Some(s) => events.addObjectEvent(Some(user), s.id, s.name, "delete_metadata_space")
                  case None =>
                }
                Ok(JsObject(Seq("status" -> JsString("ok"))))
              }
              case None if Permission.checkServerAdmin(Some(user)) => {
                metadataService.deleteDefinition(id)
                events.addEvent(new Event(user.getMiniUser, None, None, None, None, None, "delete_metadata_instance", new Date()))

                // FIXME: How should we handle URI conflicts between global and space?
                // FIXME: propagate global deletion to all spaces
                /*
                val mdUri = (md.json \ "uri").toString().replace("\"", "")
                spaceService.list().foreach(space => {
                  metadataService.getDefinitionByUriAndSpace(mdUri, Option(space.id.toString())) match {
                    case Some(spaceMd) => {
                      metadataService.deleteDefinition(spaceMd.id)
                      events.addObjectEvent(Some(user), space.id, space.name, "delete_metadata_space")
                    }
                    case None => Logger.debug("Skipping deletion.. no metadata defn found for (uri, spaceId) = " +
                      "(" + mdUri.toString() + ", " + space.id.toString() + ")")
                  }
                })
                */
                Ok(JsObject(Seq("status" -> JsString("ok"))))
              }
              case _ => {
                Unauthorized(" Not Authorized")
              }
            }

          }
          case None => BadRequest(toJson("Invalid metadata definition"))
        }

      }
      case None => BadRequest(toJson("Invalid user"))
    }
  }

  def addUserMetadata() = PermissionAction(Permission.AddMetadata)(parse.json) {
    implicit request =>
      request.user match {
        case Some(user) => {
          val json = request.body

          // when the new metadata is added
          val createdAt = new Date()

          // build creator uri
          // TODO switch to internal id and then build url when returning?
          val userURI = controllers.routes.Application.index().absoluteURL() + "api/users/" + user.id
          val creator = UserAgent(user.id, "cat:user", MiniUser(user.id, user.fullName, user.avatarUrl.getOrElse(""), user.email), Some(new URL(userURI)))

          val context: JsValue = (json \ "@context")

          // figure out what resource this is attached to
          val attachedTo =
            if ((json \ "file_id").asOpt[String].isDefined)
              Some(ResourceRef(ResourceRef.file, UUID((json \ "file_id").as[String])))
            else if ((json \ "dataset_id").asOpt[String].isDefined)
              Some(ResourceRef(ResourceRef.dataset, UUID((json \ "dataset_id").as[String])))
            else if ((json \ "curationObject_id").asOpt[String].isDefined)
              Some(ResourceRef(ResourceRef.curationObject, UUID((json \ "curationObject_id").as[String])))
            else if ((json \ "curationFile_id").asOpt[String].isDefined)
              Some(ResourceRef(ResourceRef.curationFile, UUID((json \ "curationFile_id").as[String])))
            else None

          // check if the context is a URL to external endpoint
          val contextURL: Option[URL] = context.asOpt[String].map(new URL(_))

          // check if context is a JSON-LD document
          val contextID: Option[UUID] =
            if (context.isInstanceOf[JsObject]) {
              context.asOpt[JsObject].map(contextService.addContext(new JsString("context name"), _))
            } else if (context.isInstanceOf[JsArray]) {
              context.asOpt[JsArray].map(contextService.addContext(new JsString("context name"), _))
            } else None

          //parse the rest of the request to create a new models.Metadata object
          val content = (json \ "content")
          val version = None

          if (attachedTo.isDefined) {
            val metadata = models.Metadata(UUID.generate, attachedTo.get, contextID, contextURL, createdAt, creator,
              content, version)

            // add metadata to mongo
            val metadataId = metadataService.addMetadata(metadata)
            val mdMap = metadata.getExtractionSummary

            attachedTo match {
              case Some(resource) => {
                resource.resourceType match {
                  case ResourceRef.dataset => {
                    datasets.index(resource.id)
                    //send RabbitMQ message
                    datasets.get(resource.id) match {
                      case Some(ds) => {
                        events.addObjectEvent(Some(user), resource.id, ds.name, EventType.ADD_METADATA_DATASET.toString)
                      }
                    }
                    routing.metadataAddedToResource(metadataId, resource, mdMap, Utils.baseUrl(request), request.apiKey, request.user)
                  }
                  case ResourceRef.file => {
                    files.index(resource.id)
                    //send RabbitMQ message
                    files.get(resource.id) match {
                      case Some(f) => {
                        events.addObjectEvent(Some(user), resource.id, f.filename, EventType.ADD_METADATA_FILE.toString)
                      }
                    }
                    routing.metadataAddedToResource(metadataId, resource, mdMap, Utils.baseUrl(request), request.apiKey, request.user)
                  }
                  case _ =>
                    Logger.error("File resource type not recognized")
                }
              }
              case None =>
                Logger.error("Metadata missing attachedTo subdocument")
            }

            // FIXME: the API should return JSON, not raw HTML
            // Emit our newly-created metadata as both a new card and a new table row
            Ok(Json.obj(
              "cards" -> views.html.metadatald.newCard(metadata)(request.user).toString(),
              "table" -> views.html.metadatald.newTableRow(metadata)(request.user).toString()
            ))
          } else {
            BadRequest(toJson("Invalid resource type"))
          }
        }
        case None => BadRequest(toJson("Invalid user"))
      }
  }

  def removeMetadata(id: UUID) = PermissionAction(Permission.DeleteMetadata, Some(ResourceRef(ResourceRef.metadata, id))) { implicit request =>
    request.user match {
      case Some(user) => {
        metadataService.getMetadataById(id) match {
          case Some(m) => {
            if (m.attachedTo.resourceType == ResourceRef.curationObject && curations.get(m.attachedTo.id).map(_.status != "In Preparation").getOrElse(false)
              || m.attachedTo.resourceType == ResourceRef.curationFile && curations.getCurationByCurationFile(m.attachedTo.id).map(_.status != "In Preparation").getOrElse(false)) {
              BadRequest("Publication Request has already been submitted")
            } else {
              metadataService.removeMetadata(id)
              val mdMap = m.getExtractionSummary

              m.attachedTo.resourceType match {
                case ResourceRef.file => {
                  current.plugin[ElasticsearchPlugin].foreach { p =>
                    // Delete existing index entry and re-index
                    p.delete(m.attachedTo.id.stringify)
                    files.index(m.attachedTo.id)
                  }
                  routing.metadataRemovedFromResource(id, m.attachedTo, Utils.baseUrl(request),
                      request.apiKey, request.user)
                }
                case ResourceRef.dataset => {
                  current.plugin[ElasticsearchPlugin].foreach { p =>
                    // Delete existing index entry and re-index
                    p.delete(m.attachedTo.id.stringify)
                    datasets.index(m.attachedTo.id)
                  }
                  routing.metadataRemovedFromResource(id, m.attachedTo, Utils.baseUrl(request), request.apiKey, request.user)
                }
                case _ => {
                  Logger.error("Unknown attached resource type for metadata")
                }
              }

              Ok(JsObject(Seq("status" -> JsString("ok"))))
            }
          }
          case None => BadRequest(toJson("Invalid Metadata"))
        }
      }
      case None => BadRequest("Not authorized.")
    }
  }

  def getPerson(pid: String) = PermissionAction(Permission.ViewMetadata).async { implicit request =>

    implicit val context = scala.concurrent.ExecutionContext.Implicits.global
    val peopleEndpoint = (play.Play.application().configuration().getString("people.uri"))
    if (peopleEndpoint != null) {
      val endpoint = (peopleEndpoint + "/" + URLEncoder.encode(pid, "UTF-8"))
      val futureResponse = WS.url(endpoint).get()
      var jsonResponse: play.api.libs.json.JsValue = new JsArray()
      var success = false
      val result = futureResponse.map {
        case response =>
          if (response.status >= 200 && response.status < 300 || response.status == 304) {
            Ok(response.json).as("application/json")
          } else {
            if (response.status == 404) {

              NotFound(toJson(Map("failure" -> { "Person with identifier " + pid + " not found" }))).as("application/json")

            } else {
              InternalServerError(toJson(Map("failure" -> { "Status: " + response.status.toString() + " returned from SEAD /api/people/<id> service" }))).as("application/json")
            }
          }
      }
      result
    } else {
      //TBD - see what Clowder knows
      Future(NotFound(toJson(Map("failure" -> { "Person with identifier " + pid + " not found" }))).as("application/json"))
    }
  }

  def listPeople(term: String, limit: Int) = PermissionAction(Permission.ViewMetadata).async { implicit request =>

    implicit val context = scala.concurrent.ExecutionContext.Implicits.global
    val endpoint = (play.Play.application().configuration().getString("people.uri"))
    if (play.api.Play.current.plugin[services.StagingAreaPlugin].isDefined && endpoint != null) {

      val futureResponse = WS.url(endpoint).get()
      var jsonResponse: play.api.libs.json.JsValue = new JsArray()
      var success = false
      val lcTerm = term.toLowerCase()
      val result = futureResponse.map {
        case response =>
          if (response.status >= 200 && response.status < 300 || response.status == 304) {
            val people = (response.json \ ("persons")).as[List[JsObject]]
            Ok(Json.toJson(people.map { t =>
              val fName = t \ ("givenName")
              val lName = t \ ("familyName")
              val name = JsString(fName.as[String] + " " + lName.as[String])
              val email = t \ ("email") match {
                case JsString(_) => t \ ("email")
                case _ => JsString("")
              }
              Map("name" -> name, "@id" -> t \ ("@id"), "email" -> email)

            }.filter((x) => {
              if (term.length == 0) {
                true
              } else {
                Logger.debug(lcTerm)

                ((x.getOrElse("name", new JsString("")).as[String].toLowerCase().contains(lcTerm)) ||
                  x.getOrElse("@id", new JsString("")).as[String].toLowerCase().contains(lcTerm) ||
                  x.getOrElse("email", new JsString("")).as[String].toLowerCase().contains(lcTerm))
              }
            }).take(limit))).as("application/json")
          } else {
            if (response.status == 404) {

              NotFound(toJson(Map("failure" -> { "People not found" }))).as("application/json")

            } else {
              InternalServerError(toJson(Map("failure" -> { "Status: " + response.status.toString() + " returned from SEAD /api/people service" }))).as("application/json")
            }
          }
      }
      result
    } else { //TBD - just get list of Clowder users
      /*  val lcTerm = term.toLowerCase()
      Future(Ok(Json.toJson(userService.list.map(jsonPerson).filter((x) => {
        if (term.length == 0) {
          true
        } else {
          Logger.debug(lcTerm)

          (((x \ "name").as[String].toLowerCase().contains(lcTerm)) ||
            (x \ "@id").as[String].toLowerCase().contains(lcTerm) ||
            (x \ "email").as[String].toLowerCase().contains(lcTerm))
        }
      }).take(limit))).as("application/json"))
      */
      Future(NotFound(toJson(Map("failure" -> { "People not found" }))).as("application/json"))
    }
  }

  def jsonPerson(user: User): JsObject = {
    Json.obj(
      "name" -> user.fullName,
      "@id" -> user.id.stringify,
      "email" -> user.email)
  }

  def getRepository(id: String) = PermissionAction(Permission.ViewMetadata).async { implicit request =>

    implicit val context = scala.concurrent.ExecutionContext.Implicits.global
    val repoEndpoint = (play.Play.application().configuration().getString("SEADservices.uri")) + "repositories"
    if (repoEndpoint != null) {
      val endpoint = (repoEndpoint + "/" + URLEncoder.encode(id, "UTF-8"))
      val futureResponse = WS.url(endpoint).get()
      var success = false
      val result = futureResponse.map {
        case response =>
          if (response.status >= 200 && response.status < 300 || response.status == 304) {
            Ok(response.json).as("application/json")
          } else {
            if (response.status == 404) {

              NotFound(toJson(Map("failure" -> { "Repository with identifier " + id + " not found" }))).as("application/json")

            } else {
              InternalServerError(toJson(Map("failure" -> { "Status: " + response.status.toString() + " returned from SEAD /api/repository/<id> service" }))).as("application/json")
            }
          }
      }
      result
    } else {
      Future(NotFound(toJson(Map("failure" -> { "Repository with identifier " + id + " not found" }))).as("application/json"))
    }
  }

}
