function updateInterface(licenseType, rightsHolder, licenseText, licenseUrl, allowDownload, imageBase, authorName) {
	
	//Two golbally defined variables that are used in file.scala.html to check for download permissions.
	checkAllowDownload = allowDownload;
	checkLicenseType = licenseType;
	
	if (licenseType == 'license1') {
		if (rightsHolder == null || rightsHolder.trim().length == 0){
        	//No rights holder set, so default to "Author"
        	rightsHolder = authorName;
        }
		if (licenseText == null || licenseText.trim().length == 0) {
			licenseText = 'All Rights Reserved';
		}
		if (licenseUrl != null && licenseUrl.trim().length > 0) {                	
			if (!licenseUrl.startsWith("http")) {                    	
				licenseUrl = "http://" + licenseUrl;                        
			}
			licenseText = '<a href="' + licenseUrl + '" target="_blank">' + licenseText + '</a>';
		}
	}
	else if (licenseType == 'license2') {

		//No checkboxes selected
		if (licenseText == "Attribution-NonCommercial-NoDerivs" || licenseText == "by-nc-nd") {
			rightsHolder = '<img src="' + imageBase + '/cc-by-nc-nd.png" alt="' + rightsHolder + '" />';
			licenseText = '<a href="http://creativecommons.org/licenses/by-nc-nd/3.0/" target="_blank">Attribution-NonCommercial-NoDerivs</a>';
		}
		//Only commercial selected
		else if (licenseText == "Attribution-NoDerivs" || licenseText == "by-nd") {
			rightsHolder = '<img src="' + imageBase + '/cc-by-nd.png" alt="' + rightsHolder + '" />';
			licenseText = '<a href="http://creativecommons.org/licenses/by-nd/3.0/" target="_blank">Attribution-NoDerivs</a>';
		}
		//Only remixing selected
		else if (licenseText == "Attribution-NonCommercial" || licenseText == "by-nc") {
			rightsHolder = '<img src="' + imageBase + '/cc-by-nd.png" alt="' + rightsHolder + '" />';
			licenseText = '<a href="http://creativecommons.org/licenses/by-nc/3.0/" target="_blank">Attribution-NonCommercial</a>';
		}
		//Remixing and Sharealike selected
		else if (licenseText == "Attribution-NonCommercial-ShareAlike" || licenseText == "by-nc-sa") {
			rightsHolder = '<img src="' + imageBase + '/cc-by-nc-sa.png" alt="' + rightsHolder + '" />';
			licenseText = '<a href="http://creativecommons.org/licenses/by-nc-sa/3.0/" target="_blank">Attribution-NonCommercial-ShareAlike</a>';
		}
		//All checkboxes selected
		else if (licenseText == "Attribution-ShareAlike" || licenseText == "by-sa") {
			rightsHolder = '<img src="' + imageBase + '/cc-by-sa.png" alt="' + rightsHolder + '" />';
			licenseText = '<a href="http://creativecommons.org/licenses/by-sa/3.0/" target="_blank">Attribution-ShareAlike</a>';
		}
		//Commercial and Remixing selected
		else if (licenseText == "Attribution" || licenseText == "by-nc-nd") {
			rightsHolder = '<img src="' + imageBase + '/cc-by.png" alt="' + rightsHolder + '" />';
			licenseText = '<a href="http://creativecommons.org/licenses/by/3.0/" target="_blank">Attribution</a>';			
		}
		else {
			rightsHolder = 'Creative Commons';
			licenseText = 'Specific level info';
		}
	}
	else if (licenseType == 'license3') {
			rightsHolder = '<img src="' + imageBase + '/cc-pd.png" alt="' + rightsHolder + '" />'
			licenseText = '<a href="http://creativecommons.org/publicdomain/zero/1.0/" target="_blank">Public Domain Dedication</a>';
	}
	else {
		alert('Extra case!!');
	}

	//Update the display and close the editor
	$("#rightsholderdata").html(rightsHolder);
	$("#licensetextdata").html(licenseText);

	//return false;
}

function updateData(id, imageBase, sourceObject, authorName) {
	var licenseType = $('input[name=type]:checked', '#form1').val();	        
	var rightsHolder = $('input[name=ownername]').val();
	var licenseText = $('input[name=licensedesc]').val();
	var licenseUrl = $('input[name=licenseurl]').val();
	var allowDownload = $('input[name=allowDownload]').prop('checked').toString(); 

	if (licenseType == "license1") {
		if (rightsHolder == null || rightsHolder.trim().length == 0) {
			//for this license type, rights holder can't be null. default to Author
			rightsHolder = authorName;
			//$('#ownrights').trigger('click');
		}

		if (licenseText == null || licenseText.trim().length == 0) {
			licenseText = "All Rights Reserved";
		}

		if (licenseText != null && licenseText.trim().length == 0) {
			//don't allow empty string as a value. explicitly set it to null.
			licenseText = null;
		}
	}
	if (licenseType == "license2") {
		//Change the rightsHolder

		var commBox = $('#commercial');
		var remixBox = $('#remixing');
		var shareBox = $('#sharealike');

		//No checkboxes selected
		if (!commBox.prop('checked') && !remixBox.prop('checked') && !shareBox.prop('checked')) {
			rightsHolder = "Creative Commons";
			licenseText = "by-nc-nd";
			licenseUrl = "http://creativecommons.org/licenses/by-nc-nd/3.0/";
		}
		//Only commercial selected
		else if (commBox.prop('checked') && !remixBox.prop('checked') && !shareBox.prop('checked')) {
			rightsHolder = "Creative Commons";
			licenseText = "Attribution-NoDerivs";
			licenseUrl = "http://creativecommons.org/licenses/by-nd/3.0/";
		}
		//Only remixing selected
		else if (!commBox.prop('checked') && remixBox.prop('checked') && !shareBox.prop('checked')) {
			rightsHolder = "Creative Commons";
			licenseText = "Attribution-NonCommercial";
			licenseUrl = "http://creativecommons.org/licenses/by-nc/3.0/";
		}
		//Remixing and Sharealike selected
		else if (!commBox.prop('checked') && remixBox.prop('checked') && shareBox.prop('checked')) {
			rightsHolder = "Creative Commons";
			licenseText = "Attribution-NonCommercial-ShareAlike";
			licenseUrl = "http://creativecommons.org/licenses/by-nc-sa/3.0/";
		}
		//All checkboxes selected
		else if (commBox.prop('checked') && remixBox.prop('checked') && shareBox.prop('checked')) {
			rightsHolder = "Creative Commons";
			licenseText = "Attribution-ShareAlike";
			licenseUrl = "http://creativecommons.org/licenses/by-sa/3.0/";
		}
		//Commercial and Remixing selected
		else if (commBox.prop('checked') && remixBox.prop('checked') && !shareBox.prop('checked')) {
			rightsHolder = "Creative Commons";
			licenseText = "Attribution";
			licenseUrl = "http://creativecommons.org/licenses/by/3.0/";
		}
		else {
			rightsHolder = 'Creative Commons';
			licenseText = 'Specific level info';
		}
	}
	else if (licenseType == 'license3') {
		rightsHolder = "Public Domain Dedication";
		licenseText = "Public Domain Dedication";
		licenseUrl = "http://creativecommons.org/publicdomain/zero/1.0/";
	}

	//var jsonData = JSON.stringify({"licenseData":[licenseType, rightsHolder, licenseText, licenseUrl, allowDownload]});
	var jsonData = JSON.stringify({"licenseType":licenseType, "rightsHolder":rightsHolder, "licenseText":licenseText, "licenseUrl":licenseUrl, "allowDownload":allowDownload});

	var request = null;
     
	if (sourceObject == 'dataset') {
		request = jsRoutes.api.Datasets.updateLicense(id).ajax({
			data: jsonData,
			type: 'POST',
			contentType: "application/json",
		});
	}
	else if (sourceObject == 'file') {
		request = jsRoutes.api.Files.updateLicense(id).ajax({
			data: jsonData,
			type: 'POST',
			contentType: "application/json",
		});
	}
	else {
		console.log("error case, no valid sourceObject");
	}

	request.done(function (response, textStatus, jqXHR){
		//console.log("Response " + response);
		//Sucessful update of the DB
		updateInterface(licenseType, rightsHolder, licenseText, licenseUrl, allowDownload, imageBase, authorName);

		$("#editlicense").addClass('collapsed');
		$("#collapseSix").collapse('toggle');   
	});


	request.fail(function (jqXHR, textStatus, errorThrown){
		console.error(
				"The following error occured: "+
				textStatus, errorThrown

		);
		alert("Updating the license information failed.");
	});

	return false;	        	           	       	       
}			