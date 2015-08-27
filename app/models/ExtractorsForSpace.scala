package models

/**
 * Information about extractors assigned to a space. *
 * @author Inna Zharnitsky
 *
 */
case class ExtractorsForSpace(
    spaceId:String,
    extractors:List[String]     
 )
 