package models

import org.bson.types.ObjectId


/**
 * Selected items.
 */
case class Selected (
    id: ObjectId = new ObjectId,
    user: String,
    datasets: List[String] = List.empty,
    files: List[String] = List.empty
    )
    
