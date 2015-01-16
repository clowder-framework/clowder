(function ($, Configuration) {
  console.log("video previewer for " + Configuration.id);
  
  var s = document.createElement("script");
  s.type = "text/javascript";
  s.src = window.location.protocol + "//popcornjs.org/code/dist/popcorn-complete.min.js";
    
  console.log("Updating tab " + Configuration.tab);
  $(Configuration.tab).append(s);
  $(Configuration.tab).append("<br/>");
  $(Configuration.tab).append(			  
     "<video width='750px' id='ourvideo' controls><source src='" + Configuration.url + "'></source></video>"
  );
    
}(jQuery, Configuration));