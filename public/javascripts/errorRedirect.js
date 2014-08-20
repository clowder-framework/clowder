function checkErrorAndRedirect(jqXHR, userMsg) {
	var responseText = jqXHR.responseText;
    console.log("jqXHR.responseText is " + responseText);    
    if (responseText == "Authentication Required") {
        window.location.href="/utils/authenticationRequiredMessage/" + encodeURIComponent(userMsg);
        return true;
    }
    else {
        return false;                   
    }   
}