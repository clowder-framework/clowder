package controllers

import javax.inject.Inject

import api.{Permission, WithPermission}
import models.UUID
import play.api.Logger
import services.SpaceService

/**
 * Spaces allow users to partition the data into realms only accessible to users with the right permissions.
 *
 * @author Luigi Marini
 * @author Rob Kooper
 *
 */
class Spaces @Inject()(spaces: SpaceService) extends SecuredController {
}
