function removeFile(fileId,event){
	var request = $.ajax({
	       type: 'POST',
	       url: "http://"+hostIp+":"+window.location.port+"/api/files/"+fileId+"/remove"
	     });
	request.done(function (response, textStatus, jqXHR){
        console.log("Response " + response);
        $(event.target.parentNode.parentNode).remove();    
    });
	request.fail(function (jqXHR, textStatus, errorThrown){
		console.error(
    		"The following error occured: "+
    		textStatus, errorThrown		            
			);
		alert("ERROR: " + errorThrown +". File not removed." );
			});
}