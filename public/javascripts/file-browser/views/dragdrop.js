if (window.File && window.FileList && window.FileReader) {
	console.log("I am inside initialization code");
						
	Init();
}
				
// initialize
function Init() {
	//var files = $id("query"),
    var filedrag = $id("filedrag");
	console.log("I am inside Init()");
												 
	// file select
	//files.addEventListener("change", handleFileSelect, false);
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
		  
 var files  = evt.dataTransfer.files; // FileList object
 
 var output = [];	
			
 var f=files[0];


output.push('<table id="inputTable" class="table table-bordered table-striped" style="display:inline;"><th>File</th><th>Type</th>');

				      	output.push('<tr id=i >',
						      	'<td id="r"+i+"c3">', escape(f.name), '</td>',
						      	'<td></strong> (', f.type || 'n/a', ') </td>',
						      	//'<td id="'+escape(f.name)+'"><center><i class="icon-remove"></center></td>',
						      	'</tr>');
				    	//fd.append(f.name,f);
	//UploadFile(f);
				    	//console.log("fileSelectHandlefileUploaded[]:",fileUploaded[i]);
//	}
	output.push('</table>');
	
	//document.getElementById('list').innerHTML = output.join('');
	tableinsert('list',output);
	//console.log(evt.dataTransfer.getData('File'));
	//f=evt.dataTransfer.getData('File');
	console.log("file object",f,"f.name",f.name);
	//this.innerHTML += '<p>' + evt.dataTransfer.getData('File') + '</p>';
	this.innerHTML += '<p>' + f + '</p>';
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
		xhr.open("POST", "http://localhost:9000/upload1", true);
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



