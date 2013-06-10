(function ($, Configuration) {
	console.log("Book previewer for " + Configuration.id);
	
	console.log("Updating tab " + Configuration.tab);
	
	//console.log(Configuration);
	Configuration.hostIp = "141.142.222.60";
	//Configuration.hostIp = "localhost";
	Configuration.jsPath = "/assets/javascripts";
	Configuration.assetPath = "/api/previews";
	
	var pathJs = "http://" + Configuration.hostIp + ":" + window.location.port + Configuration.jsPath + "/";
	var pathAsset = Configuration.assetPath + "/"; // "http://" + Configuration.hostIp + ":" + window.location.port + Configuration.assetPath + "/";
	
	var width = 750;
	var height = 550;
	
	
	var html = '<div id="bookPreview" ><div id="d_magazine" style="display:none;"><div id="magazine"></div></div>';
	html += '<div id="dOverlay"></div><div id="dBase"></div><div id="log"></div><div id="loader"></div><div id="blockAction"></div>		';
	html += '<div id="controller"><div id="dOpacityController"><div id="opacityController"></div></div></div></div>';
	$(Configuration.tab).append(html);
	
	base_resources = new Array();
	overlay_resources = new Array();
	var totalpage = 0;
	
	$.get(Configuration.url, function(xml) {
		console.log(xml);
		console.log("Loading data for tab " + Configuration.tab);
		
		var book = $(xml).find('book');
		totalpage = book.attr('page');
		book.find('page').each(function() {
			var magazine_page = $(this).find('magazine');
			var div = document.createElement('div');
			var img = document.createElement('img');
			img.src = pathAsset + magazine_page.attr('base');
			div.appendChild(img);
			document.getElementById('magazine').appendChild(div);
			
			var zoom_page = $(this).find('zoom');
			base_resources.push(pathAsset + zoom_page.attr('base'));
			overlay_resources.push(pathAsset + zoom_page.attr('overlay'));
		});

	});
	
	bookjs = document.createElement('script');
	bookjs.type = 'text/javascript';
	bookjs.src = pathJs + 'book.js';
	bookjs.onload = function() {
		turnjs_onload(totalpage, pathJs);
	}
	$('head')[0].appendChild(bookjs);
	
	console.log("Updating tab " + Configuration.tab);
	
			
}(jQuery, Configuration));