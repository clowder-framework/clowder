package models

/** Small class that contains a set of Long counts for various types of data in the database **/
case class DBCounts(numDatasets: Long=0, numFiles: Long=0, numBytes: Long=0, numCollections: Long=0,
                    numSpaces: Long=0, numUsers: Long=0) {

  def getMap(): Map[String, Long] = {
    Map(
      "datasets"->numDatasets,
      "files"->numFiles,
      "bytes"->numBytes,
      "collections"->numCollections,
      "spaces"->numSpaces,
      "users"->numUsers
    )
  }
}