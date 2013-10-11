package fileutils;

import java.io.File;
import java.io.IOException;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import play.Logger;

public class FilesUtils {

	public static String getMainFileTypeOfZipFile(File compressedFile, String filename, String containerType){
		
		String mainFileType = "application/zip";
		
		try {
			if(filename.startsWith("MEDICI2DATASET_") && containerType.equals("dataset"))
				return "multi/files-zipped";
			
			ZipFile zipFile = new ZipFile(compressedFile);			
			Enumeration zipEntries = zipFile.entries();
			
            while (zipEntries.hasMoreElements()) {                            	
                String fileName = ((ZipEntry)zipEntries.nextElement()).getName();
                if(fileName.endsWith(".x3d")){
                	zipFile.close();
                	mainFileType = "model/x3d-zipped";
                	return mainFileType;
                }
                if(fileName.endsWith(".obj")){
                	zipFile.close();
                	mainFileType = "model/obj-zipped";
                	return mainFileType;
                }
                if(fileName.endsWith(".sfmdataset")){
                	zipFile.close();
                	mainFileType = "model/sfm-zipped";
                	return mainFileType;
                } 
            }
            zipFile.close();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			return ("ERROR: " + e.getMessage());
		}		
		return mainFileType;
	}	
}
