package util;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

import play.Logger;

public class StreamGobblerReturnsNotUploaded extends StreamGobbler {

	private boolean wasSuccessful = false;
	
	public boolean wasSuccessful(){
		return wasSuccessful;
	}
    
    public StreamGobblerReturnsNotUploaded(InputStream is, String type)
    {
    	super(is,type);

    }
    
    public void run()
    {
        try
        {
            InputStreamReader isr = new InputStreamReader(is);
            BufferedReader br = new BufferedReader(isr);
            String line=null;
            if(type.equals("INFO"))
            	while ( (line = br.readLine()) != null){
            		Logger.info(line);
            		if(line.trim().startsWith("20"))
            			wasSuccessful = true;
            	}
            else if(type.equals("ERROR"))
            	while ( (line = br.readLine()) != null){
            		Logger.error(line);
            		if(line.trim().startsWith("20"))
            			wasSuccessful = true;
            	}
            } catch (IOException ioe)
              {
                ioe.printStackTrace();  
              }
    }
	
}
