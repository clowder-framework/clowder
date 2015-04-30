package models

case class UserSpaceAndRole (
        m_spaceId: Option[UUID] = None,            
        m_role: Option[Role] = None
)