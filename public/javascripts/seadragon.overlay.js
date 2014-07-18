var seadragonController = '';
var opacityController = '';
var opacity_new_value ={value:100};
var SDO_hide_time = 100, SDO_show_time = 200;
var dBase,base,dOverlay,overlay;
var mobile = false;
var SDO_open_callback = null;

function hide2(elem) {
	elem.css('visibility','hidden');
}
function show2(elem) {
	elem.css('visibility','visible');
}

function SDO_init(dB,b,dO,o) {
	dBase = dB;
	base = b;
	dOverlay = dO;
	overlay = o;
	
	dB.addClass('overlayed');
	dO.css({width:view_width,height:view_height,position:'absolute'});//,backgroundColor:'black',border:'1px solid black'});
	
	//Detect mobile devices
	if (navigator.userAgent.match(/Android/i) ||
	    navigator.userAgent.match(/webOS/i) ||
	    navigator.userAgent.match(/iPhone/i) ||
	    navigator.userAgent.match(/iPod/i)) {
		mobile = true;
	}
	
	/////////////////Program specific
}

function SDO_openDzi(fb,fo,callback) {
	image_isOpened = false;
	//console.log("base openDzi at "+fb);
	base.openDzi(fb);
	//console.log("overlay openDzi at "+fo);
	overlay.openDzi(fo);
	SDO_open_callback = callback;

	SDO_wait_until_open();
}

function SDO_wait_until_open() {
	if(!base.isOpen() || !overlay.isOpen()) {
		console.log("WAIT");
		setTimeout(SDO_wait_until_open,1000);
	}
	else{
		image_isOpened = true;
		SDT_get_info();
		//Align	
		//turnjs_align();
		if(SDO_open_callback != null) {
			SDO_open_callback();
			//window[SDO_open_callback]();
			SDO_open_callback = null;
		}
	}
}

function SDO_hide_overlay() {
	//Cannot haave animation here since it will ignore the SDO_show_verlay
	//Hence it will not restore the overlay layer
	/*dBase.animate({opacity:1},SDO_hide_time, function() {
		dOverlay.css('display','none');
	});*/
	dBase.css('opacity',1);
	dOverlay.hide();
	//dOverlay.css('display','none');
}

function SDO_align_overlay() {
	//Copy coordinate to overlay layer
	//log.append("ALIGN<br/>");
	overlay.viewport.panTo(base.viewport.getCenter(),true);
	overlay.viewport.zoomTo(base.viewport.getZoom(),Seadragon.Point(),true);
}

function SDO_show_overlay() {
	//log.append("POST EVENT<br/>");
	
	//Delay 100ms Seadragon does not immediately update position data
	setTimeout(SDO_align_overlay, 100);
	
	//dOverlay.animate({opacity:1},100);
	//dBase.animate({opacity:1},1000);
	baseopacity = {opacity:0};
	baseopacity.opacity = SDO_get_opactiy();
	
	dOverlay.show();
	//dOverlay.css('display','');
	//dBase.css(baseopacity);
	dBase.animate(baseopacity,SDO_show_time);
	
	//$(".overlayed").css("opacity",parseFloat($('#opacityController').slider("value"))/100);
}
function SDO_create_opacity_controller(controller,affect_elem) {
	opactiyController = controller;
	controller.slider({value:100,min:0,max:100});
	controller.bind( "slidechange", function(event, ui) {
		affect_elem.css("opacity",parseFloat($('#opacityController').slider("value"))/100);
	});
	
	//Enable touch feature
	if(touch) {
		controller.hammer({prevent_default: true, swipe:false, drag_vertical:false, transform:false ,tap_double:false, hold:false, drag_min_distance: 0});
		controller.on('dragstart',do_opacitydragstart);
		controller.on('drag',do_opacitydrag);
		//controller.on('dragend',do_opacitydragend);
		controller.on('tap',do_opacitydrag);
		controller.on('release',do_opacitydragend);
	}
}
function do_opacitydragstart(event) {
	//abc = $('.ui-slider-handle').offset().left;
	//log.append(event.type+abc+"OPACITY<br/>");
	//opacity_move_origin = get_touchpoint(event);
	
}
function do_opacitydrag(event) {
	var slider_handle = $('.ui-slider-handle');
	var old_offset = slider_handle.offset();
	var new_offset = {left:get_touchpoint(event).x};
	slider_handle.offset(new_offset);
	var pos = parseInt(slider_handle.css('left'));
	var max = parseInt(opactiyController.css('width'));
	opacity_new_value = {value:pos/max*100};
	if(opacity_new_value.value < 0) {
		opacity_new_value.value=0;
		slider_handle.offset(old_offset);  //Revert back
	}
	else if(opacity_new_value.value >100) {
		opacity_new_value.value=100;
		slider_handle.offset(old_offset);  //Revert back
	}
	slider_handle.text(parseInt(opacity_new_value.value)+'%');
	opactiyController.slider(opacity_new_value);
	//log.append(event.type+"touch"+new_offset.left+"pos"+pos+"max"+max+"val"+opacity_new_value.value+"OPACITY<br/>");
	//log.append($('.ui-slider-handle').css('left')+"<br/>");
	//log.append(parseInt($('.ui-slider-handle').css('left'))+"<br/>");
}
function do_opacitydragend(event) {
	//log.append(event.type+opacity_new_value.value+"OPACITY<br/>");
	opactiyController.slider(opacity_new_value);
}


function SDO_get_opactiy(controller) {
	controller = controller || opactiyController;
	return parseFloat(controller.slider("value"))/100;
}


//Enable hide/show overlay layer while transforming animation
function SDO_animation_event() {
	SDT_animation_event("SDO_hide_overlay","SDO_show_overlay");
}

//-------------------------------------------------------PROGRAM SCPECIFIC FUNCTION---------------------------------------------//
var fullscreenMode = false;
var doublescreenMode = false;
var custom_style = '';

function SDO_detect_screenmode() {
	if(fullscreenMode) {
		if(window.innerHeight > window.innerWidth)
			magazine_mode = 1; //Portrait
		else
			magazine_mode = 2; //Landscape
	}
	else if(doublescreenMode)
		magazine_mode = 2;
	else 
		magazine_mode = 1;
	magazine.flip('displaymode',magazine_mode);
	turnjs_setbgcolor();
	//$('.turn-page').css('background-color',turnjs_bgcolor);
	
}

function SDO_switch_doublescreen() {
	if(!fullscreenMode) {
		$('#blockAction').show();
		turnjs_animation_event_before();
		doublescreenMode = !doublescreenMode;
		SDO_detect_screenmode();
		SDO_switch_screen();
	}
}

function SDO_switch_fullscreen() {
	$('#blockAction').show();
	turnjs_animation_event_before();
	fullscreenMode = !fullscreenMode;
	SDO_detect_screenmode();
	SDO_switch_screen();
}

function SDO_switch_screen() {
	if(fullscreenMode) {
		var fullscreen_bottom = 10+parseInt(seadragonController.css('height'));
		var fullscreen_css = {width:'100%',height:'auto',position:'fixed',top:'0px',bottom:fullscreen_bottom+'px',left:'0px'};

		dBase.css(fullscreen_css);
		dOverlay.css(fullscreen_css);
		dMagazine.css(fullscreen_css);
		seadragonController.css({width:'auto',position:'fixed',top:'auto',left:'0px',right:'0px',bottom:'0px'});
		//$('#magazine').flip("dimension",window.innerWidth/2,1200/400*window.innerWidth/2);
		//$('#magazine').flip("viewport",window.innerWidth/2,window.innerHeight/2);
		view_width = window.innerWidth;
		view_height = window.innerHeight;
		
		bookPreview = dBase.parent();
		bookPreview.css({position:'fixed',width:'100%',height:'100%',left:'0px',top:'0px',zIndex:'2'});
		$('*').css('visibility','hidden');
		$('#'+bookPreview.attr('id')+' *').css('visibility','visible');
	}
	else if(doublescreenMode) {
		if($('.container').length > 0)
			view_width = parseInt($('.container').css('width'))-40;
		else
			view_width = 940;
		view_height = 600;
		dBase.css({width:view_width+'px',height:view_height+'px',position:'absolute',top:'auto',bottom:'auto',left:'auto'});
		dOverlay.css({width:view_width+'px',height:view_height+'px',position:'absolute',top:'auto',bottom:'auto',left:'auto'});
		dMagazine.css({width:view_width+'px',height:view_height+'px',position:'absolute',top:'auto',bottom:'auto',left:'auto'});
		controller_w = view_width-5;
		controller_t = 600;//view_width-parseInt(seadragonController.css('height'))-11;
		
		bookPreview = dBase.parent();
		bookPreview.css({position:'static',width:(view_width+2),height:view_h,left:'auto',top:'auto',zIndex:'auto'});
		$('*').css('visibility','visible');
		
		bookPreview.parent().css('overflow','visible');
		
		seadragonController.css({position:'relative',width:controller_w+'px',top:controller_t+'px',left:'auto',buttom:'auto'});
		
		$('.span4').css('margin-top',(50+view_h)+'px');
	}
	else {
		//Restore original value
		view_width = 600;
		view_height = 600;
		dBase.css({width:view_width+'px',height:view_height+'px',position:'absolute',top:'auto',bottom:'auto',left:'auto'});
		dOverlay.css({width:view_width+'px',height:view_height+'px',position:'absolute',top:'auto',bottom:'auto',left:'auto'});
		dMagazine.css({width:view_width+'px',height:view_height+'px',position:'absolute',top:'auto',bottom:'auto',left:'auto'});
		controller_w = view_width-5;
		controller_t = 600;//view_width-parseInt(seadragonController.css('height'))-11;
		
		bookPreview = dBase.parent();
		bookPreview.css({position:'static',width:view_w,height:view_h,left:'auto',top:'auto',zIndex:'auto'});
		$('*').css('visibility','visible');
		
		bookPreview.parent().css('overflow','auto');
		
		seadragonController.css({position:'relative',width:controller_w+'px',top:controller_t+'px',left:'auto',buttom:'auto'});
		
		$('.span4').css('margin-top','');
		
		//$('#magazine').flip("dimension",400,1200);
		//$('#magazine').flip("viewport",400,200);
		//$('#magazine').flip("displaymode",2); 
	}
	//view_width = parseInt(dBase.css('width'));
	//view_height = parseInt(dBase.css('height'));
	newsize = new Seadragon.Point(view_width,view_height);
	base.viewport.resize(newsize);
	overlay.viewport.resize(newsize);
	//Insure turnjs will properly align with seadragon
	//turnjs_align_seadragon();
	setTimeout(do_zoomFit,1000);
	//$('#loader').fadeOut();
	//reset_zoom();//Zoomfit
}

function SDO_custom_style(style) {
	custom_style = style;
}

function SDO_set_custom_style() {
	if(custom_style != '') {
		$('#controllerStyle').remove();
		
		var style = document.createElement('style');
		style.type = 'text/css';
		style.id = 'controllerStyle';
		style.innerHTML = window[custom_style](mobile);
		document.getElementsByTagName('head')[0].appendChild(style);
	}
}

function SDO_implement_controller(controller,f) {
	SDO_set_custom_style();
	
	
	seadragonController = controller;
	//window[f](controller);
	
	//Prev page
	new_elem = $(document.createElement('button'));
	new_elem.button({icons: {primary: "ui-icon-carat-1-w"}, text:false});
	new_elem.addClass('controlbutton');
	new_elem.on('click',turnjs_prev);
	new_elem.css({float:'left'});
	controller.append(new_elem);
	
	//Next page
	new_elem = $(document.createElement('button'));
	new_elem.button({icons: {primary: "ui-icon-carat-1-e"}, text:false});
	new_elem.addClass('controlbutton');
	new_elem.on('click',turnjs_next);
	controller.append(new_elem);
	
	//Fullscreen button
	new_elem = $(document.createElement('button'));
	new_elem.button({icons: {primary: "ui-icon-arrow-4-diag"}, text:false});
	new_elem.addClass('controlbutton');
	new_elem.on('click',SDO_switch_fullscreen);
	controller.append(new_elem);
	
	//Doublescreen button
	new_elem = $(document.createElement('button'));
	new_elem.button({icons: {primary: "ui-icon-arrowthick-2-e-w"}, text:false});
	new_elem.addClass('controlbutton');
	new_elem.on('click',SDO_switch_doublescreen);
	controller.append(new_elem);

	
	/*//Zoom in
	new_elem = $(document.createElement('button'));
	new_elem.button({icons: {primary: "ui-icon-zoomin"}, text:false});
	new_elem.addClass('controlbutton');
	new_elem.on('click',function(event){do_zoomBy(2);});
	controller.append(new_elem);
	
	//Zoom out
	new_elem = $(document.createElement('button'));
	new_elem.button({icons: {primary: "ui-icon-zoomout"}, text:false});
	new_elem.addClass('controlbutton');
	new_elem.on('click',function(event){do_zoomBy(0.5);});
	controller.append(new_elem);*/
	
	//Zoom fit
	new_elem = $(document.createElement('button'));
	new_elem.button({icons: {primary: "ui-icon-search"}, text:false});
	new_elem.addClass('controlbutton');
	new_elem.on('click',do_zoomFit);
	controller.append(new_elem);
	

	//$("#prevpage").button({icons: {primary: "ui-icon-carat-1-w"}, text:false});
	
	//Hide default seadragon controller
	base.clearControls();
	overlay.clearControls();
	
	/*var fullPageButton = new Seadragon.Button(
		"Go Home",
		"img/home_rest.png",
	   "img/home_group.png",
	   "img/home_hover.png",
		"img/home_down.png",
		null,       // do nothing on initialpress
		goHome,     // go home on release
		null,       // no need to use clickthresholds
		null,       // do nothing on enter
		null       // do nothing on exit
	);
	
	var opacityController = document.getElementById('opacityController');*/
	
	/*base.addControl(document.getElementById('nextpage'),Seadragon.ControlAnchor.BOTTOM_RIGHT);
	base.addControl(document.getElementById('fullscreenMode'),Seadragon.ControlAnchor.BOTTOM_RIGHT);
	base.addControl(document.getElementById('zoomin'),Seadragon.ControlAnchor.BOTTOM_RIGHT);
	base.addControl(document.getElementById('zoomout'),Seadragon.ControlAnchor.BOTTOM_RIGHT);
	base.addControl(document.getElementById('zoomfit'),Seadragon.ControlAnchor.BOTTOM_RIGHT);
	//base.addControl(fullPageButton.elmt,Seadragon.ControlAnchor.BOTTOM_RIGHT);
	base.addControl(opacityController,Seadragon.ControlAnchor.BOTTOM_RIGHT);
	base.addControl(document.getElementById('prevpage'),Seadragon.ControlAnchor.BOTTOM_RIGHT);
	var op_width = 600-40*6;
	$('#opacityController').css({width:op_width});*/
}