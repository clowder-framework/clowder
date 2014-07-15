var img1;
var embd;
var status = '';
var statusCheck;
var countDone = 0;
var xMap = new Array();
var myVar = new Array();
var tags = {};
var imgdocs = {};
var promises = [];

console.log("I am top of dtsbookmarklet!");
var host = 'http://localhost:9000/';
//var host= 'http://smruti-mac.ncsa.illinois.edu:9000/';
var key = 'r1ek3rs';

// Create a 'div' container with an input box, search button, and close button

document.body.appendChild(document.createElement('div')).id = 'searchbox';
var sdiv = document.getElementById('searchbox');
var inputdiv = document.createElement('input');
inputdiv.id = 'inputbox';
inputdiv.type = 'text';
inputdiv.value = 'Type a keyword';
sdiv.appendChild(inputdiv);

var searchk = document.createElement('button');
// searchk.setAttribute('id','searchkp');
searchk.id = 'searchkp';
// searchk.setAttribute('type','button');
searchk.type = 'button';
searchk.innerHTML = "Search";
searchk.onclick = searchindex;
sdiv.appendChild(searchk);

var pdiv = document.createElement('p');
var node = document.createTextNode('Search your keyword');
pdiv.appendChild(node);
sdiv.appendChild(pdiv);

var fbtn1 = document.createElement('button');
fbtn1.id = 'closebutton';
fbtn1.type = 'button';
fbtn1.onclick = display;
fbtn1.innerHTML = "Close";
sdiv.appendChild(fbtn1);
var tdiv = document.createElement('div');

function display() {
	console.log('Closing the box');
	$('#searchbox').remove();
}

function searchindex() {
	var keyword = $('#inputbox').val();
	alert(keyword);
	if ($('#tablediv').length) {
		clear_table();
	}
	if (keyword != null) {

		console.log('------Results:--- ', index.search(keyword));

		var searchResults = index.search(keyword);

		var slen = searchResults.length;

		if (slen > 0) {
			var r = new Array();

			var tbl = document.createElement('table');
			tbl.style.width = '100%';
			tbl.setAttribute('border', '1');
			var tbdy = document.createElement('tbody');

			for ( var i = 0; i < slen; i++) {
				console.log("Image satisfying the search query: tags: ",
						imgdocs[searchResults[i].ref].tags, "   url:",
						imgdocs[searchResults[i].ref].url);
				r.push(imgdocs[searchResults[i].ref].url);
				var tr = document.createElement('tr');
				var td1 = document.createElement('td');
				var td2 = document.createElement('td');

				var tncontainer = document.createElement('div');
				var imgel = document.createElement("img");
				imgel.src = imgdocs[searchResults[i].ref].url;
				imgel.setAttribute("width", "50px");
				imgel.setAttribute("height", "50px");
				tncontainer.appendChild(imgel);
				td1.appendChild(tncontainer);
				td2.appendChild(document
						.createTextNode(imgdocs[searchResults[i].ref].tags));
				tr.appendChild(td1);
				tr.appendChild(td2);
				tbdy.appendChild(tr);
			}
			tbl.appendChild(tbdy);
			tdiv.appendChild(tbl);
			sdiv.appendChild(tdiv).id = 'tablediv';

			console.log('Number of Images whose tags matched the keyword : '
					+ slen.toString() + '  Matched Image URLs=' + r);

			var fbtn2 = document.createElement('button');
			fbtn2.id = 'clearbutton';
			fbtn2.type = 'button';
			fbtn2.onclick = clear_table;
			fbtn2.innerHTML = "Clear Search";
			sdiv.appendChild(fbtn2);

		} else {
			alert("Not Found");
		}

	}

}
function clear_table() {
	$('#tablediv').empty();
	$('#tablediv').remove();
	$('#clearbutton').remove();
}

var index = lunr(function() {
	this.field('tags', {
		boost : 10
	})
	this.field('url')
	this.ref('id')
})

function createdoc(tags, url) {
	var x = {
		// id:fid,
		tags : tags,
		url : url
	};
	return x;
}

function checkfetch(fileid) {
	console.log('-----[checkfetch-- ', fileid, ' --]-----');
	var exmd1 = new XMLHttpRequest();
	exmd1.addEventListener('readystatechange',
			function completionhandlerStatus(evt) {
				valuestatushandler(fileid, evt);
				console.log('[checkfetch--fileid: ', fileid,
						' -]-----[exmd1 Eventlistener]');
				// console.log('[checkfetch-',fileid,'-] file id='+ fileid);
			}, false);

	exmd1.open('GET', host + 'api/extractions/' + fileid + '/metadata?key='
			+ key, true);
	exmd1.setRequestHeader('Accept', 'application/json');
	exmd1.send();
	console.log('----- END -----[checkfetch-', fileid, '-]---------');

}

function valuestatushandler(id1, evt) {
	var status = 0;
	var readyState = 0;
	console.log('[value_status_handler] File id:'.fontcolor("red") + id1);

	try {
		status = evt.target.status;
		readyState = evt.target.readyState;
	} catch (e) {
		return;
	}
	if (readyState == 4) {
		if (status == 200) {
			var value = evt.target.responseText;
			var str = JSON.parse(value);
			console.log('----[value_status_handler]  STATUS OK  [RESPONSE]='
					.fontcolor("red")
					+ value);

			for ( var l = 0; l < xMap.length; l++) {
				console.log('xMap[', l, '].id=', xMap[l].id, '  str.fild_id=',
						str.file_id);
				if ((xMap[l].id) == (str.file_id)) {
					// xMap[l].status=str.Status;
					// imgdocs[str.id]=createdoc(str.id,tags,furl);
					xMap[l].tags = str.tags;
					console.log('[value_status_handler-', l, '-]  xMap[].url='
							+ xMap[l].url);
					console.log('[value_status_handler-', l, '-]  xMap[].id='
							+ xMap[l].id);
					console.log('[value_status_handler-', l,
							'-]  xMap[].status=' + xMap[l].status);
					console.log('[value_status_handler-', l, '-]  xMap[].tags='
							+ xMap[l].tags);
					// console.log('[value_status_handler-',l,'-]
					// xMap[].tags.length='+xMap[l].tags.length);
					// console.log('[value_status_handler-',l,'-]
					// xMap[].tags[0].valueslength='+xMap[l].tags[0].values.length);
					if (xMap[l].status != 'Done') {
						xMap[l].status = str.Status;
						console.log('if status!=Done:[value_status_handler-', l,
								'-]  xMap[].status=' + xMap[l].status);
						
						var tlen = xMap[l].tags.length;
						var tagsArr = new Array();
						for ( var m = 0; m < tlen; m++) {
							var vlen = xMap[l].tags[m].values.length;
							for ( var n = 0; n < vlen; n++) {
								var t = xMap[l].tags[m].values[n];
								tagsArr.push(t);
							}

						}// end of for -m
						console.log("id=" + xMap[l].id, "    tagsArr=",
								tagsArr, "   url=", xMap[l].url);
						imgdocs[xMap[l].id] = createdoc(tagsArr, xMap[l].url);
						index.add({
							id : xMap[l].id,
							tags : tagsArr.join(" ")
						});
						setTimeout(checkfetch,2000,str.file_id);
					}else{
						console.log("if status is Done : Do Nothing");
						console.log('else:[value_status_handler-', l,
								'-]  xMap[].status=' + xMap[l].status);
						
					}// end of if-else
					break;
				}

			}//end of for

		}
	}
}

function clearSetInterval() {
	myStopFunction();
	//checkResultStopFunction();
}

function myStopFunction() {
	for ( var k = 0; k < xMap.length; k++) {
		clearInterval(myVar[k]);
		console.log('Clearing the interval for--', k);
	}

}

function checkResultStopFunction() {

	clearInterval(statusCheck);
	console.log('Clearing the status Check');

}
function checkResults() {
	countDone=0;
	for ( var j = 0; j < xMap.length; j++) {
		if (xMap[j].status == 'Processing') {
			console.log('---[check Results]---status: Processing');
		} else if (xMap[j].status == 'Done') {
			countDone++;
			console.log('---countDone Incremented-- ');
			// clearInterval(myVar[j]);
			// console.log('Clearing the interval for--',j);
		} else if (xMap[j].status == 'Required Extractor is either busy or is not currently running. Try after some time.') {
			console
					.log('---[check Results]---status: Required Extractor is either busy or is not currently running. Try after some time.');
		}
		else{
			if(xMap[j].status==''){
				console.log('[checkResults]-- Status: is empty');
				if(checkUploadStatus(xMap[j].id)==200){
					console.log("--[checkResults] ---wait for execution---");
				}else{
					console.log("--upload has failed, so skip the execution--");
					countDone++;
				}
				
			}
		}
	}
	if (countDone == xMap.length) {
		status = 'Done';
		console.log('---Status is DONE---'.fontcolor("red"));
		for ( var key in tags) {
			var value = tags[key];
			console.log("key: ", key, "  value=", value);
		}
		//clearSetInterval();
       //   clearTimeout();
	}else{
		console.log("--setTimeout on checkResults--");
		setTimeout(checkResults,2000);
	}

}

function checkUploadStatus(id){
	for(var u=0;u<promises.length;u++){
		if(promises[u].status==200){
			console.log("promise.responseText=", promises[u].responseText,"  id=",id);
			if(promises[u].responseText.indexOf(id)!=-1){
				console.log("[checkUploadStatus]:", promises[u].responseText);
				return 200;
			}
		}
	}
	return 0;
}

function reqProcess(imgurl) {
	return function(fileidjson) {
		var str = fileidjson;
		var furl = imgurl;
		console.log('----[handler]----  STATUS: OK  [RESPONSE]=' + str.id
				+ '  url: ', furl);
		var fileidObj = new Object();
		fileidObj.url = furl;
		fileidObj.id = str.id;
		fileidObj.tags = '';
		fileidObj.status = '';
		console.log('----[handler]---- fileObj pushed to xMap');
		xMap.push(fileidObj);

	}

}

function ajaxRequest(requrl, imgurl) {
	var fd = {};
	fd['fileurl'] = imgurl;
	var request = $.ajax({
		type : "POST",
		url : host + "api/extractions/upload_url?key=" + key,
		accepts : "application/json",
		processData : false,
		contentType : "application/json",
		data : JSON.stringify(fd),
		success : reqProcess(imgurl)
	});

	promises.push(request);

}

img1 = document.body.getElementsByTagName('img');

console.log('No of Images found in this page:' + img1.length);

var embd = document.body.getElementsByTagName('iframe');
console.log('No. of Embbeded Images found :' + embd.length);

console.log('----Start of for----');

for ( var i = 0; i < img1.length; i++) {
	var fileUrl = img1[i].src;
	console.log('Sending the file to Medici : ' + img1[i].src);
	if (fileUrl.indexOf('data:') == -1) {
		ajaxRequest("", fileUrl);
	}

}
console.log('*** end of for****');

var uploadStatus=false;
var uploadCount;
var failCount=0;
var undefinedStatu=0;
var h=0;
var b=0;
setTimeout(continueExecution,1000);

function continueExecution(){
    b++;
	console.log("Number of times Function Entered b= ",b);
	uploadCount=0;
	failCount=0;
	var plen=promises.length;	
	console.log("Promises Array Length:",plen);
	for(var g=0;g<plen;g++){
		console.log("Promise status: [",g,"]=", promises[g].status);
		if(promises[g].status==undefined){
			console.log("Status undefined for g=",g);
		}else if(promises[g].status==200){
			uploadCount++;
			console.log("if: Upload sucuessful for promise p=",g)
		}else{
			console.log("else: Upload not Sucessful for Promise p=",g);
			failCount++;
		}
	} 
	if(plen==(uploadCount+failCount)){
		console.log("uploadStatus becomes true");
		uploadStatus=true;
		getMetadata();
	}
	else{
		setTimeout(continueExecution,1000);
		
	}
  }

var pid;
var mdata;

function getMetadata(){
	var plen=promises.length;	
	console.log("Promises Array Length:",plen);
	for(var g=0;g<plen;g++){
		console.log("Promise status: [",g,"]=", promises[g].status);
	    if(promises[g].status==200){
			pid=promises[g].responseText;
			console.log("[getMetadata] responseText: =",pid);
			var pidstr=pid.substring(7,pid.length-2);
			console.log("ResponseText id: " , pidstr);
			//myVar[g] = setInterval(checkfetch, 5000, pidstr);
			myVar[g] = setTimeout(checkfetch, 5000, pidstr);

		}else{
			//myVar[g] = setInterval(doNothing,10000,pidstr);
			myVar[g] = setTimeout(doNothing,10000,pidstr);
		}
	} 
	
}

function doNothing(id){
	console.log("----Doing  Nothing ---");
}
//statusCheck = setInterval(checkResults, 2000);
statusCheck = setTimeout(checkResults, 2000);
console.log('End of Code');

/*$.when.apply(null, promises).done(function() {
	console.log('All images uploaded', xMap.length);

	if (promises[0].status == 200) {
		var pid = promises[0].responseText;
		console.log('ResponseText:  ', pid);
	}
	console.log('promises: ', promises[0]);

	for ( var k = 0; k < xMap.length; k++) {
		var id = xMap[k].id;
		console.log(' for: id = ', id);
		myVar[k] = setInterval(checkfetch, 2000, id);

	}

	statusCheck = setInterval(checkResults, 2000);

	console.log('End of Code')

});*/
