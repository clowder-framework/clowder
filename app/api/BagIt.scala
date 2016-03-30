package api

import javax.inject.Inject

import models.{Dataset, File}
import services.{UserService, FileService, DatasetService}

import scala.util.Try

/**
  * Created by todd_n on 3/29/16.
  */
class BagIt @Inject()(userService : UserService, files : FileService, datasets : DatasetService) extends ApiController {


  def bagDataset(dataset : Dataset) = Try {

  }

  def bagFile(file : File) = Try {


  }

}
