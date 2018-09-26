package api

import play.api.libs.iteratee.Enumerator
import play.api.mvc.Controller
import play.api.Logger
import javax.inject.Inject
import services._


/**
 * Reporting and downloading of reports.
 */
class Reporting @Inject()(selections: SelectionService,
                          datasets: DatasetService,
                          files: FileService,
                          collections: CollectionService) extends Controller with ApiController {

  def fileMetrics() = ServerAdminAction { implicit request =>
    Logger.debug("Generating file metrics report")

    var contents: String = "type,id,name,views,downloads,last_viewed,last_downloaded\n"

    files.getMetrics(None).foreach(f => {
      contents += "file,"+f.id.toString+",\""+f.filename+"\","
      contents += f.stats.views.toString+","+f.stats.downloads.toString+","
      contents += "\""+f.stats.last_viewed.getOrElse("")+"\","
      contents += "\""+f.stats.last_downloaded.getOrElse("")+"\"\n"
    })

    Ok.chunked(Enumerator(contents.getBytes("UTF-8")).andThen(Enumerator.eof)).withHeaders(
      "Content-Type" -> "text/csv",
      "Content-Disposition" -> "attachment; filename=FileMetrics.csv"
    )
  }

  def datasetMetrics() = ServerAdminAction { implicit request =>
    Logger.debug("Generating dataset metrics report")

    var contents: String = "type,id,name,views,downloads,last_viewed,last_downloaded\n"

    datasets.getMetrics(None).foreach(ds => {
      contents += "dataset,"+ds.id.toString+",\""+ds.name+"\","
      contents += ds.stats.views.toString+","+ds.stats.downloads.toString+","
      contents += "\""+ds.stats.last_viewed.getOrElse("")+"\","
      contents += "\""+ds.stats.last_downloaded.getOrElse("")+"\"\n"
    })

    Ok.chunked(Enumerator(contents.getBytes("UTF-8")).andThen(Enumerator.eof)).withHeaders(
      "Content-Type" -> "text/csv",
      "Content-Disposition" -> "attachment; filename=DatasetMetrics.csv"
    )
  }

  def collectionMetrics() = ServerAdminAction { implicit request =>
    Logger.debug("Generating collection metrics report")

    var contents: String = "type,id,name,views,last_viewed\n"

    collections.getMetrics(None).foreach(c => {
      contents += "collection,"+c.id.toString+",\""+c.name+"\","
      contents += c.stats.views.toString+","
      contents += "\""+c.stats.last_viewed.getOrElse("")+"\"\n"
    })

    Ok.chunked(Enumerator(contents.getBytes("UTF-8")).andThen(Enumerator.eof)).withHeaders(
      "Content-Type" -> "text/csv",
      "Content-Disposition" -> "attachment; filename=CollectionMetrics.csv"
    )
  }

  def allMetrics() = ServerAdminAction { implicit request =>
    Logger.debug("Generating all metrics report")

    var contents: String = "type,id,name,views,downloads,last_viewed,last_downloaded\n"

    collections.getMetrics(None).foreach(c => {
      contents += "collection,"+c.id.toString+",\""+c.name+"\","
      contents += c.stats.views.toString+",0,"
      contents += "\""+c.stats.last_viewed.getOrElse("")+"\",\n"
    })
    datasets.getMetrics(None).foreach(ds => {
      contents += "dataset,"+ds.id.toString+",\""+ds.name+"\","
      contents += ds.stats.views.toString+","+ds.stats.downloads.toString+","
      contents += "\""+ds.stats.last_viewed.getOrElse("")+"\","
      contents += "\""+ds.stats.last_downloaded.getOrElse("")+"\"\n"
    })
    files.getMetrics(None).foreach(f => {
      contents += "file,"+f.id.toString+",\""+f.filename+"\","
      contents += f.stats.views.toString+","+f.stats.downloads.toString+","
      contents += "\""+f.stats.last_viewed.getOrElse("")+"\","
      contents += "\""+f.stats.last_downloaded.getOrElse("")+"\"\n"
    })

    Ok.chunked(Enumerator(contents.getBytes("UTF-8")).andThen(Enumerator.eof)).withHeaders(
      "Content-Type" -> "text/csv",
      "Content-Disposition" -> "attachment; filename=AllMetrics.csv"
    )
  }
}
