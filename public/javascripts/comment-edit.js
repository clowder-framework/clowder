function editComment(commentId, commentText, reloadPage){
	
	if(reloadPage === undefined) reloadPage = false;
	
	var jsonData = JSON.stringify({"commentText":commentText});	
	var theText = commentText;
    
    var request = jsRoutes.api.Comments.editComment(commentId).ajax({
  	    data: jsonData,
        type: 'POST',
        contentType: "application/json",
    });
    
    request.done(function (response, textStatus, jqXHR){
        console.log("Response " + response);
        //Sucessful update of the DB - update the interface
    	$("#comment-body_" + commentId).html(theText);
    });

     
     request.fail(function (jqXHR, textStatus, errorThrown){
        console.error(
            "The following error occured: "+
            textStatus, errorThrown            
        );
        alert("Editing the comment failed. " + textStatus + " and " + errorThrown);
     });
    
	 return false;	
}