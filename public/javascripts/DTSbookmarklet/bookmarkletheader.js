var js;
var css_div;
var js_lunr;
var hostIP="http://dts1.ncsa.illinois.edu:9000/";
//var hostIP="http://smruti-mac.ncsa.illinois.edu:9000/";
//var hostIP="http://localhost:9000/";
css_div = document.createElement('link');
css_div.rel = 'stylesheet';
css_div.media = 'screen';
css_div.href = hostIP+"assets/stylesheets/dtsstylediv.css";
document.head.appendChild(css_div);
confirmStyle();

function confirmStyle() {
	console.log('Stylediv file loaded');
	js = document.createElement('script');
	js.addEventListener('load', confirmSuccess);
	js.src = '//ajax.googleapis.com/ajax/libs/jquery/1.9.0/jquery.js';
	document.head.appendChild(js);
}

function confirmSuccess() {
	console.log('jQuery loaded!');
	js_lunr = document.createElement('script');
	js_lunr.addEventListener('load', confirmLunr);
	js_lunr.src = hostIP+"assets/javascripts/DTSbookmarklet/lunr.min.js";
	document.head.appendChild(js_lunr);

}

function confirmLunr() {
	console.log('lunr loaded!');
	document.body.appendChild(document.createElement('script')).src = hostIP+"assets/javascripts/DTSbookmarklet/dtsbookmarklet.js";
   console.log("bookmarklet uploaded");
}
