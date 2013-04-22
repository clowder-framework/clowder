var fileId;

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
	UploadFile(f);
				    	//console.log("fileSelectHandlefileUploaded[]:",fileUploaded[i]);
//	}
	output.push('</table>');
	
	//document.getElementById('list').innerHTML = output.join('');
	tableinsert('list',output);
	//console.log(evt.dataTransfer.getData('File'));
	//f=evt.dataTransfer.getData('File');
	//console.log("file object",f,"f.name",f.name);
	//this.innerHTML += '<p>' + evt.dataTransfer.getData('File') + '</p>';
	//this.innerHTML += '<p>' + f.name + '</p>';
	//UploadFile(f);
	
	
	//this.innerHTML+='<input type="file" name="picture1" "hidden">';
	
  	
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
		//var File=file;
		fd.append("File",file);
		xhr.open("POST", "http://localhost:9000/uploaddnd", true);
		//xhr.setRequestHeader("Content-type","multipart/form-data");
		console.log("File ready to send ...");
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
 //console.log("File name: " + file.name);
 try {
   status = evt.target.status;
   readyState=evt.target.readyState;
 }
 catch(e) {
   return;
 }
 
 if (readyState==4){
	  if(status == 200) {
	   console.log("File: " + file.name);
	   var id=evt.target.responseText;
	  fileId=id;
	 // window.location.href=	"http://localhost:9000/files/"+id;
	  // window.location.href="http://localhost:9000/files/"+id+"/similar";
	            
	   }

   }
 
 /*function redirect(evt,id){
	 var status = 0;
	 var readyState=0;
	 //console.log("File name: " + file.name);
	 try {
	   status = evt.target.status;
	   readyState=evt.target.readyState;
	 }
	 catch(e) {
	   return;
	 }
	 
	 if (readyState==4){
		  if(status == 200) {
			  console.log(window.location.pathname);
			 // window.location.pathname="/files/"+id; 
				//  console.log(window.location.pathname);
			  window.location.href=	"http://localhost:9000/files/"+id;
			  //this.document.write(evt.target.responseText);
		  }
		  }
	 }*/
 
}


function $id(id) {
	return document.getElementById(id);
	}
function Output(msg,mgid) {
	//	var m = $id("messages");
	var m=$id(mgid);
		m.innerHTML = msg + m.innerHTML;
}
var submitBtn=document.getElementById('submit-button-id');
submitBtn.onclick=function(evt){
	window.location.href="http://localhost:9000/files/"+fileId+"/similar";
	
}


