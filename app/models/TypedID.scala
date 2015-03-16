package models


/**
 * Gives access to the type of a particular UUID
 *
 * @author Varun Kethineedi
 */
case class TypedID(
  id: UUID,
  objectType: String
  )
