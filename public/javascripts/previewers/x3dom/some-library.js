(function ($, Configuration) {
  console.log("X3D previewer for " + Configuration.id);
  
  var fileUrl = "http://" + Configuration.hostIp + ":" + window.location.port + Configuration.url;
  var pathJs = "http://" + Configuration.hostIp + ":" + window.location.port + Configuration.jsPath + "/";
  
  var width = 750;
  var height = 550;
  
  var inner = "<x3d id='x3dElement' showStat='false' showLog='false' height='" + height + "px' width='" + width + "px' style='width:" + width + ";height:" + height + ";'>";  
  $.ajax({
	    url: fileUrl,
	    async:false,
	    success: function (data) {
	    	inner = inner + data;
	    	 },
	    dataType: 'text'
	});
  inner = inner + "</x3d>";
  $(Configuration.tab).append(inner);
    
  var s = document.createElement("script");
  s.type = "text/javascript";
  s.src = pathJs + "x3dom.js";
  console.log("Updating tab " + Configuration.tab);
  $(Configuration.tab).append(s);
  
}(jQuery, Configuration));