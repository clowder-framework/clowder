function updatex3dPosition(prNum){
	if(window["oldx3dposition" + prNum] != window["thisPreview" + prNum].offset().top){
		$("#x3dElement" + prNum).attr("style", "position:absolute;top:" + (window["thisPreview" + prNum].offset().top) + "px;");
		window["oldx3dposition" + prNum] = window["thisPreview" + prNum].offset().top;
	}
}

function renderSingleFrame(prNum,index){
	
	var width = 750;
	var height = 550;
	var inner = "<x3d id='x3dElement" + prNum + "' showStat='false' showLog='true' height='" + height + "px' width='" + width + "px' x='0px' y='0px' style='position:absolute;top:" + ($(Configuration.tab).offset().top) + "px;'>";
	

	//if((bufferWriteCounter - bufferReadCounter) > 50)
	if((window["bufferWriteCounter" + prNum] - window["bufferReadCounter" + prNum]) > 0)	
	{
		if(window["frameArray" + prNum][index] != undefined){
			$("#x3dElement"+prNum).replaceWith(inner + frameArray[index] + "</x3d>");
			x3dom.reload();
			bufferReadCounter++;
			setTimeout("renderSingleFrame('" + prNum + "'," + (index+1) + ");",30);
			console.log("render frame " + index);
		}
		else{
			setTimeout("renderSingleFrame('" + prNum + "'," + (index+1) + ");",30);
		}
	}
	else {
		//setTimeout("renderSingleFrame('" + prNum + "'," + (index) + ");",100);
	}			

}


function fetchFrame(index,prNum){

	$.ajax({
		    url: previewUrl + window["previewFileIds" + prNum][index],
		    async:true,
		    success: function (data,status,jqXHR) {
				
				if(index < window["previewFileIds" + prNum].length-1) {
					fetchFrame(index+1, prNum);
					window["bufferWriteCounter" + prNum]++;
					window["frameArray" + prNum][index] = data;
					console.log("fetched file: " + index);
				}				
		  	},
		    dataType: 'text',
		    error: function (obj, status, error) {				
				console.log("Error fetching preview file:"  + index + " " + status + " " + error);

				if(index < window["previewFileIds" + prNum].length-1) {
					fetchFrame(index+1, prNum);
					window["bufferWriteCounter" + prNum]++;
					window["frameArray" + prNum][index] = data;
				}
			}
	});
}


(function ($, Configuration) {

  console.log("ONI previewer for " + Configuration.id);
  
  var hostAddress = window.location.protocol + "//" + window.location.hostname + (window.location.port ? ':' + window.location.port: '')
  
  var fileUrl = hostAddress + Configuration.url;
  var pathJs = hostAddress + Configuration.jsPath + "/";
  previewUrl = hostAddress + Configuration.appContext + "/api/previews/";
  var width = 750;
  var height = 550;
  var cacheLength = 5;  
  var prNum = Configuration.tab.replace("#previewer","");

 $(Configuration.tab).append("<table id='x3dElementTable" + prNum + "' style ='margin-bottom:560px;'></table>");

  var inner = "<x3d id='x3dElement" + prNum + "' showStat='false' showLog='true' showProgress='false' height='" + height + "px' width='" + width + "px' x='0px' y='0px' style='position:absolute;top:" +    ($(Configuration.tab).offset().top ) + "px;'>";
  window["oldx3dposition" + prNum] = $(Configuration.tab).offset().top;
  window["thisPreview" + prNum] = $(Configuration.tab);
 
  setInterval("updatex3dPosition('" + prNum + "');", 50);

   var s = document.createElement("script");
  s.type = "text/javascript";
  s.src = pathJs + "x3dom.js";
  //s.src = "http://www.x3dom.org/release/x3dom.js";
  console.log("Updating tab " + Configuration.tab);
  $(Configuration.tab).append(s);  

  window["previewFileIds" + prNum] = [];
  window["bufferReadCounter" + prNum] = 0;
  window["bufferWriteCounter" + prNum] = 0;
  window["indexPreviewIdMap" + prNum] = [];

  //fetching the list of preview files

  $.ajax({
	    url: fileUrl,
	    async:false,
	    success: function (data) {
	    	window["previewFileIds" + prNum] = data.split(",");
			window["frameArray" + prNum] = new Array(window["previewFileIds" + prNum].length-1);			
	    	 },
	    dataType: 'text',
	    error: function (obj, status, error) {
			console.log("Error fetching preview file:"  + status + " " + error);
		}
	});


	$.ajax({
	    url: previewUrl + window["previewFileIds" + prNum][0],
	    async:false,
	    success: function (data) {
			$(Configuration.tab).append(inner + data + "</x3d>");
			window["frameArray" + prNum][0] = data;
			window["bufferWriteCounter" + prNum]++;
			window["bufferReadCounter" + prNum]++;
			
	    	 },
	    dataType: 'text',
	    error: function (obj, status, error) {
		$(Configuration.tab).append(status + " " + error);
		}
	});
	
	fetchFrame(1,prNum);  
	setTimeout("renderSingleFrame('" + prNum + "'," + 1 + ");",1000); 

}(jQuery, Configuration));
