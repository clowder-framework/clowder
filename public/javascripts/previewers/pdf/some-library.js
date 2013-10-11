(function ($, Configuration) {
  console.log("PDF previewer for " + Configuration.id); 
  console.log("Updating tab " + Configuration.tab);
  
  var fileUrl = "http://" + Configuration.hostIp + ":" + window.location.port + Configuration.url;
  var pathJs = "http://" + Configuration.hostIp + ":" + window.location.port + Configuration.jsPath + "/";
  
  var s = document.createElement("script");
  s.type = "text/javascript";
  s.src = pathJs + "pdfobject.js";
  $(Configuration.tab).append(s);

  
  $(Configuration.tab).append(
	        "<div id='pdfview" + Configuration.tab.replace("#previewer","") + "' class='pdfview'>If you are seeing this, " +
	        "your browser does not support Adobe Reader or PDF.</div>");
  
  new PDFObject({ url: fileUrl }).embed("pdfview" + Configuration.tab.replace("#previewer",""));
    
}(jQuery, Configuration));