String.prototype.endsWith = function(str) 
{return (this.match(str+"$")==str)}

//CSV file format: Node and whether intermediate node or leaf.
	var allowedNodes = new Array();
	if(searchFor == "userMetadata"){
		$.ajax({
		       url: modelIp + '/user_metadata_model_allowedNodes.txt',
		       async:false,
			   success: function (data){
			   		var allowedNodesLines = data.split(/\r\n|\n/);
					for(var i = 0; i < allowedNodesLines.length; i++){
						allowedNodes[i] = allowedNodesLines[i].split(',');
					}
			   },
		       dataType: "text"
		     });
	}
		 
	//CSV file format: Node,Child, Minimum child count, Maximum child count.	
	var allowedChildren = new Array();
	if(searchFor == "userMetadata"){
		$.ajax({
		       url: modelIp + '/user_metadata_model_allowedRelationships.txt',
		       async:false,
			   success: function (data){
			   		var allowedChildrenLines = data.split(/\r\n|\n/);
					for(var i = 0; i < allowedChildrenLines.length; i++){
						allowedChildren[i] = allowedChildrenLines[i].split(',');
					}
			   },
		       dataType: "text"
		     });
	}
				
	//Counter for DOM node uniqueness.
	var elementCounter = 1;
	
	var currentFirst = 1;
	var searchResultsCount = 0;
	
$(function() {
		
		$('body').on('click','.usr_md_,.usr_md_submit',function(e){
			if($(this).is('button')){				
				   if($(this).html() == "Modify"){			  
					var textBox = document.createElement('input');
					textBox.classList.add('usr_md_');		   
					textBox.setAttribute('type', 'text');
					var fullTextValue = $(this).parent().children("span").get(0).innerHTML;
					textBox.value = fullTextValue.replace(" IGNORE CASE", "").replace(" ANYWHERE", "");
					   
					var toRemove = $(this).parent().children("span").get(0);
					
					$(this).parent().get(0).insertBefore(textBox, toRemove);					
										
					var newPropertyIgnoreCaseBox = document.createElement('input');
					newPropertyIgnoreCaseBox.classList.add('usr_md_');								   
					newPropertyIgnoreCaseBox.setAttribute('type', 'checkbox');
					newPropertyIgnoreCaseBox.setAttribute('value', 'not');
					newPropertyIgnoreCaseBox.checked=(fullTextValue.indexOf(" IGNORE CASE") > -1);
					
					var newPropertyIgnoreCaseBoxText = document.createElement('span');
					newPropertyIgnoreCaseBoxText.classList.add('usr_md_');
					newPropertyIgnoreCaseBoxText.innerHTML = " Ignore case";
					
					$(this).parent().get(0).insertBefore(newPropertyIgnoreCaseBox, toRemove);
					$(this).parent().get(0).insertBefore(newPropertyIgnoreCaseBoxText, toRemove);
					
					var newPropertyAnywhereBox = document.createElement('input');
					newPropertyAnywhereBox.classList.add('usr_md_');								   
					newPropertyAnywhereBox.setAttribute('type', 'checkbox');
					newPropertyAnywhereBox.setAttribute('value', 'not');
					newPropertyAnywhereBox.checked=(fullTextValue.indexOf(" ANYWHERE") > -1);
					
					var newPropertyAnywhereBoxText = document.createElement('span');
					newPropertyAnywhereBoxText.classList.add('usr_md_');
					newPropertyAnywhereBoxText.innerHTML = " Anywhere in value";
					
					$(this).parent().get(0).insertBefore(newPropertyAnywhereBox, toRemove);
					$(this).parent().get(0).insertBefore(newPropertyAnywhereBoxText, toRemove);
					
					$(toRemove).remove();
					   
					$(this).html("Ok");							   		   			   
				  }
				  else if($(this).html() == "Ok"){
				  	var textSpan = document.createElement('span');
					textSpan.classList.add('usr_md_');
					textSpan.innerHTML = $(this).parent().children("input").get(0).value.trim();
					
					if($(this).parent().children("input").get(1).checked)
						textSpan.innerHTML = textSpan.innerHTML + " IGNORE CASE";
					if($(this).parent().children("input").get(2).checked)
						textSpan.innerHTML = textSpan.innerHTML + " ANYWHERE";
					
					$(this).parent().children("span").remove();
						
					$(this).parent().get(0).insertBefore(textSpan, $(this).parent().children("input").get(0));
					
					$(this).parent().children("input").remove();
						 
					$(this).html("Modify");			
				  }			  
				  else if($(this).html() == "Delete"){
				  	$(this).parent().parent().get(0).removeChild($(this).parent().get(0));
				  }
				  else if($(this).html() == "Delete disjunction"){
					//Start deleting properties from the start of the selected disjunction, ie where the selected OR is  
					for(var i = $(this).parent().index() + 1; i < $(this).parent().parent().children('li').length; ){
						var ithSibling = $(this).parent().parent().children('li:nth-child(' + (i+1) + ')');
						//If start of next disjunction found, stop deleting properties. 
						if(ithSibling.children('b').length > 0)
							if(ithSibling.children('b').get(0).textContent == "OR")
								break;
						$(this).parent().parent().get(0).removeChild(ithSibling.get(0));																	
					}   				  
					$(this).parent().parent().get(0).removeChild($(this).parent().get(0));   
				  } 
				  else if($(this).html() == "Add property"){				  
				  	var newProperty = document.createElement("li");
					newProperty.classList.add('usr_md_');
					
					//
					var newPropertyMenu;
					if(searchFor == "userMetadata")
						newPropertyMenu = document.createElement("select");
					else{
						newPropertyMenu = document.createElement("input");
						newPropertyMenu.setAttribute('type', 'text');
					}
					newPropertyMenu.classList.add('usr_md_');
					//
					
					var newPropertyNotBox = document.createElement('input');
					newPropertyNotBox.classList.add('usr_md_');								   
					newPropertyNotBox.setAttribute('type', 'checkbox');
					newPropertyNotBox.setAttribute('value', 'not');
					newPropertyNotBox.checked=false;
					
					var newPropertyNotBoxText = document.createElement('span');
					newPropertyNotBoxText.classList.add('usr_md_');
					newPropertyNotBoxText.innerHTML = " NOT";
										
					var parentNodeType = "";
					if($(this).parent().is('div')){
						parentNodeType = "!root!";
					}
					else{
						parentNodeType = $(this).parent().children('b').get(0).textContent;
						if(parentNodeType.endsWith(":"))
							parentNodeType = parentNodeType.substring(0, parentNodeType.length - 1); 
					}					
					if(parentNodeType != "OR"){
						if(parentNodeType.length >= 4)
									if(parentNodeType.substring(0,4) == "NOT ")
										parentNodeType = parentNodeType.substring(4);
																
						var allowedChildrenForNode = allowedChildren.filter(function (a) {return a[0] == parentNodeType;});
						if(allowedChildrenForNode.length == 0 && searchFor == "userMetadata"){
							notify("The metadata model states that this property cannot have subproperties of any kind.", "error");
							return false;
						}
						
						//Position the new property just before the start of the next disjunction, if any.
						var siblings = $(this).parent().children('ul').children('li');
						var positionOfNew = siblings.length;
						for( var i = 0; i < siblings.length; i++){
							var ithSibling = $(this).parent().children('ul').children('li:nth-child(' + (i+1) + ')');
							if(ithSibling.children('b').length > 0)
								if(ithSibling.children('b').get(0).textContent == "OR"){
									positionOfNew = i;
									break;
								}
						}
						if(positionOfNew == siblings.length)				
							$(this).parent().children('ul')[0].appendChild(newProperty);
						else{
							$(this).parent().children('ul').children('li:nth-child(' + (positionOfNew+1) + ')').before(newProperty);
						}	
					}
					else{   
						if($(this).parent().parent().parent().is('div')){
							parentNodeType = "!root!";
						}
						else{
							parentNodeTypeText = $(this).parent().parent().parent().children('b').get(0).textContent;
							parentNodeType = parentNodeTypeText.substring(0, parentNodeTypeText.length - 1);
						}
					
						var allowedChildrenForNode = allowedChildren.filter(function (a) {return a[0] == parentNodeType;});
						if(allowedChildrenForNode.length == 0 && searchFor == "userMetadata"){
							notify("The metadata model states that this property cannot have subproperties of any kind.", "error");
							return false;
						}
						
						//Position the new property just before the start of the next disjunction, if any.
						var siblings = $(this).parent().parent().children('li');
						var positionOfNew = siblings.length;
						for( var i = $(this).parent().parent().children('li').index($(this).parent()) + 1; i < siblings.length; i++){
							var ithSibling = $(this).parent().parent().children('li:nth-child(' + (i+1) + ')');
							if(ithSibling.children('b').length > 0)
								if(ithSibling.children('b').get(0).textContent == "OR"){
									positionOfNew = i;
									break;
								}
						}
						if(positionOfNew == siblings.length)
							$(this).parent().parent().get(0).appendChild(newProperty);
						else{
							$(this).parent().parent().children('li:nth-child(' + (positionOfNew+1) + ')').before(newProperty);
						}								
					}
					for( var i = 0; i < allowedChildrenForNode.length; i++){
						var newOption = document.createElement("option");
						newOption.classList.add('usr_md_');					
						newOption.setAttribute('value', allowedChildrenForNode[i][1]);
						newOption.innerHTML = allowedChildrenForNode[i][1];
						if(i == 0){
							newOption.setAttribute('selected', 'selected');
						}
							
						newPropertyMenu.appendChild(newOption);					
					}
					newProperty.appendChild(newPropertyMenu);
					newProperty.appendChild(newPropertyNotBox);
					newProperty.appendChild(newPropertyNotBoxText);
						
					if(searchFor == "userMetadata"){
						var newSelectButton = document.createElement('button'); 	
						newSelectButton.classList.add('usr_md_');
						newSelectButton.setAttribute('type','button');		
							
						newSelectButton.innerHTML = 'Select property';
						newProperty.appendChild(newSelectButton);
					}
					else{
						var newSelectButton = document.createElement('button'); 	
						newSelectButton.classList.add('usr_md_');
						newSelectButton.setAttribute('type','button');		
							
						newSelectButton.innerHTML = 'Select node';
						newProperty.appendChild(newSelectButton);
						
						newSelectButton = document.createElement('button'); 	
						newSelectButton.classList.add('usr_md_');
						newSelectButton.setAttribute('type','button');		
							
						newSelectButton.innerHTML = 'Select leaf';
						newProperty.appendChild(newSelectButton);
					}
					
					
				  }
				  else if($(this).html() == "Select property"){				  	
				  				
					var selectTag = $(this).parent().children('select')[0];
					var selectedProperty = selectTag.options[selectTag.selectedIndex].value;
					var selectedPropertyType = "Node";				
					for(var i = 0; ; i++){
						if(allowedNodes[i][0] == selectedProperty){
							selectedPropertyType = allowedNodes[i][1];
							break;
						}
					}
					var isNot = "";
					if($(this).parent().children('input').get(0).checked == 1)
						isNot = "NOT ";

					$(this).parent().children('select').remove();
					$(this).parent().children('input').remove();
					$(this).parent().children('span').remove();

					var newPropertyKey = document.createElement('b');

					newPropertyKey.classList.add('usr_md_');
					newPropertyKey.innerHTML = isNot + selectedProperty + ":";
					$(this).parent().get(0).insertBefore(newPropertyKey, $(this).get(0));						
					if(selectedPropertyType == "String"){										
						var textBox = document.createElement('input');
						textBox.classList.add('usr_md_');
									   
						textBox.setAttribute('type', 'text');
						textBox.textContent = "";
						$(this).parent().get(0).insertBefore(textBox, $(this).get(0));
						
						var newPropertyIgnoreCaseBox = document.createElement('input');
						newPropertyIgnoreCaseBox.classList.add('usr_md_');								   
						newPropertyIgnoreCaseBox.setAttribute('type', 'checkbox');
						newPropertyIgnoreCaseBox.setAttribute('value', 'not');
						newPropertyIgnoreCaseBox.checked=true;
						
						var newPropertyIgnoreCaseBoxText = document.createElement('span');
						newPropertyIgnoreCaseBoxText.classList.add('usr_md_');
						newPropertyIgnoreCaseBoxText.innerHTML = " Ignore case";
						
						$(this).parent().get(0).insertBefore(newPropertyIgnoreCaseBox, $(this).get(0));
						$(this).parent().get(0).insertBefore(newPropertyIgnoreCaseBoxText, $(this).get(0));
						
						var newPropertyAnywhereBox = document.createElement('input');
						newPropertyAnywhereBox.classList.add('usr_md_');								   
						newPropertyAnywhereBox.setAttribute('type', 'checkbox');
						newPropertyAnywhereBox.setAttribute('value', 'not');
						newPropertyAnywhereBox.checked=true;
						
						var newPropertyAnywhereBoxText = document.createElement('span');
						newPropertyAnywhereBoxText.classList.add('usr_md_');
						newPropertyAnywhereBoxText.innerHTML = " Anywhere in value";
						
						$(this).parent().get(0).insertBefore(newPropertyAnywhereBox, $(this).get(0));
						$(this).parent().get(0).insertBefore(newPropertyAnywhereBoxText, $(this).get(0));
						
						$(this).html("Ok");
						
						var newDeleteButton = document.createElement('button'); 	
						newDeleteButton.classList.add('usr_md_');
						newDeleteButton.setAttribute('type','button');	
						
						newDeleteButton.innerHTML = 'Delete';
						$(this).parent().get(0).appendChild(newDeleteButton);
					}
					else{    
						$(this).html("Add property");
						var newDeleteButton = document.createElement('button'); 	
						newDeleteButton.classList.add('usr_md_');
						newDeleteButton.setAttribute('type','button');	
						
						newDeleteButton.innerHTML = 'Delete';
						$(this).parent().get(0).appendChild(newDeleteButton);
						
						var newPropertyList = document.createElement('ul');
						newPropertyList.classList.add('usr_md_');
						newPropertyList.classList.add('usr_md_search_list');
						if(isNot == "NOT ")
							newPropertyList.classList.add('usr_md_search_list_not');
						 
						$(this).parent().get(0).appendChild(newPropertyList);
						
//						var newDisjunctionButton = document.createElement('button'); 	
//						newDisjunctionButton.classList.add('usr_md_');
//						newDisjunctionButton.setAttribute('type','button');	
//						
//						newDisjunctionButton.innerHTML = 'Add disjunction';
//						$(this).parent().get(0).appendChild(newDisjunctionButton);
					}				
				  }				   
				  else if($(this).html() == "Select node"){				  	
		  				
						var selectTag = $(this).parent().children('input')[0];
						var selectedProperty = selectTag.value.trim();
						var selectedPropertyType = "Node";				

						var isNot = "";
						if($(this).parent().children('input').get(1).checked == 1)
							isNot = "NOT ";

						$(this).parent().children('input').remove();
						$(this).parent().children('span').remove();

						var newPropertyKey = document.createElement('b');

						newPropertyKey.classList.add('usr_md_');
						newPropertyKey.innerHTML = isNot + selectedProperty + ":";
						$(this).parent().get(0).insertBefore(newPropertyKey, $(this).get(0));						
	   
							$(this).html("Add property");
							$(this).next().html('Delete');
							
							var newPropertyList = document.createElement('ul');
							newPropertyList.classList.add('usr_md_');
							newPropertyList.classList.add('usr_md_search_list');
							if(isNot == "NOT ")
								newPropertyList.classList.add('usr_md_search_list_not');
							 
							$(this).parent().get(0).appendChild(newPropertyList);								
					  }
				  else if($(this).html() == "Select leaf"){				  	
		  				
						var selectTag = $(this).parent().children('input')[0];
						var selectedProperty = selectTag.value.trim();
						var selectedPropertyType = "String";				

						var isNot = "";
						if($(this).parent().children('input').get(1).checked == 1)
							isNot = "NOT ";

						$(this).parent().children('input').remove();
						$(this).parent().children('span').remove();

						var newPropertyKey = document.createElement('b');

						newPropertyKey.classList.add('usr_md_');
						newPropertyKey.innerHTML = isNot + selectedProperty + ":";
						$(this).parent().get(0).insertBefore(newPropertyKey, $(this).prev().get(0));						
	   						
						var textBox = document.createElement('input');
						textBox.classList.add('usr_md_');
									   
						textBox.setAttribute('type', 'text');
						textBox.textContent = "";
						$(this).parent().get(0).insertBefore(textBox, $(this).prev().get(0));
						
						
						var newPropertyIgnoreCaseBox = document.createElement('input');
						newPropertyIgnoreCaseBox.classList.add('usr_md_');								   
						newPropertyIgnoreCaseBox.setAttribute('type', 'checkbox');
						newPropertyIgnoreCaseBox.setAttribute('value', 'not');
						newPropertyIgnoreCaseBox.checked=true;
						
						var newPropertyIgnoreCaseBoxText = document.createElement('span');
						newPropertyIgnoreCaseBoxText.classList.add('usr_md_');
						newPropertyIgnoreCaseBoxText.innerHTML = " Ignore case";
						
						$(this).parent().get(0).insertBefore(newPropertyIgnoreCaseBox, $(this).prev().get(0));
						$(this).parent().get(0).insertBefore(newPropertyIgnoreCaseBoxText, $(this).prev().get(0));
						
						var newPropertyAnywhereBox = document.createElement('input');
						newPropertyAnywhereBox.classList.add('usr_md_');								   
						newPropertyAnywhereBox.setAttribute('type', 'checkbox');
						newPropertyAnywhereBox.setAttribute('value', 'not');
						newPropertyAnywhereBox.checked=true;
						
						var newPropertyAnywhereBoxText = document.createElement('span');
						newPropertyAnywhereBoxText.classList.add('usr_md_');
						newPropertyAnywhereBoxText.innerHTML = " Anywhere in value";
						
						$(this).parent().get(0).insertBefore(newPropertyAnywhereBox, $(this).prev().get(0));
						$(this).parent().get(0).insertBefore(newPropertyAnywhereBoxText, $(this).prev().get(0));
						
						
						$(this).html("Delete");
						$(this).prev().html("Ok");
														
					  }
				  else if($(this).html() == "Add disjunction"){
				  	var newDisjunction = document.createElement("li");
					newDisjunction.classList.add('usr_md_');
					
					var newDisjunctionOr = document.createElement('b');
					newDisjunctionOr.classList.add('usr_md_');
					newDisjunctionOr.innerHTML = "OR";
					
					var newPropertyButton = document.createElement('button'); 	
					newPropertyButton.classList.add('usr_md_');
					newPropertyButton.setAttribute('type','button');								
					newPropertyButton.innerHTML = 'Add property';
					
					var deleteButton = document.createElement('button'); 	
					deleteButton.classList.add('usr_md_');
					deleteButton.setAttribute('type','button');								
					deleteButton.innerHTML = 'Delete disjunction';
										
					$(this).parent().children('ul')[0].appendChild(newDisjunction);					
					newDisjunction.appendChild(newDisjunctionOr);
					newDisjunction.appendChild(newPropertyButton);
					newDisjunction.appendChild(deleteButton);					
				  }
				  else if($(this).html() == "Submit"){

					var data = DOMtoJSON(document.getElementById('queryUserMetadata').children[1]);
					var request = $.ajax({
				       type: 'POST',
				       url: queryIp,
				       data: JSON.stringify(data),
				       contentType: "application/json",
				       dataType: "json"
				     });
		 
					  request.done(function (respJSON){
					        console.log("Response " + respJSON);
					        $('.previous').css('visibility','hidden');
					        $('.next').css('visibility','hidden');
					        searchResultsCount = respJSON.length;
					        $('#resultTable tbody tr').remove();
					        //////
					        if(searchOn == "datasets"){
						        for(var i = 0; i < respJSON.length; i++){
						        	var createdDateArray = respJSON[i].created.split(" ");
						        	var createdDate = createdDateArray.slice(1,3).join(" ") + ", " + createdDateArray[5];
						        	var removeCell = "";
						        	if(window["userIsAdmin"] || window["userId"] == respJSON[i].authorId){
						        		removeCell = "<td><a href='#!' onclick='removeDataset(\"" + respJSON[i].id + "\",event)'>Remove</a></td>";
						        	}
						        	else if(window["userDefined"]){
						        		removeCell = "<td></td>";
						        	}
						        	var datasetThumbnail = "";
						        	if(respJSON[i].thumbnail != "None")
						        		datasetThumbnail = "<img src='" + window.location.protocol + "//" + window.location.hostname + (window.location.port ? ':' + window.location.port: '') + "/fileThumbnail/" + respJSON[i].thumbnail + "/blob' "
						        							+ "alt='Thumbnail of " + respJSON[i].datasetname + "' height='120' width='120'>";
						        	$('#resultTable tbody').append("<tr id='resultRow" + (i+1) + "' style='display:none;'><td><a href='" + window.location.protocol + "//" + window.location.hostname + (window.location.port ? ':' + window.location.port: '')
						        								+ "/datasets/" + respJSON[i].id + "'>"+ respJSON[i].datasetname + "</a></td>"
						        								+ "<td>" + createdDate + "</td>"
						        								+ "<td style='white-space:pre-line;'>" + respJSON[i].description + "</td>"
						        								+ "<td>" + datasetThumbnail + "</td>"
						        								+ removeCell + "</tr>");
						        }
						    }
					        else if(searchOn == "files"){
						        for(var i = 0; i < respJSON.length; i++){
						        	//var createdDateArray = respJSON[i].created.split(" ");
						        	//var createdDate = createdDateArray.slice(1,3).join(" ") + ", " + createdDateArray[5];
						        	var removeCell = "";
						        	if(window["userIsAdmin"] || window["userId"] == respJSON[i].authorId){
						        		removeCell = "<td><a href='#!' onclick='removeFile(\"" + respJSON[i].id + "\",event)'>Remove</a></td>";
						        	}
						        	else if(window["userDefined"]){
						        		removeCell = "<td></td>";
						        	}
						        	var fileThumbnail = "";
						        	if(respJSON[i].thumbnail != "None")
						        		fileThumbnail = "<img src='" + window.location.protocol + "//" + window.location.hostname + (window.location.port ? ':' + window.location.port: '') + "/fileThumbnail/" + respJSON[i].thumbnail + "/blob' "
						        							+ "alt='Thumbnail of " + respJSON[i].filename + "' height='120' width='120'>";
						        	$('#resultTable tbody').append("<tr id='resultRow" + (i+1) + "' style='display:none;'><td><a href='" + window.location.protocol + "//" + window.location.hostname + (window.location.port ? ':' + window.location.port: '')
						        								+ "/files/" + respJSON[i].id + "'>"+ respJSON[i].filename + "</a></td>"
						        								+ "<td>" + respJSON[i].contentType + "</td>"
						        								+ "<td>" + respJSON[i].dateCreated + "</td>"
						        								+ "<td>" + fileThumbnail + "</td>"
						        								+ removeCell + "</tr>");
						        }
						    }
					        ////
					        $('#resultTable').show();
					        
					        for(var i = 0; i < 10; i++){
					        	$("#resultTable tbody tr[id='resultRow" + (i+1) + "']").each(function() {
					        	    $(this).css('display','table-row');
					        	});
					        }
					        
					        if(respJSON.length > 10){
					        	currentFirst = 1;
					        	$('.next').css('visibility','visible');
					        }
		     			});
					 
					  request.fail(function (jqXHR, textStatus, errorThrown){
		        		console.error(
		            		"The following error occured: "+
		            		textStatus, errorThrown		            
		        			);
		        		notify("ERROR: " + errorThrown +". Search not executed.", "error");
		     			});
					 
					 
				  }	
				  
				  return false;
			}
		});
		
		 $('body').on('keypress','.usr_md_,.usr_md_submit',function(e){
			   	if($(this).is('input')){
				   	if(e.which == 13){
						$($(this).parent().children('button')[0]).click();			
						return false;
					}
				}
			   });
		 
		 $('body').on('click','.next',function(e){
			 currentFirst = currentFirst + 10;
			 $("#resultTable tbody tr").each(function() {
	        	    $(this).css('display','none');
	         });
			 for(var i = currentFirst; i < currentFirst + 10; i++){
				 $("#resultTable tbody tr[id='resultRow" + i + "']").each(function() {
					 $(this).css('display','table-row');
				 });
			 }
			 $('.previous').css('visibility','visible');
			 if(currentFirst + 10 > searchResultsCount)
				 $('.next').css('visibility','hidden');			 
		 });
		 $('body').on('click','.previous',function(e){
			 currentFirst = currentFirst - 10;
			 $("#resultTable tbody tr").each(function() {
	        	    $(this).css('display','none');
	         });
			 for(var i = currentFirst; i < currentFirst + 10; i++){
				 $("#resultTable tbody tr[id='resultRow" + i + "']").each(function() {
					 $(this).css('display','table-row');
				 });
			 }
			 $('.next').css('visibility','visible');
			 if(currentFirst == 1)
				 $('.previous').css('visibility','hidden');			 
		 });
			   
			   function DOMtoJSON(branchRootNode){
			   			var branchData = {};				
						var childrenProperties = branchRootNode.children;
						for(var i = 0; i < childrenProperties.length; i++){
							if(childrenProperties[i].children[1].tagName.toLowerCase() == 'input')
								continue;							
							var key = childrenProperties[i].children[0].innerHTML;
							if(key == "OR"){
								key = key + "__" + elementCounter;
								elementCounter++;
								branchData[key] = "dummy";
							}							
							else{
								key = key.substring(0, key.length - 1);
								if(key.length >= 4)
									if(key.substring(0,4) == "NOT ")
										key = key.substring(4) + "__not";								
								key = key + "__" + elementCounter;
								elementCounter++;
								if(childrenProperties[i].children[1].tagName.toLowerCase() == 'span'){
									branchData[key] = childrenProperties[i].children[1].innerHTML;  
								}else{
									branchData[key] = DOMtoJSON(childrenProperties[i].children[3]);
								}
							}
						}
						return branchData;
			   }
		
});