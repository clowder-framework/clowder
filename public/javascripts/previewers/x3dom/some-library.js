(function ($, Configuration) {
  console.log("X3D previewer for " + Configuration.id);
  
  var fileUrl = "http://" + Configuration.hostIp + ":" + window.location.port + Configuration.url;
  var pathJs = "http://" + Configuration.hostIp + ":" + window.location.port + Configuration.jsPath + "/";
  
  var width = 750;
  var height = 550;
  
  $(Configuration.tab).append("<table><tr><td>Left Button / Left Button + Shift</td><td>&nbsp;&nbsp;&nbsp;&nbsp;Rotate</td></tr>"
		  					+ "<tr><td>Mid Button / Left Button + Ctl</td><td>&nbsp;&nbsp;&nbsp;&nbsp;Pan</td></tr>"
		  					+ "<tr><td>Right Button / Wheel / Left Button + Alt</td><td>&nbsp;&nbsp;&nbsp;&nbsp;Zoom</td></tr></table>"); 
  
  var inner = "<x3d id='x3dElement" + Configuration.tab.replace("#previewer","") + "' showStat='false' showLog='false' height='" + height + "px' width='" + width + "px' style='width:" + width + ";height:" + height + ";'>";  
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