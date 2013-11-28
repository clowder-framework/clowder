(function ($, Configuration) {
  console.log("video previewer for " + Configuration.id);
  
//  var s = document.createElement("script");
//  s.type = "text/javascript";
//  s.src = "http://popcornjs.org/code/dist/popcorn-complete.min.js";
//    
//  console.log("Updating tab " + Configuration.tab);
//  $(Configuration.tab).append(s);  
//  $(Configuration.tab).append(			  
//     "<video width='600px' id='ourvideo' controls><source src='" + Configuration.url + "'></source></video>"
//  );
  
  var height = 400;  
  var width = 750;
  
  $(Configuration.tab).append("<p>For panoramic images viewed through Quicktime, Shift can be used to zoom in and Ctrl to zoom out.</p>");
  
  $(Configuration.tab).append(
		  "<object classid='clsid:02BF25D5-8C17-4B23-BC80-D3488ABDDC6B' height='"+ height +"' width='"+ width +"'>"+
		  "<param name='src' value='" + "http://"+Configuration.hostIp+":"+window.location.port+ Configuration.url   +"' />"+
		  "<param name='autoplay' value='false'>"+
		  "<param name='controller' value='true'>"+
		  "<param name='scale' value='tofit'>"+
		  "<embed type='video/quicktime' alt='No video plugin capable of playing videos was found.' src='" + "http://"+Configuration.hostIp+":"+window.location.port+ Configuration.url   +"' height='"+ height +"' width='"+ width +"' autoplay='false' controller='true' scale='tofit'></embed>"+
		  "</object>"		  
  );

  
}(jQuery, Configuration));