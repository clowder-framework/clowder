package services

import models.AppAppearance

trait AppAppearanceService {

  def getDefault(): Option[AppAppearance]
  
  def setDisplayedName(displayedName: String)
  
  def setWelcomeMessage(welcomeMessage: String)
  
}