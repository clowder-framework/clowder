package models

import play.api.Logger
import play.api.libs.json._
import services.{MetadataService, DI}

/**
 * Definition of metadata fields to present to the user a list of options.
 * This can be defined local within the local instance or retrieved from a remote server.
 */
case class MetadataDefinition(
  id: UUID = UUID.generate(),
//  remoteURL: Option[URL] = None,
  spaceId: Option[UUID] = None,
  json: JsValue
)

object MetadataDefinition {

  implicit val repositoryFormat = Json.format[MetadataDefinition]

  /** Register default definitions that every instance should have **/
  def registerDefaultDefinitions(): Unit = {
    // add default definition
    Logger.debug("Adding core metadata vocabulary definitions to database")
    val metadataService: MetadataService = DI.injector.getInstance(classOf[MetadataService])
    val default = List(
      Json.parse("""{"label":"Alternative Title",
          "uri":"http://purl.org/dc/terms/alternative",
          "type":"string"}"""),
      Json.parse("""{"label":"Audience",
          "uri":"http://purl.org/dc/terms/audience",
          "type":"string"}"""),
      Json.parse("""{"label":"References",
          "uri":"http://purl.org/dc/terms/references",
          "type":"string"}"""),
      Json.parse("""{
          "label":"Date and Time",
          "uri":"http://purl.org/dc/terms/date",
          "type":"datetime"}"""),
        Json.parse("""{"label":"CSDMS Standard Name",
          "uri":"http://csdms.colorado.edu/wiki/CSN_Searchable_List",
          "type":"list",
          "definitions_url":"http://ecgs.ncsa.illinois.edu/gsis/CSN"}"""),
      Json.parse("""{"label":"Annotating CSV File",
          "uri":"https://clowder.ncsa.illinois.edu/metadata/terms/variable_annotation",
          "type":"annotation",
          "definitions_url":"http://ecgs.ncsa.illinois.edu/gsis/CSN"}"""),
        Json.parse("""{
          "label":"ODM2 Variable Name",
          "uri":"http://vocabulary.odm2.org/variablename",
          "type":"list",
          "definitions_url":"http://ecgs.ncsa.illinois.edu/gsis/sas/sn/odm2"}"""),
        Json.parse("""{
          "label":"SAS Variable Name",
          "uri":"http://ecgs.ncsa.illinois.edu/gsis/sas/vars",
          "type":"scientific_variable",
          "definitions_url":"http://ecgs.ncsa.illinois.edu/gsis/sas/vars/unit/udunits2",
          "query_parameter": "term"}"""),
        Json.parse("""{
          "label":"SAS Spatial Geocode",
          "uri":"http://ecgs.ncsa.illinois.edu/gsis/sas/geocode",
          "type":"listgeocode",
          "definitions_url":"http://ecgs.ncsa.illinois.edu/gsis/sas/geocode",
          "query_parameter": "loc"}"""),
        Json.parse("""{
          "label":"Unit",
          "uri":"http://ecgs.ncsa.illinois.edu/gsis/sas/unit/udunits2",
          "type":"list",
          "definitions_url":"http://ecgs.ncsa.illinois.edu/gsis/sas/unit/udunits2"}"""),
        Json.parse("""
          {"label":"Principal Investigator(s)",
            "uri":"http://sead-data.net/terms/PrincipalInvestigator",
            "type":"string"}"""),
        Json.parse("""
          {"label":"Funding Institution",
            "uri":"http://sead-data.net/terms/FundingInstitution",
            "type":"string"}"""),
        Json.parse("""
          {"label":"Grant Number",
            "uri":"http://sead-data.net/terms/GrantNumber",
            "type":"string"}"""),
        Json.parse("""
          {"label":"Related Publications",
            "uri":"http://sead-data.net/terms/RelatedPublications",
            "type":"string"}"""),
        Json.parse("""
          {"label":"Time Periods",
            "uri":"http://purl.org/dc/terms/PeriodOfTime",
            "type":"string"}"""),
        Json.parse("""
          {"label":"Primary/Initial Publication",
            "uri":"http://sead-data.net/terms/PrimaryPublication",
            "type":"string"}"""),
        Json.parse("""
          {"label":"GeoJSON",
            "uri":"http://geojson.org/geojson-spec.html",
            "type":"wkt"}"""),
        Json.parse("""
          {"label":"vega5-spec",
           "description": "Visualization specs for Vega v5 or Vega Lite v4",
           "uri":"https://vega.github.io/schema/",
           "type":"string"}""")
      )
    // Add the default definitions, do not update if they already exist.
    if(metadataService.getDefinitions().size == 0) {
      Logger.debug("Add default metadata definition.")
      default.map(d => metadataService.addDefinition(MetadataDefinition(json = d), update = false))
    }
  }
}
