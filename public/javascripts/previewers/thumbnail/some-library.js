(function ($, Configuration) {
  console.log("image previewer for " + Configuration.id);
  
  console.log("Updating tab " + Configuration.tab);
  $(Configuration.tab).append(
     "<img src='" + Configuration.url + "' width='400px'></img>"
  );
  console.log($(Configuration.tab))
  
}(jQuery, Configuration));