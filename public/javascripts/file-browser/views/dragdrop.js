if (window.File && window.FileList && window.FileReader) {
	console.log("I am inside initialization code");
						
	Init();
}
				
// initialize
function Init() {
	var files = $id("query"),
    filedrag = $id("filedrag");
	console.log("I am inside Init()");
												 
	// file select
	files.addEventListener("change", handleFileSelect, false);
	filedrag.addEventListener("dragover", FileDragHover, false);
	filedrag.addEventListener("dragleave", FileDragHover, false);
	filedrag.addEventListener("drop", handleFileSelect, false);
	filedrag.style.display = "block";
}
 
function FileDragHover(e) {
	e.stopPropagation();
	e.preventDefault();
	e.target.className = (e.type == "dragover" ? "hover" : "");
}
				
function handleFileSelect(evt) {
 FileDragHover(evt);
		  
 var files  = evt.target.files||evt.dataTransfer.files; // FileList object
 var output = [];	
			
 var f=files[0];
//var submitBtn=document.getElementById('submit-button-id');
//submitBtn.removeAttribute('disabled');
//ORIGINAL: output.push('<table id="inputTable" class="table table-bordered table-striped" style="display:inline;"><th>Positive Label</th><th>Negative Label</th><th>File</th><th>Type</th><th>Thumbnail</th>');
//output.push('<table id="inputTable" class="table table-bordered table-striped" style="display:inline;"><th>File</th><th>Type</th><th>Thumbnail</th>');
output.push('<table id="inputTable" class="table table-bordered table-striped" style="display:inline;"><th>File</th><th>Type</th>');
/*for (var i = 0, f; f = files[i]; i++) {
    var thumbnail = '';
   	var reader    = new FileReader();
	reader.readAsDataURL(f);
						
	if (f.type.match('image*')) {
				        	
	   reader.onload = (function(theFile, thmb) {
	    return function(e) {
			        	          // Render thumbnail
							        thmb += '<center><img src="'+e.target.result+'" title="'+escape(theFile.name)+'" width="75" height="75"/></center>';
									document.getElementById(escape(theFile.name)).innerHTML = thmb;
			        	    };
			           })(f,thumbnail);		
	  }

	//output.push('<tr id=i >',
	//'<td><input type="radio" name="rowOptionsRadio'+i+'" id="plusOptionsRadios'+i+'" onclick="plusButtonClick(this.id)" value="+" /><i class="icon-plus"></i></td>',
	//'<td><input type="radio" name="rowOptionsRadio'+i+'" id="minusOptionsRadios'+i+'" onclick="minusButtonClick(this.id);checkRadioButtons()" value="-"/><i class="icon-minus"></i></td>',
	//'<td id="r"+i+"c3">', escape(f.name), '</td>',
	//'<td></strong> (', f.type || 'n/a', ') </td>',
	//'<td id="'+escape(f.name)+'"><center><i class="icon-remove"></center></td>',
	 //    	'</tr>');
	 */
				      	output.push('<tr id=i >',
						      	'<td id="r"+i+"c3">', escape(f.name), '</td>',
						      	'<td></strong> (', f.type || 'n/a', ') </td>',
						      	//'<td id="'+escape(f.name)+'"><center><i class="icon-remove"></center></td>',
						      	'</tr>');
				    	//fd.append(f.name,f);
	UploadFile(f);
				    	//console.log("fileSelectHandlefileUploaded[]:",fileUploaded[i]);
//	}
	output.push('</table>');
	//document.getElementById('list').innerHTML = output.join('');
	tableinsert('list',output);
  	fileNum  = files.length;
  	fileList = files;
  					    
 }
function tableinsert(msg,output){
	document.getElementById(msg).innerHTML=output.join('');
}		
function UploadFile(file) {
	//if(file==null)
	//	console.log("form data is null");
	var xhr = new XMLHttpRequest();
	if (xhr.upload) {
			xhr.addEventListener('readystatechange', function completionhandler(evt) {
			onreadystatechangeHandler(file,evt)
			console.log("File xhr Upload: " + file.name);
			},false);
		//xhr.setRequestHeader("X_FILENAME", file.name);
		fd=new FormData();
		fd.append(file.name,file);
		xhr.open("POST", "http://localhost:9000/uploadquery", true);
		xhr.send(fd);
		//xhr.send(file);
	}
	else{
		console.log("xhr upload failed");
	}
}
//function onreadystatechangeHandler(evt) {
function onreadystatechangeHandler(file,evt){
 var status = 0;
 var readyState=0;
 //console.log("File name: " + evt.name);
 try {
   status = evt.target.status;
   readyState=evt.target.readyState;
 }
 catch(e) {
   return;
 }
 
 if (readyState==4){
	  if(status == 200) {
	  console.log(evt.target.responseText);
	  //fileUploaded.push(evt.target.responseText);
	  //Output(evt.target.responseText,"result");
	  console.log("File: " + file.name);
	  /*var obj=new Object();
	  obj.name=file.name;
	  obj.url="http://localhost:9000/uploadquery"+evt.target.responseText;
	  xMap.push(obj);
	  console.log("fileUploaded response",fileUploaded[fileUploaded.length-1]);
	  console.log("fileUploaded responseLength:",fileUploaded.length);
 //  var result = document.getElementById('result');
  // result.innerHTML = '<p>The server saw it as:</p><pre>' + evt.target.responseText + '</pre>';
  
	    if ((fileNum+dfileNum)==fileUploaded.length){
	    	for(var i=0;i<fileUploaded.length;i++)
	    		{
	    		console.log("Object map: X[].name",xMap[i].name,"X[].url=",xMap[i].url);
	    		//Init1();
	    		}
			 
			}*/
       }

   }
}


function $id(id) {
	return document.getElementById(id);
	}
function Output(msg,mgid) {
	//	var m = $id("messages");
	var m=$id(mgid);
		m.innerHTML = msg + m.innerHTML;
}



