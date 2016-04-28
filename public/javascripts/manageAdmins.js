function removeAdmin(email){

	var adminId = {};
	adminId['email'] = email;

	var request = jsRoutes.api.Admin.removeAdmin().ajax({
		type: 'POST',
	    data: JSON.stringify(adminId),
	    contentType: "application/json"
	});

	request.done(function (response, textStatus, jqXHR){
        console.log("Response " + response);        
        
        if(textStatus == "success")
        	notify("Admin removed.", "success");
        else if(textStatus == "notmodified")
        	notify("Admin was removed already.");
    });
	request.fail(function (jqXHR, textStatus, errorThrown){
		console.error(
    		"The following error occured: "+
    		textStatus, errorThrown		            
			);
		notify("ERROR: " + errorThrown +". Admin not removed.", error);
			});	
}