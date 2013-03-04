(function ($, Configuration) {
  console.log("Some library is running on " + Configuration.id);
  
  var s = document.createElement("script");
  s.type = "text/javascript";
  s.src = "http://popcornjs.org/code/dist/popcorn-complete.min.js";
  
  $("#previewer_Video").append(s);
  
  
  $("#previewer_Video").append(
     "<video height='400' width='600' id='ourvideo' controls><source src='" + Configuration.url + "'></source></video>"
  );
  
}(jQuery, Configuration));