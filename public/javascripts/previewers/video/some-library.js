(function ($, Configuration) {
  console.log("video previewer for " + Configuration.id);
  
  var s = document.createElement("script");
  s.type = "text/javascript";
  s.src = "http://popcornjs.org/code/dist/popcorn-complete.min.js";
    
  console.log("Updating tab " + Configuration.tab);
  $(Configuration.tab).append(s);  
  $(Configuration.tab).append(			  
     "<video width='600px' id='ourvideo' controls><source src='" + Configuration.url + "'></source></video>"
  );
  
  $(Configuration.tab).append("<br/><p>Note: Quicktime VR panoramas (QTVR) cannot be viewed using this previewer. You can use the \"Quicktime\"" +
								" previewer to interact with them (if on Windows or Mac) or download the QTVR and view it on your desktop.</p>");
     
}(jQuery, Configuration));