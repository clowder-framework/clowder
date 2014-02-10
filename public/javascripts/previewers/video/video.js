(function ($, Configuration) {
  console.log("Starting video previewer for " + Configuration.id);
  
  var height = 400;  
  var width = 750;
  
  $(Configuration.tab).append("<p>For panoramic images viewed through Quicktime, Shift can be used to zoom in and Ctrl to zoom out.</p>");
    
  console.log("Updating tab " + Configuration.tab);
  $(Configuration.tab).append(s);  
  $(Configuration.tab).append(			  
     "<video width='600px' id='ourvideo' controls><source src='" + Configuration.url + "'></source></video>"
  );

}(jQuery, Configuration));