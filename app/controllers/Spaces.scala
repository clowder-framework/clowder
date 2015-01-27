package controllers

import javax.inject.Inject

import services.SpaceService

/**
 * Spaces allow users to partition the data into realms only accessible to users with the right permissions.
 *
 * @author Luigi Marini
 *
 */
class Spaces @Inject()(spaces: SpaceService) {

}
