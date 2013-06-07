(function ($, Configuration) {
  console.log("X3D previewer for " + Configuration.id);
  
  var fileUrl = "http://" + Configuration.hostIp + ":" + window.location.port + Configuration.url;
  var pathJs = "http://" + Configuration.hostIp + ":" + window.location.port + Configuration.jsPath + "/";
  
  var width = 750;
  var height = 550;
  
  $(Configuration.tab).append("<table><tr><td>Left mouse button drag</td><td>&nbsp;&nbsp;&nbsp;&nbsp;Rotate</td></tr>"
		  					+ "<tr><td>Left mouse button + Ctrl drag</td><td>&nbsp;&nbsp;&nbsp;&nbsp;Pan</td></tr>"
		  					+ "<tr><td>Right mouse button drag / Left mouse button + Alt drag</td><td>&nbsp;&nbsp;&nbsp;&nbsp;Zoom</td></tr>"
		  					+ "<tr><td>m button</td><td>&nbsp;&nbsp;&nbsp;&nbsp;Change rendering (regular-vertices-wireframe)</td></tr>"
		  					+ "<tr><td>Space</td><td>&nbsp;&nbsp;&nbsp;&nbsp;Display-hide model statistics</td></tr>"
		  					+ "<tr><td></td><td>&nbsp;&nbsp;&nbsp;&nbsp;</td></tr>"
		  					+ "</table>");
  
  var inner = "<x3d id='x3dElement" + Configuration.tab.replace("#previewer","") + "' showStat='false' showLog='true' height='" + height + "px' width='" + width + "px' style='width:" + width + ";height:" + height + ";' x='0px' y='0px'>";  
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
  //$(Configuration.tab).append("<a href='#' onClick=\"document.getElementById('x3dElement_1').runtime.showAll();return false;\">Show all</a>");
    
  var s = document.createElement("script");
  s.type = "text/javascript";
  s.src = pathJs + "x3dom.js";
  console.log("Updating tab " + Configuration.tab);
  $(Configuration.tab).append(s);
  
 
//  var e = document.getElementById('x3dElement' + Configuration.tab.replace("#previewer",""));
//  alert(x3dom.Runtime.prototype.canvas);
//  x3dom.Viewarea.prototype.showAll("posX");
  
}(jQuery, Configuration));