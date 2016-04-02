package api

import java.nio.file.{Path, Files}
import javax.inject.Inject

import models.{UUID, Dataset, File}
import play.api.mvc.Action
import services.{UserService, FileService, DatasetService}

import scala.util.Try

/**
  * Created by todd_n on 3/29/16.
  */
class BagIt @Inject()(userService : UserService, files : FileService, datasets : DatasetService) extends ApiController {


  def bagDataset(dataset : Dataset) = Action {
    val temp = Files.createTempDirectory(null)
    temp.toFile().deleteOnExit()
    temp.toFile.delete()
    Ok("not implemented")
  }

  def bagFile(id : UUID) = Action {
    val tmp = System.getProperty("java.io.tmpdir")
    val temp = Files.createTempDirectory(null);
    temp.toFile().deleteOnExit()
    temp.toFile().delete()
    Ok("not implemented")

  }

}
