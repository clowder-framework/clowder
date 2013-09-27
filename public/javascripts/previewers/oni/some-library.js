function updatex3dPosition(prNum){
	if(window["oldx3dposition" + prNum] != window["thisPreview" + prNum].offset().top){
		$("#x3dElement" + prNum).attr("style", "position:absolute;top:" + (window["thisPreview" + prNum].offset().top + 288) + "px;");
		window["oldx3dposition" + prNum] = window["thisPreview" + prNum].offset().top;
	}
}

function renderSingleFrame(prNum,index){
	
	var width = 750;
	var height = 550;
	var inner = "<x3d id='x3dElement" + prNum + "' showStat='false' showLog='false' height='" + height + "px' width='" + width + "px' x='0px' y='0px' style='position:absolute;top:" + ($(Configuration.tab).offset().top + 288) + "px;'>";
	

	if((bufferWriteCounter - bufferReadCounter) > 50)
	{
		if(frameArray[index] != undefined){
			$("#x3dElement"+prNum).replaceWith(inner + frameArray[index] + "</x3d>");
			x3dom.reload();
			bufferReadCounter++;
			setTimeout("renderSingleFrame('" + prNum + "'," + (index+1) + ");",30);
			console.log("render frame " + index);
		}
		else{
			setTimeout("renderSingleFrame('" + prNum + "'," + (index) + ");",30);
		}
	}
	else {
		setTimeout("renderSingleFrame('" + prNum + "'," + (index) + ");",100);
	}			

}


function fetchFrame(index){

	$.ajax({
		    url: previewUrl + previewFileIds[index],
		    async:true,
		    success: function (data,status,jqXHR) {
				
				if(index < previewFileIds.length-2) {
					fetchFrame(index+1);
					bufferWriteCounter++;
					frameArray[index] = data;
					console.log("fetched file: " + index);
				}				
		  	},
		    dataType: 'text',
		    error: function (obj, status, error) {				
				console.log("Error fetching preview file:"  + index + " " + status + " " + error);

				if(index < previewFileIds.length-2) {
					fetchFrame(index+1);
					bufferWriteCounter++;
					frameArray[index] = data;
				}
			}
	});
}


(function ($, Configuration) {

  console.log("ONI previewer for " + Configuration.id);
  
  var fileUrl = "http://" + Configuration.hostIp + ":" + window.location.port + Configuration.url;
  var pathJs = "http://" + Configuration.hostIp + ":" + window.location.port + Configuration.jsPath + "/";
  previewUrl = "http://" + Configuration.hostIp + ":" + window.location.port + "/api/previews/";
  var width = 750;
  var height = 550;
  var cacheLength = 5;  
  var prNum = Configuration.tab.replace("#previewer","");

 $(Configuration.tab).append("<table id='x3dElementTable" + prNum + "' style ='margin-bottom:560px;'></table>")

  var inner = "<x3d id='x3dElement" + prNum + "' showStat='false' showLog='false' showProgress='false' height='" + height + "px' width='" + width + "px' x='0px' y='0px' style='position:absolute;top:" +    ($(Configuration.tab).offset().top ) + "px;'>";
  window["oldx3dposition" + prNum] = $(Configuration.tab).offset().top;
  window["thisPreview" + prNum] = $(Configuration.tab);
 
//  setInterval("updatex3dPosition('" + prNum + "');", 50);

   var s = document.createElement("script");
  s.type = "text/javascript";
 // s.src = pathJs + "x3dom.js";
  s.src = "http://www.x3dom.org/release/x3dom.js"
  console.log("Updating tab " + Configuration.tab);
  $(Configuration.tab).append(s);  

  previewFileIds = [];
  bufferReadCounter = 0;
  bufferWriteCounter = 0;
  indexPreviewIdMap = [];

  //fetching the list of preview files

  $.ajax({
	    url: fileUrl,
	    async:false,
	    success: function (data) {
			previewFileIds = data.split(",");
			frameArray = new Array(previewFileIds.length-1);			
	    	 },
	    dataType: 'text',
	    error: function (obj, status, error) {
			console.log("Error fetching preview file:"  + status + " " + error);
		}
	});


	$.ajax({
	    url: previewUrl + previewFileIds[15],
	    async:false,
	    success: function (data) {
			$(Configuration.tab).append(inner + data + "</x3d>");
			frameArray[0] = data;
			bufferWriteCounter++;
			bufferReadCounter++;
			
	    	 },
	    dataType: 'text',
	    error: function (obj, status, error) {
		$(Configuration.tab).append(status + " " + error);
		}
	});
	
	//fetchFrame(1);
	//setTimeout("renderSingleFrame('" + prNum + "'," + 1 + ");",1000);

}(jQuery, Configuration));
