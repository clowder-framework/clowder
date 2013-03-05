(function ($, Configuration) {
  console.log("Some library is running on " + Configuration.id);
  
  $("#previewer_Thumbnail").append(
     "<img src='" + Configuration.url + "' width='400px'></img>"
  );
  
}(jQuery, Configuration));