package models

import java.util.Date

/**
 * Information about index containing sections. Index can have a name and a type.
 *
 * @author Inna Zharnitsky
 *
 */
case class SectionIndexInfo(
    indexId:String, 
    indexName:Option[String] = None,
    indexType: Option[String] = None
 )