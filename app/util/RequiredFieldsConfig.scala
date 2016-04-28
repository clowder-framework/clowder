package util

import play.api.Play._

/**
 * Utility to load default values for required fields from the application configuration file.
 *
 */
object RequiredFieldsConfig {
    
    /**
     * Value for if name fields should be required or not.
     * 
     * @return A boolean, true if name should be required. False otherwise. Default is true.
     */
    def isNameRequired(): Boolean = {
        configuration.getBoolean("medici2.requiredfields.isNameRequired").getOrElse(true)
    }
    
    /**
     * Value for if description fields should be required or not.
     * 
     * @return A boolean, true if description should be required. False otherwise. Default is false.
     * 
     */
    def isDescriptionRequired(): Boolean = {
        configuration.getBoolean("medici2.requiredfields.isDescriptionRequired").getOrElse(false)        
    }

    
}