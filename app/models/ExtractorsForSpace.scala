package models

/**
 * Information about extractors assigned to a space.
 */
case class ExtractorsForSpace(
    spaceId:String,
    extractors:List[String]     
 )
 