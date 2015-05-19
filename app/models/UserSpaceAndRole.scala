package models

/**
 * Elements here are initialized to null since they should always be defined, and if they are not, 
 * then it is an exception case.
 */
case class UserSpaceAndRole (
        spaceId: UUID = null,            
        role: Role = null
)