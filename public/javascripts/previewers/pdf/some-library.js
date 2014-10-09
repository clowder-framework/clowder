(function ($, Configuration) {
  console.log("PDF previewer for " + Configuration.id); 
  console.log("Updating tab " + Configuration.tab);
  
  var hostAddress = window.location.protocol + "//" + window.location.hostname + (window.location.port ? ':' + window.location.port: '')
  var fileUrl = hostAddress + Configuration.url;
  var pathJs = hostAddress + Configuration.jsPath + "/";
  
  var s = document.createElement("script");
  s.type = "text/javascript";
  s.src = pathJs + "pdfobject.js";
  $(Configuration.tab).append(s);

  $(Configuration.tab).append("<br/>");
  
  $(Configuration.tab).append(
	        "<div id='pdfview" + Configuration.tab.replace("#previewer","") + "' class='fit-in-space pdfview'>If you are seeing this, " +
	        "your browser does not support Adobe Reader or PDF.</div>");
  
  new PDFObject({ url: fileUrl }).embed("pdfview" + Configuration.tab.replace("#previewer",""));
  
  if((navigator.appVersion.indexOf("X11")!=-1 || navigator.appVersion.indexOf("Linux")!=-1) && navigator.userAgent.indexOf("hrome") != -1)
	  $(Configuration.tab).append("<br/><p>Note: 3D models embedded in PDF cannot be interacted with on Unix-based Google Chrome. You can use Firefox, or download the PDF" +
	  								" and view the model on your desktop.</p>");
    
}(jQuery, Configuration));