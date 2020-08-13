package Iterators

import java.io.InputStream
import java.security.{DigestInputStream, MessageDigest}
import java.util.zip.ZipOutputStream
import scala.collection.mutable.HashMap

import models.User


class BagItIterator(pathToFolder: String, zip: ZipOutputStream, senderId: String, description: String,
                    md5Bag : HashMap[String, MessageDigest], md5Files : HashMap[String, MessageDigest],
                    user: Option[User]) extends Iterator[Option[InputStream]] {

  var file_type = "bag-info.txt"
  var bytes = 0L
  var dis: Option[DigestInputStream] = None


  def setBytes(totalBytes : Long) = {
    bytes = totalBytes
  }

  def hasNext(): Boolean = {
    file_type != "none"
  }

  def next(): Option[DigestInputStream] = {
    file_type match {
      case "bag-info.txt" => {
        val is = IteratorUtils.addTextFileToZip(zip, pathToFolder, file_type,
          IteratorUtils.getBagInfoTxt)
        dis = IteratorUtils.addMD5Entry(file_type, is, md5Bag)
        file_type = "bagit.txt"
      }
      case "bagit.txt" => {
        val is = IteratorUtils.addTextFileToZip(zip, pathToFolder, file_type,
          IteratorUtils.getBagItTxtHeader(bytes, 0, senderId, description, user))
        dis = IteratorUtils.addMD5Entry(file_type, is, md5Bag)
        file_type = "manifest-md5.txt"
      }
      case "manifest-md5.txt" => {
        val is = IteratorUtils.addTextFileToZip(zip, pathToFolder, file_type,
          IteratorUtils.generateBagMD5String(md5Bag))
        dis = IteratorUtils.addMD5Entry(file_type, is, md5Files)
        file_type = "tagmanifest-md5.txt"
      }
      case "tagmanifest-md5.txt" => {
        val is = IteratorUtils.addTextFileToZip(zip, pathToFolder, file_type,
          IteratorUtils.generateBagMD5String(md5Bag))
        dis = IteratorUtils.addMD5Entry(file_type, is, md5Bag)
        file_type = "done"
      }
      case _ => None
    }
    dis
  }
}
