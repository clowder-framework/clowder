(function ($, Configuration) {
  console.log("Quicktime previewer for " + Configuration.id);
  
  console.log("Updating tab " + Configuration.tab);
  
  var height = 400;  
  var width = 750;
  $(Configuration.tab).append(
		  "<object classid='clsid:02BF25D5-8C17-4B23-BC80-D3488ABDDC6B' height='"+ height +"' width='"+ width +"'>"+
		  "<param name='src' value='" + "http://"+Configuration.hostIp+":"+window.location.port+ Configuration.url   +"' />"+
		  "<param name='autoplay' value='true'>"+
		  "<param name='controller' value='true'>"+
		  "<param name='scale' value='tofit'>"+
		  "<embed src='" + "http://"+Configuration.hostIp+":"+window.location.port+ Configuration.url   +"' height='"+ height +"' width='"+ width +"' autoplay='true' controller='true' scale='tofit'></embed>"+
		  "</object>"		  
  );

  
  
}(jQuery, Configuration));