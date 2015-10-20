package models

import java.net.URL

import play.api.Logger
import play.api.libs.json._
import services.{MetadataService, DI, UserService}

/**
 * Definition of metadata fields to present to the user a list of options.
 * This can be defined local within the local instance or retrieved from a remote server.
 */
case class MDVocabularyDefinition(
  id: UUID = UUID.generate(),
//  remoteURL: Option[URL] = None,
  spaceId: Option[UUID] = None,
  json: JsValue
)

object MDVocabularyDefinition {

  implicit val repositoryFormat = Json.format[MDVocabularyDefinition]

  /** Register default definitions that every instance should have **/
  def registerDefaultVacabularyDefinitions(): Unit = {
    // add default definition
    Logger.debug("Adding core metadata vocabulary definitions to database")
    val metadataService: MetadataService = DI.injector.getInstance(classOf[MetadataService])
    val default = List(
      Json.parse("""
        {"label":"Abstract",
          "uri":"http://purl.org/dc/terms/abstract",
          "type":"string"}"""),
      Json.parse("""{"label":"Audience",
          "uri":"http://purl.org/dc/terms/audience",
          "type":"string"}"""),
      Json.parse("""{"label":"Alternative Title",
          "uri":"http://purl.org/dc/terms/alternative",
          "type":"string"}"""),
      Json.parse("""{"label":"References",
          "uri":"http://purl.org/dc/terms/references",
          "type":"string"}"""),
        Json.parse("""{"label":"CSDMS Standard Name",
          "uri":"http://csdms.colorado.edu/wiki/CSN_Searchable_List",
          "type":"list",
          "definitions_url":"http://ecgs.ncsa.illinois.edu/gsis/CSN"}"""),
        Json.parse("""{
          "label":"ODM2 Variable Name",
          "uri":"http://vocabulary.odm2.org/variablename",
          "type":"list",
          "definitions_url":"http://ecgs.ncsa.illinois.edu/gsis/sas/sn/odm2"}"""),
        Json.parse("""{
          "label":"SAS Variable Name",
          "uri":"http://ecgs.ncsa.illinois.edu/gsis/sas/vars",
          "type":"listjquery",
          "definitions_url":"http://ecgs.ncsa.illinois.edu/gsis/sas/vars/map"}""")
    )
    default.map(d => metadataService.addVocabularyDefinition(MDVocabularyDefinition(json = d)))
  }
}
