package models

import api.Permission

import play.api.libs.json._


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
    def isDownloadAllowed(user: Option[User]) = {
      (m_licenseType != "license1") || m_allowDownload || Permission.checkPermission(user, Permission.DownloadFiles) || (user match {
        case Some(x) => isRightsOwner(x.fullName)
        case None => false
      })
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

    /**
     * Utility to return a url even if empty, but enough other attributes available to determine it
     * this is repurposed from:
     * function updateData(id, imageBase, sourceObject, authorName)  
     * in updateLicenseInfo.js line:88
     */
    def urlViaAttributes() : String = {
       if (m_licenseUrl != "") return m_licenseUrl
       var licenseUrl = m_licenseUrl;
       if (m_licenseType == "license2") {
         //No checkboxes selected
         if (!m_ccAllowCommercial && !m_ccAllowDerivative && !m_ccRequireShareAlike) {
            licenseUrl = "http://creativecommons.org/licenses/by-nc-nd/3.0/";
         }
         //Only commercial selected
         else if (m_ccAllowCommercial && !m_ccAllowDerivative && !m_ccRequireShareAlike) {
            licenseUrl = "http://creativecommons.org/licenses/by-nd/3.0/";
         }
         //Only remixing selected
         else if (!m_ccAllowCommercial && m_ccAllowDerivative && !m_ccRequireShareAlike) {
            licenseUrl = "http://creativecommons.org/licenses/by-nc/3.0/";
         }
         //Remixing and Sharealike selected
         else if (!m_ccAllowCommercial && m_ccAllowDerivative && m_ccRequireShareAlike) {
            licenseUrl = "http://creativecommons.org/licenses/by-nc-sa/3.0/";
         }
         //All checkboxes selected
         else if (m_ccAllowCommercial && m_ccAllowDerivative && m_ccRequireShareAlike) {
            licenseUrl = "http://creativecommons.org/licenses/by-sa/3.0/";
         }
         //Commercial and Remixing selected
         else if (m_ccAllowCommercial && m_ccAllowDerivative && !m_ccRequireShareAlike) {
            licenseUrl = "http://creativecommons.org/licenses/by/3.0/";
         }
         //else { rightsHolder = 'Creative Commons';
         //   licenseText = 'Specific level info'; }
      }
      else if (m_licenseType == "license3") {
         licenseUrl = "http://creativecommons.org/publicdomain/zero/1.0/";
      }
      else {
         licenseUrl = "https://dbpedia.org/page/All_rights_reserved";
      }
       //m_licenseType = licenseUrl; //would only reset if not "" to start 
      return licenseUrl
   }
        
   /**
    * Utility function, similar to a json Write, to return string version in json-ld  format
    * Should also return key
    */
    def to_jsonld () : String = {
       //return m_licenseUrl
       //return this.urlViaAttributes()
       val licURI = this.urlViaAttributes()
       val licURL = if (licURI != "https://dbpedia.org/page/All_rights_reserved") licURI
                    else ""
       val licLD = JsObject(Seq(
                "@id" -> JsString(licURI),
                "URL" -> JsString(licURL),
                "@type" -> JsString("license"),
                "Text" -> JsString(m_licenseText)  //added this DataType
                ))
       return Json.stringify(licLD)
    }
}
