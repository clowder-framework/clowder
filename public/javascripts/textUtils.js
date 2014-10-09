//Taken from StackOverflow: http://stackoverflow.com/questions/4535888/jquery-text-and-newlines
//
//To be utilized in cases where newlines need to be replaced by br elements.
//
//Probably need a converse method, to reverse the operation, when going from HTML to a textarea or textfield.
//
function htmlForTextWithEmbeddedNewlines(text) {
    var htmls = [];
    var lines = text.split(/\n/);
    // The temporary <div/> is to perform HTML entity encoding reliably.
    //
    // document.createElement() is *much* faster than jQuery('<div></div>')
    // http://stackoverflow.com/questions/268490/
    //
    // You don't need jQuery but then you need to struggle with browser
    // differences in innerText/textContent yourself
    var tmpDiv = jQuery(document.createElement('div'));
    for (var i = 0 ; i < lines.length ; i++) {
        htmls.push(tmpDiv.text(lines[i]).html());
    }
    return htmls.join("<br>").html();
}