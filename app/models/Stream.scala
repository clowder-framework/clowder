/**
 *
 */
package models
import org.bson.types.ObjectId

/**
 * A stream is a sequence of objects with potentially no beginning and no end.
 * @author Luigi Marini
 *
 */
case class Stream (
  id: ObjectId = new ObjectId
)