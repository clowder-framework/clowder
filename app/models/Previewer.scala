/**
 *
 */
package models

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