function closeSpaceConfig() {
	//Close the edit form
	$("#editspaceconfig").addClass('collapsed');
	$("#collapseSix").collapse('toggle');
	
	//Reset the data to the current values
	var text = $("#spacedescdisplay").text();
	$("#editspacedesc").val(text);
	text = $("#spacenamedisplay").text();
	$("#editspacename").val(text);
	text = $('#timetolive').html();
	$("#edittime").val(text);
	text = $('#expireenabled').html();
	
	var $radios = $('input:radio[name=radiogroup]');
	if (text == "On") {
		$radios.filter('[value=expiretrue]').prop('checked', true);
	}
	else {
		$radios.filter('[value=expirefalse]').prop('checked', true);
	}
	
	return false;
}

function updateSpaceConfig(spaceId) {
	var description = $("#editspacedesc").val();
	var name = $("#editspacename").val();
	var encName = htmlEncode(name);
	var encDescription = htmlEncode(description);
	var timeToLive = $('#edittime').val();
	var expireEnabled = $('#exptrue').prop('checked');
	console.log("expireEnabled is " + expireEnabled);
	
	var jsonData = JSON.stringify({"description":encDescription, "name":encName, "timetolive":timeToLive, "enabled":expireEnabled});

	var request = jsRoutes.api.Spaces.updateSpace(spaceId).ajax({
	data: jsonData,
	type: 'POST',
	contentType: "application/json"
	});

	request.done(function (response, textStatus, jqXHR){
		console.log("Response " + response);
		//Sucessful update of the DB - update the interface
	
		$("#spacedescdisplay").text(htmlDecode(encDescription));
		$("#spacenamedisplay").text(htmlDecode(encName));
		$('#timetolive').text(timeToLive);
		if (expireEnabled) {
			$('#expireenabled').html("On");
		}
		else {
			$('#expireenabled').html("Off");
		}
	
		closeSpaceConfig();
	});

	request.fail(function (jqXHR, textStatus, errorThrown){
		console.error("The following error occured: "+textStatus, errorThrown);
		var errMsg = "You must be logged in to update the information about a space.";
		if (!checkErrorAndRedirect(jqXHR, errMsg)) {
			alert("The space information was not updated due to : " + errorThrown);
		}
	});

	return false;
}