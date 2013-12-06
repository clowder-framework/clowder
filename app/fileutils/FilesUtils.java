package fileutils;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import play.Logger;

import org.json.JSONObject;
import org.json.XML;

import org.apache.commons.io.FileUtils;


public class FilesUtils {

	public static String getMainFileTypeOfZipFile(File compressedFile, String filename, String containerType){
		
		String mainFileType = "multi/files-zipped";
		
		try {
			if(filename.startsWith("MEDICI2DATASET_") && containerType.equals("dataset"))
				return "multi/files-zipped";
			
			ZipFile zipFile = new ZipFile(compressedFile);			
			Enumeration zipEntries = zipFile.entries();
			
            while (zipEntries.hasMoreElements()) {                            	
                String fileName = ((ZipEntry)zipEntries.nextElement()).getName();
                if(fileName.toLowerCase().endsWith(".x3d")){
                	zipFile.close();
                	mainFileType = "model/x3d-zipped";
                	return mainFileType;
                }
                if(fileName.toLowerCase().endsWith(".obj")){
                	zipFile.close();
                	mainFileType = "model/obj-zipped";
                	return mainFileType;
                }
                if(fileName.toLowerCase().endsWith(".sfmdataset")){
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
	
	public static String readXMLgetJSON(File xmlFile){
		
		try{
			FileInputStream fis = new FileInputStream(xmlFile);
			byte[] data = new byte[(int)xmlFile.length()];
			fis.read(data);
		    fis.close();
		    String theXML = new String(data, "UTF-8");
		    
		  //Remove spaces from XML tags
//		    int currStart = theXML.indexOf("<");
//		    int currEnd = -1;
//		    String xmlNoSpaces = "";		    
//		    while(currStart != -1){
//		      xmlNoSpaces = xmlNoSpaces + theXML.substring(currEnd+1,currStart);
//		      currEnd = theXML.indexOf(">", currStart+1);
//		      xmlNoSpaces = xmlNoSpaces + theXML.substring(currStart,currEnd+1).replaceAll(" ", "_");
//		      currStart = theXML.indexOf("<", currEnd+1);
//		    }
//		    xmlNoSpaces = xmlNoSpaces + theXML.substring(currEnd+1);
//		    theXML = xmlNoSpaces;
		    
		    Logger.debug("thexml: " + theXML);
		    
		    JSONObject xmlJSONObj = XML.toJSONObject(theXML);
		    
		    return xmlJSONObj.toString();
			
		}catch (Exception e) {
			// TODO Auto-generated catch block
			return ("ERROR: " + e.getMessage());
		}
	}
	
}