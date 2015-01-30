package models

/**
 * Web page URL and (file URLs in that specific web page, UUIDs for those file saved in database)
 * This is used for DTS service
 * @author Smruti Padhy
 * 
 */
case class WebPageResource(
    id: UUID,
    webPageURL:String,
    URLs: Map[String,String]
)