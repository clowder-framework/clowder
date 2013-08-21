function processData(file){
	$(Configuration.tab).append("success");
}

function getZipEntries(entries) {
    if (entries.length) {
	entries[0].getData(new zip.TextWriter(), processData);
    }
}

function zipReadCallback (reader) {
  reader.getEntries(getZipEntries);
  reader.close();
}

function zipReadError (reader) {
	  console.log("Error occurred reading zip file");
}

(function ($, Configuration) {

  console.log("ONI previewer for " + Configuration.id);
  
  var fileUrl = "http://" + Configuration.hostIp + ":" + window.location.port + Configuration.url;
  var pathJs = "http://" + Configuration.hostIp + ":" + window.location.port + Configuration.jsPath + "/";
  
  var width = 750;
  var height = 550;
  
  var prNum = Configuration.tab.replace("#previewer","");
  
  var zipData = new Object();
  $.ajax({
	    url: fileUrl,
	    async:false,
	    success: function (data) {
	    	//inner = inner + data;
		 zipData = data;
		$(Configuration.tab).append("<h1>Data Success</h1>");
	    	 },
	    dataType: 'text',
	    error: function (obj, status, error) {
		$(Configuration.tab).append(status + " " + error);
		}
	});

  var s = document.createElement("script");
  s.type = "text/javascript";
  s.src = pathJs + "zip.js";
  console.log("Updating tab " + Configuration.tab);
  $(Configuration.tab).append(s); 

  var s = document.createElement("script");
  s.type = "text/javascript";
  s.src = pathJs + "x3dom.js";
  console.log("Updating tab " + Configuration.tab);
  $(Configuration.tab).append(s);  

 zip.createReader(new zip.BlobReader(zipData), zipReadCallback, zipReadError);

}(jQuery, Configuration));
