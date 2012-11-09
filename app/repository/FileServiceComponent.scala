package repository
import java.io.File

/**
 * Client service to access file store.
 * 
 * @author Luigi Marini
 *
 */
trait FileServiceComponent {
  this: DiskFileRepositoryComponent => 
    
  val fileService: FileService
  
  class FileService {
    def save(file: File) = fileRepository.save(file)
    def get(id: String) = fileRepository.get(id)
  }
}