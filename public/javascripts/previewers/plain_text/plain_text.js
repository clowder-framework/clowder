(function ($, Configuration) {

  var fileUrl = "http://" + Configuration.hostIp + ":" + window.location.port + Configuration.url;
  var pathJs = "http://" + Configuration.hostIp + ":" + window.location.port + Configuration.jsPath + "/";
  
  var width = 750;
  var height = 550;
    
  var prNum = Configuration.tab.replace("#previewer","");
  window["currentTextHolderId" + prNum] = $(Configuration.tab);
  
  $(document).ready(function() {
    $.ajax({
        type: "GET",
        url: fileUrl,
        dataType: "text",
        success: function(data) {processData(data);}
     });
  });

  function processData(allText) {

	var allTextLines = allText.split(/\r\n|\n/);

	var table = "<div id=\"table-scroll\"><table width=\"700\" height=\"550\" border=\"0\">";


	for (var i=0; i<allTextLines.length; i++) {
		var line = allTextLines[i].replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;");
		table += "<tr><td>"+line+"</td></tr>";
	}

	table += "</table></div>";
	window["currentTextHolderId" + prNum].append(table);

    // var record_num = 5;  // or however many elements there are in each row
    // var allTextLines = allText.split(/\r\n|\n/);
    // var entries = allTextLines[0].split(',');
    // var lines = [];

    // var headings = entries.splice(0,record_num);
    // while (entries.length>0) {
    //     var tarr = [];
    //     for (var j=0; j<record_num; j++) {
    //         tarr.push(headings[j]+":"+entries.shift());
    //     }
    //     lines.push(tarr);
    // }
    // alert(lines);
  }

  
}(jQuery, Configuration));