package util

import play.api.Play._

/**
 * Utility to load default values for spaces from the application configuration file.
 *
 */
object SpaceConfig {
    
    /**
     * Value for if purging should be enabled or not.
     * 
     * @return A boolean, true if resources should expire. False otherwise.
     */
    def getIsTimeToLiveEnabled(): Boolean = {
        configuration.getBoolean("medici2.space.isTimeToLiveEnabled").getOrElse(false)
    }
    
    /**
     * The amount of time before resources should expire.
     * 
     * @return The value of the amount, in milliseconds.
     * 
     */
    def getTimeToLive(): Long = {
        val timeString = configuration.getInt("medici2.space.timeToLive").getOrElse(720)
        (timeString * 60 * 60 * 1000L)
    }
}