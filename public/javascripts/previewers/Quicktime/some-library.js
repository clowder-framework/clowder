(function ($, Configuration) {
  console.log("Quicktime previewer for " + Configuration.id);
  
  console.log("Updating tab " + Configuration.tab);
  //Thus, show the file as a single video.
  
  var height = 400;  
  var width = 750;
  
  var haveqt = false;

  if (navigator.plugins) {
      for (i=0; i < navigator.plugins.length; i++ ) {
          if (navigator.plugins[i].name.indexOf
          ("QuickTime") >= 0)
          { haveqt = true; }
      }
  }
  
  if(!haveqt) {
	  //No Quicktime 
	  $(Configuration.tab).append("<br/><p>Quicktime support not available: To preview Quicktime VR panoramas (QTVR), your browser must have the Quicktime plug-in." +
		" If you have an applications that can view Quicktime VR panoramas (QTVR), you can download the file and view it on your desktop.</p>");
  } else {
    $(Configuration.tab).append("<p>For panoramic images viewed through Quicktime, Shift can be used to zoom in and Ctrl to zoom out.</p>");
  
    $(Configuration.tab).append(
		  "<object classid='clsid:02BF25D5-8C17-4B23-BC80-D3488ABDDC6B' height='"+ height +"' width='"+ width +"'>"+
		  "<param name='src' value='" + Configuration.url   +"' />"+
		  "<param name='autoplay' value='false'>"+
		  "<param name='controller' value='true'>"+
		  "<param name='scale' value='tofit'>"+
		  "<embed type='video/quicktime' alt='No video/QTVR plugin capable of playing this video/displaying this QTVR was found.' src='" + Configuration.url   +"' height='"+ height +"' width='"+ width +"' autoplay='false' controller='true' scale='tofit'></embed>"+
		  "</object>"		  
    );

    if(navigator.appVersion.indexOf("X11")!=-1 || navigator.appVersion.indexOf("Linux")!=-1)
	  $(Configuration.tab).append("<br/><p>Note: Quicktime VR panoramas (QTVR) are not viewed properly on Unix-based browsers due to lack of format support." +
	  							" If the file is a QTVR, you can download it and view it on your desktop.</p>");
  }
}(jQuery, Configuration));
