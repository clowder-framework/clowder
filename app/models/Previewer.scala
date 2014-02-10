/**
 *
 */
package models
import org.bson.types.ObjectId
import java.util.Date

/**
 * Previewers.
 * 
 * @author Luigi Marini
 *
 */
case class Previewer(
    id: String, 
    path: String,
    main: String,
    contentType: List[String]
)