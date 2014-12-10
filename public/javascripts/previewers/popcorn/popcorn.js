(function ($, Configuration) {
  console.log("video previewer for " + Configuration.id);
  
  var s = document.createElement("script");
  s.type = "text/javascript";
  s.src = "http://popcornjs.org/code/dist/popcorn-complete.min.js";
    
  console.log("Updating tab " + Configuration.tab);
  $(Configuration.tab).append(s);  
  $(Configuration.tab).append("<br/>");
  $(Configuration.tab).append(			  
     "<video width='600px' id='ourvideo' controls><source src='" + Configuration.url + "'></source></video>"
  );
  
//  var height = 400;  
//  var width = 750;
//  
//  $(Configuration.tab).append("<p>For panoramic images viewed through Quicktime, Shift can be used to zoom in and Ctrl to zoom out.</p>");
//    
//  console.log("Updating tab " + Configuration.tab);
//  $(Configuration.tab).append(s);  
//  $(Configuration.tab).append(			  
//     "<video width='600px' id='ourvideo' controls><source src='" + Configuration.url + "'></source></video>"
//  );
  
  
}(jQuery, Configuration));