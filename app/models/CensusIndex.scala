package models

import java.util.Date

/**
 * Information about census index id, index can have a name and a type.
 *
 * @author Inna Zharnitsky
 *
 */
case class CensusIndex(
  
    //when indexId is UUID, keep getting Boxed Error
	//indexId:UUID, 
     indexId:String, 
    indexName:Option[String] = None,
    indexType: Option[String] = None
 )