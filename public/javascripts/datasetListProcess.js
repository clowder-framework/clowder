function removeDataset(datasetId,event, reloadPage){
	if(reloadPage === undefined) reloadPage = false;
	
	var request = $.ajax({
	       type: 'POST',
	       url: window.location.protocol + "//" + window.location.hostname + (window.location.port ? ':' + window.location.port: '')+"/api/datasets/"+datasetId+"/remove"
	     });
	request.done(function (response, textStatus, jqXHR){
        console.log("Response " + response);
        $(event.target.parentNode.parentNode).remove();
        
        if(reloadPage == true)
        	location.reload(true);
    });
	request.fail(function (jqXHR, textStatus, errorThrown){
		console.error("The following error occured: "+textStatus, errorThrown);
        var errMsg = "You must be logged in to remove a dataset from the system.";
        if (!checkErrorAndRedirect(jqXHR, errMsg)) {
            alert("The dataset was not removed due to : " + errorThrown);
        }   
		
	});	
}