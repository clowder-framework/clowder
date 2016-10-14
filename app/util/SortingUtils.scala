package util

import models._

object SortingUtils {

  def sortCollections(cList: List[Collection], sortOrder: String): List[Collection] = {
    sortOrder match {
      case "dateN" => cList.sortWith((left, right) => left.created.compareTo(right.created) > 0)
      case "dateO" => cList.sortWith((left, right) => left.created.compareTo(right.created) > 0).reverse
      case "titleA" => cList.sortBy(_.name)
      case "titleZ" => cList.sortBy(_.name).reverse
      case "sizeL" => cList.sortBy(c => { c.datasetCount + c.childCollectionsCount }).reverse
      case "sizeS" => cList.sortBy(c => { c.datasetCount + c.childCollectionsCount })
    }
  }

  def sortDatasets(dList: List[Dataset], sortOrder: String): List[Dataset] = {
    sortOrder match {
      case "dateN" => dList.sortWith((left, right) => left.created.compareTo(right.created) > 0)
      case "dateO" => dList.sortWith((left, right) => left.created.compareTo(right.created) > 0).reverse
      case "titleA" => dList.sortBy(_.name)
      case "titleZ" => dList.sortBy(_.name).reverse
      case "sizeL" => dList.sortBy(d => { d.files.length + d.folders.length }).reverse
      case "sizeS" => dList.sortBy(d => { d.files.length + d.folders.length })
    }
  }

  def sortFolders(fList: List[Folder], sortOrder: String): List[Folder] = {
    sortOrder match {
      case "dateN" => fList.sortWith((left, right) => left.created.compareTo(right.created) > 0)
      case "dateO" => fList.sortWith((left, right) => left.created.compareTo(right.created) > 0).reverse
      case "titleA" => fList.sortBy(_.displayName)
      case "titleZ" => fList.sortBy(_.displayName).reverse
      case "sizeL" => fList.sortBy(l => { l.folders.length + l.files.length }).reverse
      case "sizeS" => fList.sortBy(l => { l.folders.length + l.files.length })
    }
  }

  def sortFiles(fList: List[File], sortOrder: String): List[File] = {
    sortOrder match {
      case "dateN" => fList.sortWith((left, right) => left.uploadDate.compareTo(right.uploadDate) > 0)
      case "dateO" => fList.sortWith((left, right) => left.uploadDate.compareTo(right.uploadDate) > 0).reverse
      case "titleA" => fList.sortBy(_.filename)
      case "titleZ" => fList.sortBy(_.filename).reverse
      case "sizeL" => fList.sortBy(_.length).reverse
      case "sizeS" => fList.sortBy(_.length)
    }
  }

}
