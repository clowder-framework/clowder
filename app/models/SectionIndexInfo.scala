package models

/**
 * Information about index containing sections. Index can have a name and a type.
 *
 *
 */
case class SectionIndexInfo(
    indexId:String, 
    indexName:Option[String] = None,
    indexType: Option[String] = None
 )