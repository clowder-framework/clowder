package jsonutils;

import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import org.json.simple.parser.ContainerFactory;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;


public class JsonUtil {

	public static LinkedHashMap parseJSON(String JSONString){
		
		JSONParser parser = new JSONParser();
		 ContainerFactory containerFactory = new ContainerFactory(){
			    public List creatArrayContainer() {
			      return new LinkedList();
			    }
			    public Map createObjectContainer() {
			      return new LinkedHashMap();
			    }			                        
			  };
    	  
			  try{
				  LinkedHashMap json = (LinkedHashMap)parser.parse(JSONString, containerFactory);
				  return json;
				  }
				  catch(ParseException pe){
				    System.out.println(pe);
				  }	  
		
		return null;
	}
	
}
