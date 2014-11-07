package models

/**
 * case class to handle specific license information. Currently attached to individual Datasets and Files.  
 */
case class LicenseData (
        id: UUID = UUID.generate(),
        m_licenseType: String = "license1",
        m_licenseUrl: String = "",
        m_licenseText: String = "All Rights Reserved",
        m_rightsHolder: String = "", 
        m_ccAllowCommercial: Boolean = false,
        m_ccAllowDerivative: Boolean = false,
        m_ccRequireShareAlike: Boolean = false,
        m_allowDownload: Boolean = false               
) {
    /**
     * Utility method to check if the license allows the file to be downloaded. Currently, if the license type is NOT
     * "license1", or if it is "license1" and the "allowDownload" flag is set, the file can be downloaded.
     * 
     * @return A boolean, true if the license type allows the file to be downloaded, false otherwise.
     * 
     */
    def isDownloadAllowed = {
        (m_licenseType != "license1") || m_allowDownload
    }
    
    /**
     * Utility method to check if a name matches the rights holder of the license.
     * 
     * @param aName A String that represents the name of a user to compare to the current rights holder.
     * 
     * @return A boolean, true if the parameter matches the owner, false otherwise.
     * 
     */
    def isRightsOwner(aName: String) = {
        m_rightsHolder == aName
    }
}

