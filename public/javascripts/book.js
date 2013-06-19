function get_style(mobile) {
	var unit = $(window).width()/10;
	var numButton = 4;
	
	if($(window).width() >= $(window).height()) //landscape
		unit *= aspect_ratio;

	if(mobile) {
		//style = '*.controlbutton{width:'+unit+'px !important;height:'+unit+'px;float:right;background-size:800px !important;}';
		style = '*.controlbutton{width:'+unit/2+'px !important;height:'+unit/2+'px;float:right;background-size:800px !important;-webkit-transform:scale(2,2);-moz-transform:scale(2,2);margin:'+(unit/4)+'px '+(unit/4+5)+'px '+(unit/4)+'px '+(unit/4+5)+'px;}';
		style +='#controller{	width:595px; border-top:solid 1px #b8b8b8; position:relative; padding:5px; background-color:#ffffff; opacity:1.0; top:600'+/*(588-unit)+*/'px; height:'+(unit+1)+'px;}';
		style +='#dOpacityController {left:'+(26+unit)+'px;right:'+(80+numButton*unit)+'px;}';
		style +='.ui-slider .ui-slider-handle { width:'+unit+'px; height:'+unit+'px; font-size:'+unit/4+'px; } .ui-slider-horizontal .ui-slider-handle { top: -12px; margin-left: -'+(unit/2)+'px; text-align:center; text-decoration:none; } .ui-slider-horizontal { height:'+(unit-28)+'px; }';
		view_w = 602;
		view_h = 602+unit+10;
	}
	else {
		style = '*.controlbutton{width:34px !important;height:34px;float:right;}';
		style +='#controller{	width:595px; border-top:solid 1px #b8b8b8; position:relative; padding:5px; background-color:#ffffff; opacity:0.9; top:600px; height:35px;}';
		style +='#dOpacityController {left:50px;right:'+(20+numButton*40)+'px;}';
		view_w = 602;
		view_h = 647;
	}
	$('#d_magazine').parent().css({height:view_h});
	//style +='#magazine{ width:600px; height:600px; }';
	//style +='#magazine .turn-page{ background-color:'+turnjs_bgcolor+'; background-size:100% 100%; }';
	return style;
}

function turnjs_animation_event() {
	SDT_animation_event("turnjs_animation_event_before","turnjs_animation_event_after");
}
function turnjs_animation_event_before() {
	//log.append("ANIM start<br>");
	if(!magazineFolding) {
		SDO_hide_overlay();
		magazine.hide();
	}
}
function turnjs_animation_event_after() {
	if(!magazineFolding) {
		//log.append("ANIM end<br>");
		//console.log(override_unlock_animation);
		if(!override_unlock_animation)
			SDO_show_overlay();
		//Get image infomation
		//alert('123123');
		SDT_get_info();
		if(!override_unlock_animation) {
			turnjs_align();
			if(magazine_mode == 2) { //Double page mode
				if(turnjs_centering_right_page() == current_page%2) { //Current page is out of center
					//log.append("Out of center<br>");
					turnjs_out_of_center();
				}
				else {
					//log.append("In center<br>");
				}
			}	
		}
	}
}
function turnjs_out_of_center() {
	if(current_page%2 == 1 && current_page < total_page) { //Currently on the left page and not the last page
		current_page++;
		image_left += image_width;
		turnjs_openDzi();
		//setTimeout(turnjs_openDzi,1000);
		//setTimeout(turnjs_openDzi,1000);
		return true;
	}
	else if(current_page%2 == 0 && current_page > 0) {// Not the first page
		//log.append('pg'+current_page+"<br>");
		current_page--;
		//log.append('pg'+current_page+"<br>");
		//log.append(image_left+"<br>");
		image_left -= image_width;
		//log.append(image_left+"<br>");
		turnjs_openDzi();
		//setTimeout(turnjs_openDzi,1000);
		return true;
	}
	return false;
}

function turnjs_align() {
	magazine_pos.x = image_left-(magazine_mode-1)*image_width*((current_page+1)%2);
	magazine_pos.y = image_top;
	magazine.flip("set_position",magazine_pos.x,magazine_pos.y);
	magazine.flip('zoom_by',image_scale,image_width,image_height);
	magazine.show();
}


var magazine,dMagazine;
var magazinedrag_original = {x:0,y:0};
var magazine_moveby = {x:0,y:0};
var magazine_pos = {x:0,y:0};
var magazine_mode = 1; //1 Single 2 Double
var magazine_manual_turn = false;
//var turnjs_bgcolor = "#000";
var page_pos = {x:0,y:0};
var current_page = 0;
var total_page = 0;
//var base_resources = ['001-1.xml','002-1.xml','003-1.xml','001-1.xml','002-1.xml'];
//var overlay_resources = ['001-2.xml','002-2.xml','003-2.xml','001-2.xml','002-2.xml'];
var magazineFolding = false;

//Aspect ratio for controller
var aspect_ratio;
var known_aspect_ratio = [0.5294117647058824,0.5625,0.6,0.625,0.6666666666666667,0.75,0.8]; //Support 17:9 16:9 5:3 8:5 3:2 4:3 5:4

function turnjs_setbgcolor() {
	$('.turn-page.p-temporal').css('background-color',bgcolor_resources[current_page]);
}


/*function turnjs_openDzi() {
	log.append('turnjs opendzi pg'+current_page+'<br>');
	image_isOpened = false;
	//new_pos = base.viewport.getCenter();
	//log.append(new_pos.x+" "+new_pos.y+"<br>");
	$('.turn-page').css('background-color',turnjs_bgcolor);
	dBase.hide();
	dOverlay.hide();
	base.openDzi(base_resources[current_page]);	
	overlay.openDzi(overlay_resources[current_page]);
	turnjs_align_seadragon();
}*/

function turnjs_popupMsg_set(msg) {
	$('#popupMsg').html(msg);
	if(fullscreenMode)
		$('#popupMsg').css('top','0px');
	else
		$('#popupMsg').css('top','41px');
	$('#popupMsg').slideDown('fast');
}

function turnjs_popupMsg(msg) {
	turnjs_popupMsg_set(msg)
	setTimeout(turnjs_popupMsg_clear, 1000);
}

function turnjs_popupMsg_clear() {
	$('#popupMsg').slideUp('fast');
}

function turnjs_openDzi() {
	turnjs_popupMsg_set('Loading Page '+(current_page+1)+' From '+total_page);
	$('#blockAction').show();
	//log.append(image_left+"<br>");
	//log.append('turnjs opendzi pg'+current_page+'<br>');
	image_isOpened = false;
	//new_pos = base.viewport.getCenter();
	//log.append(new_pos.x+" "+new_pos.y+"<br>");
	dMagazine.css("z-index",100);
	turnjs_setbgcolor();
	//$('.turn-page').css('background-color',turnjs_bgcolor);
	turnjs_wait_until_ready_to_open();
	//base.openDzi(base_resources[current_page]);	
	//overlay.openDzi(overlay_resources[current_page]);
	//log.append(image_left+"<br>");
	//turnjs_align_seadragon();
	
}


function turnjs_wait_until_ready_to_open() {
	//console.log('C');
	if(base.profiler.isMidUpdate() || overlay.profiler.isMidUpdate()) {
		//log.append('turnjs waiting<br>');	
		setTimeout(turnjs_wait_until_ready_to_open,1000);
	}
	else {
		//base.close();
		//overlay.close();
		base.openDzi(base_resources[current_page]);	
		overlay.openDzi(overlay_resources[current_page]);
		//log.append('done_opening<br>');	
		turnjs_align_seadragon();
	}
}

function turnjs_align_seadragon() {
	if(!base.isOpen()) {
		//log.append('turnjs base opening<br>');	
		setTimeout(turnjs_align_seadragon,1000);
	}
	else if(!overlay.isOpen()) {
		//log.append('turnjs overlay opening<br>');	
		setTimeout(turnjs_align_seadragon,1000);
	}
	else {
		//log.append('ALIGNSS seadragon<br>');
		//log.append('ALIGNSS seadragon<br>');
		image_isOpened = true;
		goal_scale = image_scale; //Goal scale and position
		goal_left = image_left;
		goal_top = image_top;
		SDT_get_info(); //Current scale and position
		//Calculate static point (does not move when zoom)
		//In viewport coordinate
		diff_scale = goal_scale-image_scale;
		diff_left = image_left-goal_left;
		diff_top = image_top-goal_top;
		
		if(diff_scale != 0) {
			static_left = goal_left+(goal_scale/diff_scale)*diff_left;
			static_top = goal_top+(goal_scale/diff_scale)*diff_top;
			//Convert to image point Seadragon system
			pt_l = (static_left-(view_width-image_width)/2)/image_width;
			pt_t = (static_top-(view_height-image_height)/2)/image_width;
			base.viewport.zoomTo(goal_scale,new Seadragon.Point(pt_l, pt_t),true);
			//log.append('static L:'+static_left+' T:'+static_top+'<br>');
		}
		else {
			//No need to magnify, Only pan the image
			pt_l = diff_left/image_width;
			pt_t = diff_top/image_width;
			base.viewport.panBy(new Seadragon.Point(pt_l, pt_t),true);
		}
			
		//log.append('Goal S:'+goal_scale+' L:'+goal_left+' T:'+goal_top+'<br>');
		//log.append('Image S:'+image_scale+' L:'+image_left+' T:'+image_top+'<br>');
		//log.append('Diff S:'+diff_scale+' L:'+diff_left+' T:'+diff_top+'<br>');
		
		//log.append('point L:'+pt_l+' T:'+pt_t+'<br>');
		
		//For slow device, it update the image information too slow.
		//We need to allow it update whenever animation is complete
		override_unlock_animation = true;
		setTimeout(function() {
			dMagazine.css("z-index",-100);
			turnjs_popupMsg_clear()
			$('#blockAction').hide();
		}, 1000);
		//log.append('DONE<br>');
		/*
		image_scale = current_image_scale; // Preserve current zoom level
		pos = magazine.flip("get_position");
		log.append("posx"+pos.x+" poxy"+pos.y+"<br>");
		log.append("width"+image_width+"scale"+image_scale+"<br>");
		pos_left = image_width/2-pos.x+(view_width-image_width)/2;
		pos_top = image_height/2-pos.y+(view_height-image_height)/2;
		pos_pt_left = pos_left/image_width;
		pos_pt_top = pos_top/image_width;
		log.append("L"+pos_left+" T"+pos_top+"<br>");
		log.append('LL'+pos_pt_left+" TT"+pos_pt_top+"<br>");
		base.viewport.panTo(new Seadragon.Point(pos_pt_left, pos_pt_top),true);
		log.append('DONE<br>');
		*/
	}
}



function turnjs_centering_right_page() {
	if(magazine_pos.x+image_width < view_width/2) //On the right side
		return 1;
	else
		return 0;
}

function turnjs_next() {
	magazine_manual_turn = true;
	if(magazine_mode == 1) { //single page mode
		if(current_page < total_page-1) {
			current_page ++;
			magazine.turn('next');
		}
		else turnjs_popupMsg("Last Page");
	}
	else { //double page mode
		if(turnjs_centering_right_page() == 1) { //On the right
			if(current_page < total_page-2) { // Have next right page
				current_page += 2;
				magazine.turn('next');
			}
			else if(current_page < total_page-1) { // Only have next left page
				current_page++;
				image_left -= image_width;u
				magazine.turn('next');
				//alert("FAILED IMPLEMENT turnjs_next()");
			}
			else turnjs_popupMsg("Last Page");
		}
		else { //On left page
			if(current_page < total_page-2) { // Allow turn only have next left page
				current_page += 2;
				magazine.turn('next');
			}
			else turnjs_popupMsg("Last Page");
		}
	}
}

function turnjs_prev() {
	magazine_manual_turn = true;
	if(magazine_mode == 1) { //single page mode
		if(current_page > 0) {
			current_page --;
			magazine.turn('previous');
		}
		else turnjs_popupMsg("First Page");
	}
	else { //double page mode
		if(turnjs_centering_right_page() == 0) { //On the left
			if(current_page > 1) { // Have next left page
				current_page -= 2;
				magazine.turn('previous');
			}
			else if(current_page > 0) { // Only have next right page
				current_page --;
				image_left += image_width;
				magazine.turn('previous');
				//alert("FAILED IMPLEMENT turnjs_next()");
			}
			else turnjs_popupMsg("First Page");
		}
		else { //On left page
			if(current_page > 1) { // Allow turn only have next right page
				current_page -= 2;
				magazine.turn('previous');
			}
			else turnjs_popupMsg("First Page");
		}
	}
}

var loadingjscssfile = 0;

//For browsers (Safari/iOs) who does not support <link> onload.
//http://www.phpied.com/when-is-a-stylesheet-really-loaded/
var loadingcssfile = document.styleSheets.length;  


// http://www.javascriptkit.com/javatutors/loadjavascriptcss.shtml
function loadjscssfile(filename, filetype){
 if (filetype=="js"){ //if filename is a external JavaScript file
  var fileref=document.createElement('script');
  fileref.setAttribute("type","text/javascript");
  fileref.setAttribute("src", filename+'.'+filetype);
  
 }
 else if (filetype=="css"){ //if filename is an external CSS file
  var fileref=document.createElement("link");
  fileref.setAttribute("rel", "stylesheet");
  fileref.setAttribute("type", "text/css");
  fileref.setAttribute("href", filename+'.'+filetype);
 }
 if (typeof fileref!="undefined") {
  document.getElementsByTagName("head")[0].appendChild(fileref);
  fileref.setAttribute("onload","turnjs_onloaded()");
  loadingjscssfile++;
 }
}

function turnjs_onloaded() {
	loadingjscssfile--;
}

function turnjs_onload(page,pathJs) {
	console.log("turnJS onload");
	//alert('turnjs_onload');
	
	//Set totalpage
	total_page = page;
	
	loadjscssfile(pathJs + "jquery-ui-1.10.3.custom.min", "css");
	loadjscssfile(pathJs + "book", "css");
	
	loadjscssfile(pathJs + "jquery-ui-1.10.3.custom.min", "js");
	
	loadjscssfile(pathJs + "turn", "js");
	loadjscssfile(pathJs + "hammer", "js");
	loadjscssfile(pathJs + "jquery.hammer", "js");
	
	loadjscssfile(pathJs + "seadragon.touch", "js");
	loadjscssfile(pathJs + "seadragon.overlay", "js");
	

	setTimeout("turnjs_onload_done()",1000);
}

function turnjs_onload_done() {
	if(loadingjscssfile != 0 && (loadingjscssfile != 2 || loadingcssfile+2 != document.styleSheets.length)) {
		console.log('WAIT' + loadingjscssfile);
		setTimeout("turnjs_onload_done()",1000);
	}
	else {
		console.log("turnJS onload done");
		
		dBase = $('#dBase');
		dOverlay = $('#dOverlay');
		log = $('#log');
	
		base = new Seadragon.Viewer("dBase");
		overlay = new Seadragon.Viewer("dOverlay");
		//base.openDzi("001-1.xml");
		//overlay.openDzi("001-2.xml");
	
		Seadragon.Config.animationTime = const_animationTime;
		Seadragon.Config.springStiffness = 10;
		Seadragon.Config.visibilityRatio = 0;
		//Seadragon.Config.imagePath = "img/";
	
		console.log("SDT init");
		SDT_init(dBase,base);
		SDT_enable_touch(dBase);
		
		console.log("SDO init");
		SDO_init(dBase,base,dOverlay,overlay);
		
		console.log("SDO open dzi");
		SDO_openDzi(base_resources[0],overlay_resources[0],function() {	
			//SDO_animation_event(); //Enable touch feature with overlay
			turnjs_animation_event();
			
			console.log("turnJS magazine init");
			turnjs_setbgcolor('#cff');
			turnjs_init($('#magazine'),$('#d_magazine'));
			
			
			console.log("SDO controller");
			SDO_create_opacity_controller($('#opacityController'),$(".overlayed"));
			SDO_custom_style('get_style');
			SDO_implement_controller($('#controller'));
			
			$('#d_magazine').parent().css({width:view_w,height:view_h,border:'1px solid #b8b8b8'});
			
			dMagazine.show();
		});
		
		//Find closest aspect ratio
		if($(window).width() < $(window).height())
			aspect_ratio = $(window).width()/$(window).height();
		else
			aspect_ratio = $(window).height()/$(window).width();
		var min_err = 1;
		for(var x in known_aspect_ratio) {
			var err = known_aspect_ratio[x]-aspect_ratio;
			if(Math.abs(err) < Math.abs(min_err))
				min_err = err;
		}
		aspect_ratio += min_err;
		
		//BlockAction
		$('#blockAction').on('click',function(event){event.preventDefault();});
		$('#blockAction').on('touchstart',function(event){event.preventDefault();});
		$('#popupMsg').on('click',function(event){event.preventDefault();});
		$('#popupMsg').on('touchstart',function(event){event.preventDefault();});
	}
}

function turnjs_init(mz,dmz) {
	magazine = mz;
	dMagazine = dmz;
	magazine.turn({
		display: 'single',
		acceleration: true,
		gradients: true,
		//gradients: !$.isTouch,
		elevation:50,
		shadows: true,
		when: {
			turned: function(e, page) {
				//$('.turn-page').css('background-color','');
				dBase.fadeIn();
				dOverlay.fadeIn();
				/*console.log('Current view: ', $(this).turn('view'));*/
			}
		}
	});
	magazine_mode = 1;
	magazine_pos.x = (view_width-image_width)/2-(magazine_mode-1)*image_width*((current_page+1)%2);
	magazine_pos.y = (view_height-image_height)/2;
	//magazine.flip("set_position",magazine_pos.x,magazine_pos.y);
	magazine.flip("dimension",image_width,image_height);
	magazine.flip("viewport",view_width,view_height);
	magazine.flip("update_size",magazine);
	turnjs_align();
	
	turnjs_setbgcolor();
	//$('.turn-page').css('background-color',turnjs_bgcolor);
	
	magazine.bind("turning", function(event, page, view) {
		//log.append("TURNING");
		$('#blockAction').show();
		dBase.hide();
		dOverlay.hide();
	});
	magazine.bind("release", function(event, turned) {
		$('#blockAction').show();
	});
	magazine.bind("turned", function(event, page, view) {
		if(magazine_manual_turn) {
			magazine_manual_turn = false;
			turnjs_openDzi();
		}
		else {
			$('#blockAction').hide();
			//log.append("TURNED");
			page--;
			//log.append(page);
			if(magazine_mode == 2) { // Double page
				if(page > current_page) // Forward
					if(page < total_page-1) // Not the last page
						current_page = page+1;
					else {
						current_page = page;
						image_left -= image_width;
					}
				else // Backward
					if(page > 0) // Not the first page
						current_page = page-1;
					else {
						current_page = page;
						image_left += image_width;
					}	
			}
			else
				current_page = page;
			turnjs_openDzi();
		}
		//current_page = page-1; // Check out of center
		/*if(!turnjs_out_of_center())
			 // Manually open page*/
	});
	
	if(!touch) {
		magazine.bind("start", function(event, pageObject, corner) {
			//console.log('START');
			//log.append("start");
			magazineFolding = true;
			dMagazine.css("z-index",100);
			
			/*seadragonController.animate({top:'+=45px',height:'0px',opacity:0} ,function() {
				//console.log('START-animate');
				dMagazine.css("z-index",100);
				//dBase.hide();
				//dOverlay.hide();
				//console.log('START-animated');
			});*/
			//console.log('START-done');
		});
		
		magazine.bind("endzz", function(event, pageObject, turned) { //endzz
			//log.append("end");
			$('#blockAction').hide();
			console.log('ENDZZ');
			magazineFolding = false;
			//seadragonController.animate({top:'-=45px',height:'35px',opacity:1});
			dMagazine.css("z-index",-100);
			//Delay for seadragon
			//setTimeout(turnjs_align_seadragon,100);
			//dBase.show();
			//dOverlay.show();
		});
	}
	else {
		magazine.bind("endzz", function(event, pageObject, turned) { //endzz
			//log.append("end");
			$('#blockAction').hide();
			console.log('ENDZZ');
			//magazineFolding = false;
			//seadragonController.animate({top:'-=45px',height:'35px',opacity:1});
			dMagazine.css("z-index",-100);
			//Delay for seadragon
			//setTimeout(turnjs_align_seadragon,100);
			//dBase.show();
			//dOverlay.show();
		});
	}
	
	$(window).resize(function(){
		
		
		$('#blockAction').show();
		SDO_set_custom_style();
		
	
		if(fullscreenMode) {
			
			view_width = window.innerWidth;
			view_height = window.innerHeight;
			
			SDO_detect_screenmode();
			
			var fullscreen_bottom = 10+parseInt($('#controller').css('height'));
			var fullscreen_css = {width:'100%',height:'auto',position:'fixed',top:'0px',bottom:fullscreen_bottom+'px',left:'0px'};
			dBase.css(fullscreen_css);
			dOverlay.css(fullscreen_css);
			dMagazine.css(fullscreen_css);
			bookPreview = dBase.parent();
			bookPreview.css({position:'fixed',width:'100%',height:'100%',left:'0px',top:'0px',zIndex:'2'});
			
			setTimeout(do_zoomFit,1000);
			
			/*if(window.innerHeight > window.innerWidth){
				magazine.flip("displaymode",1); //Portrait
			}
			else {
				magazine.flip("displaymode",2); //Landscape
			}*/
		}
		else
			$('#blockAction').hide();
		//resizeScreen();
	});
	
	
	
	//$('#magazine').flip("displaymode",0); 
	
	//Enable touch feature
	/*if(touch) {
		magazine.hammer({prevent_default: true, swipe:false, drag_vertical:false, transform:false ,tap_double:false, hold:false, drag_min_distance: 0});
		magazine.on('dragstart',do_magazinedragstart);
		magazine.on('drag',do_magazinedrag);
		//controller.on('dragend',do_opacitydragend);
		//magazine.on('tap',do_opacitydrag);
		magazine.on('release',switch_seadraon_turnjs);
	}*/
}

function switch_seadraon_turnjs() {
	dMagazine.hide();
	dBase.show();
	dOverlay.show();
}

function do_magazinedragstart(event) {
	magazinedrag_original= get_touchpoint(event);
	magazine_pos = magazine.flip("get_position");
}
function do_magazinedrag(event) {
	cur_pos = get_touchpoint(event);
	new_x = magazine_pos.x+cur_pos.x-magazinedrag_original.x;
	new_y = magazine_pos.y+cur_pos.y-magazinedrag_original.y;
	magazine.flip("set_position",new_x,new_y);
}
