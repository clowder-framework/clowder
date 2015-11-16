// Author: Constantinos Sophocleous
(function ($, Configuration) {
	console.log("Iframe previewer for " + Configuration.id);
	
	console.log("Updating tab " + Configuration.tab);

	  $.ajax({
		    url: Configuration.url,
		    async:false,
		    success: function (data) {
		    	var resourceData = JSON.parse(data.trim());
		    	var appendStr = "";
		    	var fileId = Configuration.fileid;

		    	if(resourceData.wasRedirected && Configuration.authenticatedFileModify)
		    			$(Configuration.tab).append("<p><b>NOTE: Due to insecure content in iframe, iframe URL was auto-converted to HTTPS.</b></p>");
		    			    	
		    	appendStr = appendStr.concat("<div class='modal fade iframeModalDiv' id='externResource_"+fileId+"' tabindex='-1' role='dialog' aria-labelledby='externResource_"+fileId+"' aria-hidden='true'>");
		    	appendStr = appendStr.concat('<div class="modal-content"><div class="modal-header"><button type="button" class="close iframeClose" data-dismiss="modal" aria-hidden="true">&times;</button>');
		    	appendStr = appendStr.concat('<h4 class="modal-title" id="externResourceLabel_'+fileId+'">'+resourceData.title+'</h4>');
		    	appendStr = appendStr.concat('</div><div class="modal-body iframeModalDivInternal"><iframe class="iframeModal" src="'+resourceData.url+'" frameBorder="0"><p>Could not load iframe</p></iframe></div></div></div>');
		    	appendStr = appendStr.concat('<div class="prevIframeWrap"><iframe class="prevIframe" scrolling="no" src="'+resourceData.url+'"><p>Could not load iframe</p></iframe></div>');
		    	appendStr = appendStr.concat('<a class="iframeLink" href="#!" data-toggle="modal" data-target="#externResource_'+fileId+'">');
		    	appendStr = appendStr.concat('<div class="transbox"><button type="button" class="btn btn-default iframeBtn" aria-hidden="true">&plus;</button></div></a>');
		    	$(Configuration.tab).append(appendStr);
		    	$(Configuration.tab).append('<p><a href="'+resourceData.url+'" target="_blank">Click here to open in new window</a></p>');
		    			    	
		    	//Dynamic resize of modal iframe
		    	$(document).ready(function() {
		    		$("#externResource_"+fileId).css("height", Math.round($(window).height()*90/parseFloat(100))+"px" );
		    		$("#externResource_"+fileId+" .modal-content").css("height", Math.round($(window).height()*90/parseFloat(100))+"px" );
		    		$("#externResource_"+fileId+" .modal-body").css("height", Math.round($(window).height()*82/parseFloat(100))+"px" );
  					$("#externResource_"+fileId+" .iframeModal").css("height", Math.round($(window).height()*77/parseFloat(100))+"px" );
		    		
		  			window["iframeWidth_"+fileId]= $("#externResource_"+fileId+" .modal-body").get(0).clientWidth;
		  			window["iframeWindowHeight_"+fileId]= $(window).height();
		  			$(window).resize(function(){
		  				newWidth = $("#externResource_"+fileId+" .modal-body").get(0).clientWidth;
		  				newHeight = $(window).height();
		  				if(newWidth != window["iframeWidth_"+fileId]){
		  					$("#externResource_"+fileId+" .modal-body").css("height", Math.round($("#externResource_"+fileId+" .modal-body").get(0).clientHeight*newWidth/parseFloat(window["iframeWidth_"+fileId]))+"px" );
		  					$("#externResource_"+fileId+" .modal-content").css("height", Math.round($("#externResource_"+fileId+" .modal-content").get(0).clientHeight*newWidth/parseFloat(window["iframeWidth_"+fileId]))+"px" );
		  					$("#externResource_"+fileId+" .iframeModal").css("height", Math.round($("#externResource_"+fileId+" .iframeModal").get(0).clientHeight*newWidth/parseFloat(window["iframeWidth_"+fileId]))+"px" );
		  					$("#externResource_"+fileId).css("height", Math.round($("#externResource_"+fileId).get(0).clientHeight*newWidth/parseFloat(window["iframeWidth_"+fileId]))+"px" );
		  					window["iframeWidth_"+fileId]=newWidth;
		  				}
		  				if(newHeight != window["iframeWindowHeight_"+fileId]){
		  					$("#externResource_"+fileId+" .modal-body").css("height", Math.round($("#externResource_"+fileId+" .modal-body").get(0).clientHeight*newHeight/parseFloat(window["iframeWindowHeight_"+fileId]))+"px" );
		  					$("#externResource_"+fileId+" .modal-content").css("height", Math.round($("#externResource_"+fileId+" .modal-content").get(0).clientHeight*newHeight/parseFloat(window["iframeWindowHeight_"+fileId]))+"px" );
		  					$("#externResource_"+fileId+" .iframeModal").css("height", Math.round($("#externResource_"+fileId+" .iframeModal").get(0).clientHeight*newHeight/parseFloat(window["iframeWindowHeight_"+fileId]))+"px" );
		  					$("#externResource_"+fileId).css("height", Math.round($("#externResource_"+fileId).get(0).clientHeight*newHeight/parseFloat(window["iframeWindowHeight_"+fileId]))+"px" );
		  					window["iframeWindowHeight_"+fileId]=newHeight;
		  				}
		  			});
		  		});
		    			    			    	
		    	 },
		    	 error: function(jqXHR, textStatus, errorThrown) { 
		    	        alert("Status: " + textStatus); alert("Error: " + errorThrown); 
		    	    },
		    dataType: 'text'
		});

	 			
}(jQuery, Configuration));
