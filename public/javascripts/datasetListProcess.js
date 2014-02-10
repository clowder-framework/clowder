function removeDataset(datasetId,event, reloadPage){
	if(reloadPage === undefined) reloadPage = false;
	
	var request = $.ajax({
	       type: 'POST',
	       url: "http://"+hostIp+":"+window.location.port+"/api/datasets/"+datasetId+"/remove"
	     });
	request.done(function (response, textStatus, jqXHR){
        console.log("Response " + response);
        $(event.target.parentNode.parentNode).remove();
        
        if(reloadPage == true)
        	location.reload(true);
    });
	request.fail(function (jqXHR, textStatus, errorThrown){
		console.error(
    		"The following error occured: "+
    		textStatus, errorThrown		            
			);
		alert("ERROR: " + errorThrown +". Dataset not removed. Maybe it was already removed." );
			});	
}