(function ($, Configuration) {
  console.log("Quicktime previewer for " + Configuration.id);
  
  console.log("Updating tab " + Configuration.tab);
  
  var height = 400;  
  var width = 750;
  
  $(Configuration.tab).append("<p>For panoramic images viewed through Quicktime, Shift can be used to zoom in and Ctrl to zoom out.</p>");
  
  $(Configuration.tab).append(
		  "<object classid='clsid:02BF25D5-8C17-4B23-BC80-D3488ABDDC6B' height='"+ height +"' width='"+ width +"'>"+
		  "<param name='src' value='" + "http://"+Configuration.hostIp+":"+window.location.port+ Configuration.url   +"' />"+
		  "<param name='autoplay' value='true'>"+
		  "<param name='controller' value='true'>"+
		  "<param name='scale' value='tofit'>"+
		  "<embed type='video/quicktime' alt='No video plugin capable of playing mov videos was found.' src='" + "http://"+Configuration.hostIp+":"+window.location.port+ Configuration.url   +"' height='"+ height +"' width='"+ width +"' autoplay='true' controller='true' scale='tofit'></embed>"+
		  "</object>"		  
  );

  if(navigator.appVersion.indexOf("X11")!=-1 || navigator.appVersion.indexOf("Linux")!=-1)
	  $(Configuration.tab).append("<br/><p>Note: Quicktime VR panoramas (QTVR) are not viewed properly on Unix-based browsers due to lack of format support." +
	  							" If the file is a QTVR, you can download it and view it on your desktop.</p>");
  
}(jQuery, Configuration));