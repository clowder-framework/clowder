package Iterators

import java.io.{ByteArrayInputStream, InputStream}
import java.util.zip.{ZipEntry, ZipOutputStream}
import play.api.libs.json.{JsValue, Json}


object IteratorUtils {

  /**
   * Add a .json file containing information about resource.
   * The file in zip will be at: zipPath/outFile.json with contents jsonData.
   *
   * @param zipPath - where in zip file the info file will be written (i.e. pathToFolder)
   * @param outFile - name of output file before .json (e.g. "MyDataset_info")
   * @param jsonData - contents to include
   */
  def addJsonFileToZip(zip: ZipOutputStream, zipPath: String, outFile: String, jsonData: JsValue): Option[InputStream] = {
    val path = if (outFile.endsWith(".json"))
      zipPath + "/" + outFile
    else
      zipPath + "/" + outFile + ".json"

    zip.putNextEntry(new ZipEntry(path))
    Some(new ByteArrayInputStream(
      Json.prettyPrint(jsonData).getBytes("UTF-8")
    ))
  }
}
