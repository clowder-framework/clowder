//Functions to edit comments
//
//Referenced by comment.scala.html
//
function editComment(commentId, commentText, reloadPage){
	
	if(reloadPage === undefined) reloadPage = false;
	
	var jsonData = JSON.stringify({"commentText":commentText});	
	var theText = commentText;
    
    var request = jsRoutes.api.Comments.editComment(commentId).ajax({
  	    data: jsonData,
        type: 'POST',
        contentType: "application/json"
    });
    
    request.done(function (response, textStatus, jqXHR){
        console.log("Response " + response);
        //Sucessful update of the DB - update the interface
    	$("#comment-body_" + commentId).html(theText.replace(/\n/g, "<br>"));
    });

     
     request.fail(function (jqXHR, textStatus, errorThrown){
    	 console.error("The following error occured: " + textStatus, errorThrown);
         var errMsg = "You must be logged in to edit a comment.";                                
         if (!checkErrorAndRedirect(jqXHR, errMsg)) {
         	notify("The comment was not edited due to : " + errorThrown, "error");
         }  
         else {
        	 var responseText = jqXHR.responseText;
        	 notify(responseText, "error");
         }
     });
    
	 return false;	
}