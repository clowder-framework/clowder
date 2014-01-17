package api

import services.{QueryService, DatasetService, FileService, ElasticsearchPlugin}
import play.api.Play.current
import play.Logger
import scala.collection.mutable.ListBuffer
import scala.collection.JavaConversions.mapAsScalaMap
import scala.util.parsing.json.JSONArray
import play.api.libs.json.JsValue
import play.api.libs.json.Json
import play.api.libs.json.Json.toJson
import javax.inject.{Inject, Singleton}


@Singleton
class Search @Inject() (files: FileService, datasets: DatasetService)  extends ApiController {
  /**
   * Search results.
   */
  def search(query: String) = SecuredAction(parse.anyContent, authorization = WithPermission(Permission.SearchDatasets)) { implicit request =>
    current.plugin[ElasticsearchPlugin] match {
      case Some(plugin) => {
        Logger.debug("Searching for: " + query)
        var filesFound = ListBuffer.empty[models.File]
        var datasetsFound = ListBuffer.empty[models.Dataset]
        var mapdatasetIds = new scala.collection.mutable.HashMap[String, (String, String)]
        if (query != "") {
          import play.api.Play.current
          
          val result = current.plugin[ElasticsearchPlugin].map { _.search("data", query) }
          
          result match {
            case Some(searchResponse) => {
              for (hit <- searchResponse.getHits().getHits()) {
                Logger.debug("Computing search result " + hit.getId())
                Logger.info("Fields: ")
                for ((key, value) <- mapAsScalaMap(hit.getFields())) {
                  Logger.info(value.getName + " = " + value.getValue())
                }
                if (hit.getType() == "file") {
                  files.getFile(hit.getId()) match {
                    case Some(file) => {
                      Logger.debug("FILES:hits.hits._id: Search result found file " + hit.getId());
                      Logger.debug("FILES:hits.hits._source: Search result found dataset " + hit.getSource().get("datasetId"))
                      //Logger.debug("Search result found file " + hit.getId()); files += file
                      mapdatasetIds.put(hit.getId(), (hit.getSource().get("datasetId").toString(), hit.getSource.get("datasetName").toString))
                      filesFound += file
                    }
                    case None => Logger.debug("File not found " + hit.getId())
                  }
                } else if (hit.getType() == "dataset") {
                  Logger.debug("DATASETS:hits.hits._source: Search result found dataset " + hit.getSource().get("name"))
                  Logger.debug("DATASETS:Dataset.id=" + hit.getId());
                  //Dataset.findOneById(new ObjectId(hit.getId())) match {
                  datasets.get(hit.getId()) match {
                    case Some(dataset) =>
                      Logger.debug("Search result found dataset" + hit.getId()); datasetsFound += dataset
                    case None => {
                      Logger.debug("Dataset not found " + hit.getId())
                      //Redirect(routes.Datasets.dataset(hit.getId))
                    }
                  }
                }
              }
            }
            case None => {
              Logger.debug("Search returned no results")
            }
          }
        }
        
        val filesJson = toJson(for(currFile <- filesFound.toList) yield {
          currFile.id.toString
        } )
        val datasetsJson = toJson(for(currDataset <- datasetsFound.toList) yield {
          currDataset.id.toString
        } )
        
        val fullJSON = toJson(Map[String,JsValue]("files" -> filesJson, "datasets" -> datasetsJson))
        
        Ok(fullJSON)
      }
      case None => {
        Logger.debug("Search plugin not enabled")
        Ok(views.html.pluginNotEnabled("Text search"))
      }
    }

  }
  
  
}