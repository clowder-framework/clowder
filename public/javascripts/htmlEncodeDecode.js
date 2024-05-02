////
//
// JavaScript file to contain elements that are related to encoding and decoding of HTML to and from regular text. 
//
//
////

function htmlEncode(value){
	return $('<div/>').text(value).html();
}

function htmlDecode(value){
	return $('<div/>').html(value).text();
}

function escapeString(htmlStr) {
	return htmlStr.replace(/&/g, "&amp;")
		.replace(/</g, "&lt;")
		.replace(/>/g, "&gt;")
		.replace(/"/g, "&quot;")
		.replace(/'/g, "&#39;");

}