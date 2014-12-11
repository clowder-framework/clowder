function removeDataset(datasetId,event, reloadPage){
	if(reloadPage === undefined) reloadPage = false;
	
	var request = jsRoutes.api.Datasets.deleteDataset(datasetId).ajax({
		type: 'DELETE'
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

function removeDatasetAndRedirect(datasetId, url){
	if(url === undefined) reloadPage = "/datasets";

	var request = jsRoutes.api.Datasets.deleteDataset(datasetId).ajax({
		type: 'DELETE'
	});

	request.done(function (response, textStatus, jqXHR){
		console.log("Response " + response);		
		window.location.href=url;
	});
	
	request.fail(function (jqXHR, textStatus, errorThrown){
        console.error("The following error occured: " + textStatus, errorThrown);
        var errMsg = "You must be logged in to delete a dataset from the system.";        
        if (!checkErrorAndRedirect(jqXHR, errMsg)) {
            alert("The dataset was not deleted from the system due to : " + errorThrown);
        }
	});	
}