function clearConfigTab(prNum){
  	  $(window["configs" + prNum].tab + " > textarea").remove();
  	  $(window["configs" + prNum].tab + " > button").remove(); 
  	  $(window["configs" + prNum].tab + " > h5").remove();
  	  $(window["configs" + prNum].tab + " > span").remove();
  	  $(window["configs" + prNum].tab + " > br").remove();
    }
    
  function focusOnAnnotation(x_coord, y_coord, z_coord, event, prNum){	
  	if(window["showAnnotations" + prNum] && (window["isCurrentSubmitted" + prNum] || (x_coord != window["currentAnnotation" + prNum][0] || y_coord != window["currentAnnotation" + prNum][1] || z_coord != window["currentAnnotation" + prNum][2]))){  	  
  	  if(window["currentAnnotation" + prNum].length == 3){
  		  if(window["isCurrentSubmitted" + prNum])
  			  $("#x3dElement" + prNum + " > scene > transform[data-annotation][translation='" + window["currentAnnotation" + prNum][0] + "," + window["currentAnnotation" + prNum][1] + "," + window["currentAnnotation" + prNum][2] 
  			  + "'] > shape > appearance > material").get(0).setAttribute("diffuseColor", "mediumblue");
  		  else{
  			  $("#x3dElement" + prNum + " > scene > transform[data-annotation][translation='" + window["currentAnnotation" + prNum][0] + "," + window["currentAnnotation" + prNum][1] + "," + window["currentAnnotation" + prNum][2] + "']").remove();
  			  window["isCurrentSubmitted" + prNum] = true;
  		  }   
  		  clearConfigTab(prNum);		  
  	  }	  
  	  window["currentAnnotation" + prNum][0] = x_coord;
  	  window["currentAnnotation" + prNum][1] = y_coord;
  	  window["currentAnnotation" + prNum][2] = z_coord;
  	  
  	  $("#x3dElement" + prNum + " > scene > transform[data-annotation][translation='" + window["currentAnnotation" + prNum][0] + "," + window["currentAnnotation" + prNum][1] + "," + window["currentAnnotation" + prNum][2] 
  	  + "'] > shape > appearance > material").get(0).setAttribute("diffuseColor", "olivedrab");
  	  
        var annotationHeading = document.createElement('h5');
        annotationHeading.innerHTML = "Annotation description";
        $(window["configs" + prNum].tab).append(annotationHeading);

        var annotationDescription = document.createElement('span');
        annotationDescription.innerHTML = $("#x3dElement" + prNum + " > scene > transform[data-annotation][translation='" + window["currentAnnotation" + prNum][0] + "," + window["currentAnnotation" + prNum][1] + "," + window["currentAnnotation" + prNum][2] + "'] > .annotationdescription").get(0).innerHTML;
        annotationDescription.setAttribute("style","display:block; width:" + window["width" + prNum] + "px;");
        $(window["configs" + prNum].tab).append(annotationDescription);

        $(window["configs" + prNum].tab).append(document.createElement('br'));

        var annotationEdit = document.createElement('button'); 	
        annotationEdit.setAttribute('type','button');		
        annotationEdit.setAttribute("onclick","editAnnotationDescription('" + prNum + "')");
        annotationEdit.innerHTML = 'Edit';
        annotationEdit.setAttribute("style","margin-bottom: 5px;");
        $(window["configs" + prNum].tab).append(annotationEdit);
        
        event.cancelBubble = true;
        event.stopPropagation();
      }
    }   
  	
    function createAnnotationPoint(x_coord, y_coord, z_coord, descriptionText, prNum){
  	  window["annotationsCount"+prNum]++; 

  	  var annotation = document.createElement('transform');
  	  annotation.setAttribute("id", "annotation_" + window["annotationsCount"+prNum]);
  	  annotation.setAttribute("DEF", "annot_" + window["annotationsCount"+prNum]);
  	  annotation.setAttribute("scale", ".15 .15 .15");
  	  annotation.setAttribute("render", "true");
  	  annotation.setAttribute("bboxCenter", "0,0,0");
  	  annotation.setAttribute("bboxSize", "-1,-1,-1");
  	  annotation.setAttribute("center", "0,0,0");
  	  annotation.setAttribute("translation", x_coord + "," + y_coord + "," + z_coord);
  	  annotation.setAttribute("rotation", "0,0,0,0");
  	  annotation.setAttribute("scaleOrientation", "0,0,0,0");
  	  annotation.setAttribute("data-annotation", "true");  
  	  document.getElementById("x3dElement" + prNum).getElementsByTagName('scene')[0].appendChild(annotation);  
  	  
  	  var annotationShape = document.createElement('shape');
  	  annotationShape.setAttribute("render", "true");
  	  annotationShape.setAttribute("bboxCenter", "0,0,0");
  	  annotationShape.setAttribute("bboxSize", "-1,-1,-1");
  	  annotationShape.setAttribute("isPickable", "false");
  	  annotation.appendChild(annotationShape);
  	  
  	  var appearance = document.createElement('appearance');
  	  appearance.setAttribute("sortType", "auto");
  	  annotationShape.appendChild(appearance);
  	  
  	  var material = document.createElement('material');
  	  material.setAttribute("diffuseColor", "mediumblue");
  	  material.setAttribute("specularColor", "peachpuff");
  	  material.setAttribute("ambientIntensity", "0.2");
  	  material.setAttribute("emissiveColor", "0,0,0");
  	  material.setAttribute("shininess", "0.2");
  	  if(window["showAnnotations"+prNum])
  		  material.setAttribute("transparency", "0");
  	  else
  		  material.setAttribute("transparency", "1");	  
  	  appearance.appendChild(material);
  	  
  	  var sphere = document.createElement('sphere');
  	  sphere.setAttribute("solid", "true");
  	  sphere.setAttribute("ccw", "true");
  	  sphere.setAttribute("useGeoCache", "true");
  	  sphere.setAttribute("radius", "1");
  	  sphere.setAttribute("subdivision", "24,24");
  	  annotationShape.appendChild(sphere);
  	  
  	  var description = document.createElement('span');
  	  description.setAttribute("style","display:none;");
  	  description.classList.add('annotationdescription');
  	  description.innerHTML = descriptionText;
  	  annotation.appendChild(description);  	  
    }
    
    function submitAnnotationEdit(prNum){	  
  	  var annotation = {};
  	  annotation['x_coord'] = "" + window["currentAnnotation" + prNum][0];
  	  annotation['y_coord'] = "" + window["currentAnnotation" + prNum][1];
  	  annotation['z_coord'] = "" + window["currentAnnotation" + prNum][2];
  	  annotation['description'] = $(window["configs" + prNum].tab + " > textarea").get(0).value;
  	  
  	  var request = $.ajax({
  	       type: 'POST',
  	       async:false,
  	       url: window["configs" + prNum].annotationsEditPath,
  	       data: JSON.stringify(annotation),
  	       contentType: "application/json",
  	     });
  	  
  		  request.done(function (response, textStatus, jqXHR){
  		        console.log("Response " + response);		        
  		        $("#x3dElement" + prNum + " > scene > transform[data-annotation][translation='" + window["currentAnnotation" + prNum][0] + "," + window["currentAnnotation" + prNum][1] + "," + window["currentAnnotation" + prNum][2] + "'] > .annotationdescription").get(0).innerHTML = $(window["configs" + prNum].tab + " > textarea").get(0).value;

  		        clearConfigTab(prNum);
  		  	  	
  		        var newAnnotationHeading = document.createElement('h5');
  		        newAnnotationHeading.innerHTML = "Annotation description";
  		        $(window["configs" + prNum].tab).append(newAnnotationHeading);

  		        var newAnnotationDescription = document.createElement('span');
  		        newAnnotationDescription.innerHTML = annotation['description'];
  		        newAnnotationDescription.setAttribute("style","display:block; width:" + window["width" + prNum] + "px;");
  		        $(window["configs" + prNum].tab).append(newAnnotationDescription);

  		        $(window["configs" + prNum].tab).append(document.createElement('br'));

  		        var newAnnotationEdit = document.createElement('button'); 	
  		        newAnnotationEdit.setAttribute('type','button');		
  		        newAnnotationEdit.setAttribute("onclick","editAnnotationDescription('" + prNum + "')");
  		        newAnnotationEdit.innerHTML = 'Edit';
  		        newAnnotationEdit.setAttribute("style","margin-bottom: 5px;");
  		        $(window["configs" + prNum].tab).append(newAnnotationEdit);	
  		  	    		        
  			});
  		 
  		  request.fail(function (jqXHR, textStatus, errorThrown){
  			console.error(
  	 		"The following error occured: "+
  	 		textStatus, errorThrown		            
  				);
  			alert("ERROR: " + errorThrown +". Annotation edit not submitted." );
  			});
    }
    
    function cancelAnnotationEdit(prNum){
  	  	clearConfigTab(prNum);
  	  
        var annotationHeading = document.createElement('h5');
        annotationHeading.innerHTML = "Annotation description";
        $(window["configs" + prNum].tab).append(annotationHeading);

        var annotationDescription = document.createElement('span');
        annotationDescription.innerHTML = $("#x3dElement" + prNum + " > scene > transform[data-annotation][translation='" + window["currentAnnotation" + prNum][0] + "," + window["currentAnnotation" + prNum][1] + "," + window["currentAnnotation" + prNum][2] + "'] > .annotationdescription").get(0).innerHTML;
        annotationDescription.setAttribute("style","display:block; width:" + window["width" + prNum] + "px;");
        $(window["configs" + prNum].tab).append(annotationDescription);

        $(window["configs" + prNum].tab).append(document.createElement('br'));

        var annotationEdit = document.createElement('button'); 	
        annotationEdit.setAttribute('type','button');		
        annotationEdit.setAttribute("onclick","editAnnotationDescription('" + prNum + "')");
        annotationEdit.innerHTML = 'Edit';
        annotationEdit.setAttribute("style","margin-bottom: 5px;");
        $(window["configs" + prNum].tab).append(annotationEdit);	
    }
    
    function editAnnotationDescription(prNum){
  	  clearConfigTab(prNum);
  	  
  	  var editAnnotationHeading = document.createElement('h5');
  	  editAnnotationHeading.innerHTML = "Edit annotation description";
  	  $(window["configs" + prNum].tab).append(editAnnotationHeading);
  	  
  	  var editAnnotationTextBox = document.createElement('textarea');
  	  editAnnotationTextBox.setAttribute("rows", "4");
  	  editAnnotationTextBox.setAttribute("style","width:" + (window["width" + prNum] - 15) + "px;");
  	  editAnnotationTextBox.value = $("#x3dElement" + prNum + " > scene > transform[data-annotation][translation='" + window["currentAnnotation" + prNum][0] + "," + window["currentAnnotation" + prNum][1] + "," + window["currentAnnotation" + prNum][2] + "'] > .annotationdescription").get(0).innerHTML;
  	  $(window["configs" + prNum].tab).append(editAnnotationTextBox);
  	  
  	  $(window["configs" + prNum].tab).append(document.createElement('br'));
  	  
  	  var editAnnotationSubmit = document.createElement('button'); 	
  	  editAnnotationSubmit.setAttribute('type','button');		
  	  editAnnotationSubmit.setAttribute('style','margin-right:10px;');
  	  editAnnotationSubmit.setAttribute("onclick","submitAnnotationEdit('" + prNum + "')");
  	  editAnnotationSubmit.innerHTML = 'OK';
  	  editAnnotationSubmit.setAttribute("style","margin-bottom: 5px;");
  	  $(window["configs" + prNum].tab).append(editAnnotationSubmit);
  	  
  	  var editAnnotationCancel = document.createElement('button'); 	
  	  editAnnotationCancel.setAttribute('type','button');		
  	  editAnnotationCancel.setAttribute("onclick","cancelAnnotationEdit('" + prNum + "')");
  	  editAnnotationCancel.innerHTML = 'Cancel';
  	  editAnnotationCancel.setAttribute("style","margin-bottom: 5px;");
  	  $(window["configs" + prNum].tab).append(editAnnotationCancel);
  	  
    }
    
    function submitAnnotation(prNum){
  	  var annotation = {};
  	  annotation['x_coord'] = "" + window["currentAnnotation" + prNum][0];
  	  annotation['y_coord'] = "" + window["currentAnnotation" + prNum][1];
  	  annotation['z_coord'] = "" + window["currentAnnotation" + prNum][2];
  	  annotation['description'] = $(window["configs" + prNum].tab + " > textarea").get(0).value;
  	  var request = $.ajax({
  	       type: 'POST',
  	       async:false,
  	       url: window["configs" + prNum].annotationsAttachPath,
  	       data: JSON.stringify(annotation),
  	       contentType: "application/json",
  	     });    

  		  request.done(function (response, textStatus, jqXHR){
  		        console.log("Response " + response);		        
  		        $("#x3dElement" + prNum + " > scene > transform[data-annotation][translation='" + window["currentAnnotation" + prNum][0] + "," + window["currentAnnotation" + prNum][1] + "," + window["currentAnnotation" + prNum][2] + "'] > .annotationdescription").get(0).innerHTML = $(window["configs" + prNum].tab + " > textarea").get(0).value;
  		        window["isCurrentSubmitted" + prNum] = true;
  		        
  		        clearConfigTab(prNum);
  		  	  	
  		  	    if(window["showAnnotations" + prNum]){
  		  		  var newAnnotationHeading = document.createElement('h5');
  		  		  newAnnotationHeading.innerHTML = "Annotation description";
  		  		  $(window["configs" + prNum].tab).append(newAnnotationHeading);
  		  		  
  		  		  var newAnnotationDescription = document.createElement('span');
  		  		  newAnnotationDescription.innerHTML = annotation['description'];
  		  		  newAnnotationDescription.setAttribute("style","display:block; width:" + window["width" + prNum] + "px;");
  		  		  $(window["configs" + prNum].tab).append(newAnnotationDescription);
  		  		  
  		  		  $(window["configs" + prNum].tab).append(document.createElement('br'));
  		  		  
  		  		  var newAnnotationEdit = document.createElement('button'); 	
  		  		  newAnnotationEdit.setAttribute('type','button');		
  		  		  newAnnotationEdit.setAttribute("onclick","editAnnotationDescription('" + prNum + "')");
  		  		  newAnnotationEdit.innerHTML = 'Edit';
  		  		  newAnnotationEdit.setAttribute("style","margin-bottom: 5px;");
  		  		  $(window["configs" + prNum].tab).append(newAnnotationEdit);	
  		  	    }else{
  		  	    	$("#x3dElement" + prNum + " > scene > transform[data-annotation][translation='" + window["currentAnnotation" + prNum][0] + "," + window["currentAnnotation" + prNum][1] + "," + window["currentAnnotation" + prNum][2] 
  		  	    	+ "'] > shape > appearance > material").get(0).setAttribute("diffuseColor", "mediumblue");
  		  	    	window["currentAnnotation" + prNum] = new Array();
  		  	    }		        
  			});   
  		 
  		  request.fail(function (jqXHR, textStatus, errorThrown){
     		console.error(
         		"The following error occured: "+
         		textStatus, errorThrown		            
     			);
     		alert("ERROR: " + errorThrown +". Annotation not submitted." );
  			});	  
    }   
    
    function cancelNewAnnotation(prNum){
  	  $("#x3dElement" + prNum + " > scene > transform[data-annotation][translation='" + window["currentAnnotation" + prNum][0] + "," + window["currentAnnotation" + prNum][1] + "," + window["currentAnnotation" + prNum][2] + "']").remove();
  	  window["isCurrentSubmitted" + prNum] = true;
  	  window["currentAnnotation" + prNum] = new Array();
  	  clearConfigTab(prNum);	  
    }
  	
    function addAnnotation(event, prNum, isShiftClicked){
  	  if(isShiftClicked){
  		  clearConfigTab(prNum);

  		  if(window["currentAnnotation" + prNum].length == 3)
  			  if(window["isCurrentSubmitted" + prNum])
  				  $("#x3dElement" + prNum + " > scene > transform[data-annotation][translation='" + window["currentAnnotation" + prNum][0] + "," + window["currentAnnotation" + prNum][1] + "," + window["currentAnnotation" + prNum][2] 
  				  + "'] > shape > appearance > material").get(0).setAttribute("diffuseColor", "mediumblue");
  			  else{
  				  $("#x3dElement" + prNum + " > scene > transform[data-annotation][translation='" + window["currentAnnotation" + prNum][0] + "," + window["currentAnnotation" + prNum][1] + "," + window["currentAnnotation" + prNum][2] + "']").remove();
  				  isCurrentSubmitted = true;
  			  }
  		  
  		  window["currentAnnotation" + prNum][0] = event.hitPnt[0];
  		  window["currentAnnotation" + prNum][1] = event.hitPnt[1];
  		  window["currentAnnotation" + prNum][2] = event.hitPnt[2];
  		  
  		  createAnnotationPoint(window["currentAnnotation" + prNum][0], window["currentAnnotation" + prNum][1], window["currentAnnotation" + prNum][2], "", prNum);
  		  
  		  $("#x3dElement" + prNum + " > scene > transform[data-annotation][translation='" + window["currentAnnotation" + prNum][0] + "," + window["currentAnnotation" + prNum][1] + "," + window["currentAnnotation" + prNum][2] 
  		  + "'] > shape > appearance > material").get(0).setAttribute("diffuseColor", "olivedrab");
  		  
  		  window["isCurrentSubmitted" + prNum] = false;
  		  
  		  var newAnnotationHeading = document.createElement('h5');
  		  newAnnotationHeading.innerHTML = "Enter annotation description";
  		  $(window["configs" + prNum].tab).append(newAnnotationHeading);
  		  
  		  var newAnnotationTextBox = document.createElement('textarea');
  		  newAnnotationTextBox.setAttribute("rows", "4");
  		  newAnnotationTextBox.setAttribute("style","width:" + (window["width" + prNum] - 15) + "px;");
  		  $(window["configs" + prNum].tab).append(newAnnotationTextBox);
  		  
  		  $(window["configs" + prNum].tab).append(document.createElement('br'));
  		  
  		  var newAnnotationSubmit = document.createElement('button'); 	
  		  newAnnotationSubmit.setAttribute('type','button');		
  		  newAnnotationSubmit.setAttribute('style','margin-right:10px;');
  		  newAnnotationSubmit.setAttribute("onclick","submitAnnotation('" + prNum + "')");
  		  newAnnotationSubmit.innerHTML = 'OK';
  		  newAnnotationSubmit.setAttribute("style","margin-bottom: 5px;");
  		  $(window["configs" + prNum].tab).append(newAnnotationSubmit);
  		  
  		  var newAnnotationCancel = document.createElement('button'); 	
  		  newAnnotationCancel.setAttribute('type','button');		
  		  newAnnotationCancel.setAttribute("onclick","cancelNewAnnotation('" + prNum + "')");
  		  newAnnotationCancel.innerHTML = 'Cancel annotation addition';
  		  newAnnotationCancel.setAttribute("style","margin-bottom: 5px;");
  		  $(window["configs" + prNum].tab).append(newAnnotationCancel);
  		  
  	      event.cancelBubble = true;
  	      event.stopPropagation();
  	  }	
    }
    
    function handleObjectClick(event, prNum){
    	var isShiftClicked = window["isShiftClicked" + prNum];
    	var annotationsTranslations = [];    	
    	$("#x3dElement" + prNum + " > scene > transform[data-annotation]").each(function(){
    		annotationsTranslations.push($(this).attr("translation").split(","));
    	});
    	for(var j = 0; j < annotationsTranslations.length; j++){
    		if(Math.abs(annotationsTranslations[j][0]-event.hitPnt[0])+Math.abs(annotationsTranslations[j][1]-event.hitPnt[1])+Math.abs(annotationsTranslations[j][2]-event.hitPnt[2]) <= 0.25){
    			focusOnAnnotation(annotationsTranslations[j][0], annotationsTranslations[j][1], annotationsTranslations[j][2], event, prNum);
    			return;
    		}
    	}  	
    	addAnnotation(event, prNum, isShiftClicked);
    }

(function ($, Configuration) {

  console.log("X3D previewer for " + Configuration.id);
  
  var fileUrl = "http://" + Configuration.hostIp + ":" + window.location.port + Configuration.url;
  var pathJs = "http://" + Configuration.hostIp + ":" + window.location.port + Configuration.jsPath + "/";
  
  var width = 750;
  var height = 550;
  
  var prNum = Configuration.tab.replace("#previewer","");
  
  window["showAnnotations" + prNum] = false; 
  window["currentAnnotation" + prNum] = new Array(); 
  window["isCurrentSubmitted" + prNum] = true;
  window["annotationsCount" + prNum] = 0;
  window["isStatisticsExtended" + prNum] = false;
  window["isShiftClicked" + prNum] = false;
  window["configs" + prNum] = Configuration;
  window["width" + prNum] = width;
  
  $(Configuration.tab).append("<table id='x3dElementTable" + prNum + "' style ='margin-bottom:550px;'><tr><td>Left mouse button drag</td><td>&nbsp;&nbsp;&nbsp;&nbsp;Rotate</td></tr>"
		  					+ "<tr><td>Left mouse button + Ctrl drag</td><td>&nbsp;&nbsp;&nbsp;&nbsp;Pan</td></tr>"
		  					+ "<tr><td>Right mouse button drag / Left mouse button + Alt drag</td><td>&nbsp;&nbsp;&nbsp;&nbsp;Zoom</td></tr>"
		  					+ "<tr><td>m button</td><td>&nbsp;&nbsp;&nbsp;&nbsp;Change rendering (regular-vertices-wireframe)</td></tr>"
		  					+ "<tr><td>Shift + Left mouse button</td><td>&nbsp;&nbsp;&nbsp;&nbsp;Add annotation</td></tr>"
		  					+ "<tr><td>Q</td><td>&nbsp;&nbsp;&nbsp;&nbsp;Toggle annotations visibility</td></tr>"
		  					+ "<tr><td></td><td>&nbsp;&nbsp;&nbsp;&nbsp;</td></tr>"
		  					+ "</table>");
  
  var inner = "<x3d id='x3dElement" + prNum + "' showStat='false' showLog='true' height='" + height + "px' width='" + width + "px' x='0px' y='0px' style='position:absolute;top:" + ($(Configuration.tab).offset().top + 148) + "px;' >";

  $.ajax({
	    url: fileUrl,
	    async:false,
	    success: function (data) {
	    	inner = inner + data;
	    	 },
	    dataType: 'text'
	});
  inner = inner + "</x3d>";
  
  $(Configuration.tab).append(inner);
  
  $.ajax({
      url: Configuration.annotationsListPath,
      async:false,
      success: function (json) {   	  
    	  for(var i = 0; i < json.length; i++)
    		  createAnnotationPoint(json[i].x_coord,json[i].y_coord,json[i].z_coord,json[i].description, prNum);   	    	    		  
	  },
      dataType: "json"
    });

  $("#x3dElement" + prNum + " > scene > shape > indexedfaceset").attr("DEF","model");
  $("#x3dElement" + prNum + " > scene > shape > indexedfaceset").attr("solid","true");
  $("#x3dElement" + prNum + " > scene > shape > indexedfaceset").attr("onclick","handleObjectClick(event,'" + prNum + "');");
    
  var s = document.createElement("script");
  s.type = "text/javascript";
  s.src = pathJs + "x3dom.js";
  console.log("Updating tab " + Configuration.tab);
  $(Configuration.tab).append(s);
  
  $("body").on('keypress','#x3dElement' + prNum,function(e){
	  if(e.which == 113){
		  if(window["showAnnotations" + prNum]){
			  $("#x3dElement" + prNum + " > scene > transform[data-annotation]" 
			  + " > shape > appearance > material").attr("transparency", "1");
			  if(window["currentAnnotation" + prNum].length == 3){
				  if(window["isCurrentSubmitted" + prNum])
					  $("#x3dElement" + prNum + " > scene > transform[data-annotation][translation='" + window["currentAnnotation" + prNum][0] + "," + window["currentAnnotation" + prNum][1] + "," + window["currentAnnotation" + prNum][2] 
					  + "'] > shape > appearance > material").get(0).setAttribute("diffuseColor", "mediumblue");
				  else{
					  $("#x3dElement" + prNum + " > scene > transform[data-annotation][translation='" + window["currentAnnotation" + prNum][0] + "," + window["currentAnnotation" + prNum][1] + "," + window["currentAnnotation" + prNum][2] + "']").remove();
					  window["isCurrentSubmitted" + prNum] = true;
				  }   
				  clearConfigTab(prNum);
				  window["currentAnnotation" + prNum] = new Array();
			  }
			  window["showAnnotations" + prNum] = false;
		  }else{
			  $("#x3dElement" + prNum + " > scene > transform[data-annotation]" 
			  + " > shape > appearance > material").attr("transparency", "0");
			  
			  window["showAnnotations" + prNum] = true; 
		  }		  
		  
		  return false;
	  }
	  else if(e.which == 32){
		  if(window["isStatisticsExtended" + prNum]){
			  $("#x3dElementTable" + prNum).css("margin-bottom","-=160");
			  window["isStatisticsExtended" + prNum] = false;
		  }
		  else{
			  $("#x3dElementTable" + prNum).css("margin-bottom","+=160");
			  window["isStatisticsExtended" + prNum] = true;
		  }
	  }
  });
  
  $("body").on('keydown',function(e){
	  if(e.which == 16){
		  window["isShiftClicked" + prNum] = true;
	  }
  });  
  $("body").on('keyup',function(e){
	  if(e.which == 16){
		  window["isShiftClicked" + prNum] = false;
	  }
  });
  

}(jQuery, Configuration));