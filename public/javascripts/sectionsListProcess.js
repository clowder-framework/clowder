			function postSectionDescr(id, posturl) {			
				var theText =  $('#sectiondescrText_'+id).val();
							
				console.log("Posting " + theText + " to " + posturl);
				var request = $.ajax({
					url:  posturl,
					data: JSON.stringify({ descript: theText }),
					type: "POST",
					contentType: "application/json",
		     	});
				request.done(function (response, textStatus, jqXHR) { 
					console.log("Response " + response);
					window["previousSectiondescr_"+id] =  $('#sectiondescrText_'+id).val(); 
					$('#sectiondescr_'+id).text(window["previousSectiondescr_"+id]);									
					$('#sectiondescrEditArea_'+id).css("display","none");
					$('#sectiondescr_'+id).css("display","block");
					$('#sectiondescrPost_'+id).css("display","none");
					$('#sectiondescrCancel_'+id).css("display","none");
					$('#sectiondescrEdit_'+id).css("display","inline");					
				});
				request.fail(function (jqXHR, textStatus, errorThrown){
					console.error("The following error occured: "+textStatus, errorThrown);
                    var errMsg = "You must be logged in to submit a section description.";                    
                    if (!checkErrorAndRedirect(jqXHR, errMsg)) {
                        alert("The section description was not posted due to : " + errorThrown);
                    }					
				});						
				return false;			
			}
			
			function cancelEditSectionDescr(id){				
				$('#sectiondescr_'+id).text(window["previousSectiondescr_"+id]);
				$('#sectiondescrEditArea_'+id).css("display","none");
				$('#sectiondescrText_'+id).val(window["previousSectiondescr_"+id]);								
				$('#sectiondescr_'+id).css("display","block");
				$('#sectiondescrPost_'+id).css("display","none");
				$('#sectiondescrCancel_'+id).css("display","none");
				$('#sectiondescrEdit_'+id).css("display","inline");
				
				return false;
			}
			
			function openForEditSectionDescr(id){
				$('#sectiondescr_'+id).css("display","none");
				$('#sectiondescrEditArea_'+id).css("display","block");			
				$('#sectiondescrEdit_'+id).css("display","none");
				$('#sectiondescrPost_'+id).css("display","inline");
				$('#sectiondescrCancel_'+id).css("display","inline");
				
				return false;
			}