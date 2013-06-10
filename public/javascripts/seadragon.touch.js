//const value
var const_animationTime = 0.5;
var const_zoom_animationTime = 0.1;
var touch;
var dBase;
var base;
var log;
var scale = 1;

var zoom_center = new Seadragon.Point(0,0);
var zoom_scale;

var move_origin = new Seadragon.Point(0,0);
var move_to = new Seadragon.Point(0,0);
var move_center = new Seadragon.Point(0,0);

//Event
var pre_event = '', post_event = '';
var swip_event = '';

//Lock
var animation_lock = false;
var transform_lock = false;
var turning_lock = false;

//Attribute
var view_width = 600;
var view_height = 600;
var view_w = view_width; //View port
var view_h = view_height;
var image_width = 0;
var image_height = 0;
var image_left = 0;
var image_top = 0;
var image_scale = 1;
var image_isOpened = false;

// Detect touch support
function SDT_init(dElem,elem) {
	touch = 'ontouchend' in document;
	//base = elem;
	elem.addEventListener("animationstart",lock_animation);
	elem.addEventListener("animationfinish",unlock_animation);
	dElem.css({width:view_width,height:view_height,position:'absolute'});//,backgroundColor:'black',border:'1px solid black'});
}

function SDT_openDzi(f) {
	base.opendzi(f);
	alert('NOT YET IMPLEMENT REFER TO "SDO_openDzi" FOR DETAILED IMPLEMENTATION');
}

function SDT_get_info() {
	image_size = base.viewport.getBounds().getSize();
	image_center = base.viewport.getCenter();
	
	image_width = view_width/image_size.x;
	image_height = image_width/base.source.aspectRatio;//view_height/(((image_center.y-image_topleft.y)/image_size.y)+0.5);
	image_topleft = base.viewport.getBounds();
	//log.append('ll'+image_center.x+" tt"+image_center.y+'<br/>');
	//image_tl = base.viewport.pixelFromPoint(image_topleft);
	image_left = -image_topleft.x*image_width;
	image_top = -image_topleft.y*image_width;
	image_scale = base.viewport.getZoom();
	//log.append('W:'+image_width+' H:'+image_height+' L:'+image_left+' T:'+image_top+' Z:'+image_scale+'<br>');
	//image_height = 
}

function SDT_enable_touch(elem) {
	if (touch) {
		elem.hammer({swipe_time:300, prevent_default: false, hold:false, tap:false, tap_double:false, scale_treshold: 0, drag_min_distance: 0});
		elem.on('dragstart',do_dragstart);
		elem.on('drag',do_drag);
		elem.on('dragend',do_dragend);
		elem.on('transformstart',do_transformstart);
		elem.on('transform',do_transform);
		elem.on('transformend',do_transformend);
		elem.on('swipe',do_swipe);
		//elem.on('doubletap',hammerLog);
		//elem.on('hold',hammerLog);
		elem.on('release',do_release);
		elem.on('tap',hammerLog);
	}
}

function SDT_animation_event(pre_e,post_e) {
	pre_event = pre_e;
	post_event = post_e;
}

function hammerLog(event) {
	//event.preventDefault();
	//log.append(event.type+"<br/>");
}

//Return center on pinch-zoom in pixel;
function get_touchcenter(event) {
	x = (event.originalEvent.touches[0].clientX + event.originalEvent.touches[1].clientX)/2;
	y = (event.originalEvent.touches[0].clientY + event.originalEvent.touches[1].clientY)/2;
	return new Seadragon.Point(x,y);
}
function get_touchpoint(event) {
	x = event.originalEvent.touches[0].clientX;
	y = event.originalEvent.touches[0].clientY;
	return new Seadragon.Point(x,y);
}

function touch_at_corner(event) {
	x = event.originalEvent.touches[0].clientX - dBase[0].offsetLeft - image_left;
	y = event.originalEvent.touches[0].clientY - dBase[0].offsetTop - image_top;
	csz = 100;
	//log.append (current_page+','+ x +','+ y +','+image_width+','+image_height+"<br/>");
	if((y>0&&y<csz) || (y>image_height-csz&&y<image_height)) { // In Y region
		if(current_page%2 == 0 && current_page-1 != total_page) { // Right page
			if(current_page < total_page-1 && x>image_width-csz && x<image_width)// Not the last page
				return true;
			if(current_page != 0) // Have left page
				if(x>0-image_width && x <csz-image_width) {
					image_left -= image_width;
					return true;
				}
		}
		else if(current_page%2 == 1 ) { // Left page
			if(current_page > 0 && x>0 && x<csz) //Not the first page
				return true;
			if(current_page != total_page-1) // Have right page
				if(x>2*image_width-csz && x<2*image_width) {
					image_left += image_width;
					return true;
				}
		}
	}
	return false;
	//if(magazine_mode == 2)
	//alert(magazine.turn("_cornerActivated",event));
	//log.append(+"CCC" );//css("top"));
	
}

function do_dragstart(event) {
	//.append(pre_event+"PRE EVENT2<br/>");
	if(touch_at_corner(event)) { //book.js
		//log.append("Corner<br>");
		turning_lock = true;
		dMagazine.css("z-index",100);
		//dBase.hide();
		//dOverlay.hide();
		event.type = "touchstart";
		magazine.trigger(event);
	}
	else if(pre_event != '') {
		//log.append(pre_event+"PRE EVENT<br/>");
		//dMagazine.css("z-index",1000);//show();
		
		//alert(event.target.id);
		
		//magazine.flip("testtest",event);
		window[pre_event]();
	}
	//hide_overlay();
	move_origin = get_touchpoint(event);
	move_center = base.viewport.getCenter();
}

function do_drag(event) {
	if(turning_lock) {
		event.type = "touchmove";
		magazine.trigger(event);
	}
	else {
	//TEST pretty flip
	/*dBase.css('pointer-events','none');
	dOverlay.css('pointer-events','none');
	dBase.hide();
	dOverlay.hide();
	magazine.show();
	$('.turn-page').css('background-color',turnjs_bgcolor);
	//Stimulate event to turnjs
	//alert(event.target.id);
	//magazine.flip('testtest',event);
	event.target = magazine[0];
	event.type = 'touchstart';
	log.append('fire');
	//event.target.dispatchEvent(event);
	log.append('here');
	return;*/
	
	//log.append(event.type+"<br/>");
	move_to = get_touchpoint(event);
	//log.append(animation_lock+"<br/>");
	//DO NOT SKIP FRAME. IT IS TOO LACK WHEN DROP SOME FRAME.
	//if(!animation_lock) {
		//log.append("DO<br/>");
		//lock_animation();
		move_by = base.viewport.deltaPointsFromPixels(move_origin.minus(move_to));
		base.viewport.panTo(move_center.plus(move_by),true);
	}
	//}
	//else
	//	log.append("SKIP<br/>");
}

function do_dragend(event) {
	if(turning_lock) {
		event.type = "touchend";
		magazine.trigger(event);
		//setTimeout(function() {dMagazine.css("z-index",-100); $('#blockAction').hide(); /*dBase.show(); dOverlay.show();*/},3000);
	}
	else {
		do_release(event);
	}
	//Force move to final position
	/*while(animation_lock);
	move_by = base.viewport.deltaPointsFromPixels(move_origin.minus(move_to));
	base.viewport.panTo(move_center.plus(move_by),true);*/
		
}

function do_swipe(event) {
	base.viewport.panTo(move_center,true);
	if(event.direction == 'left')
		turnjs_next();
	else if(event.direction == 'right')
		turnjs_prev();
	else
		event.preventDefault();
	do_release(event);
}

function do_transformstart(event) {
	//log.append(event.type+"<br/>");
	if(pre_event != '') {
		window[pre_event]();
	}
	//hide_overlay();
	scale = base.viewport.getZoom();
	//Seadragon.Config.animationTime = const_zoom_animationTime;
	move_origin = get_touchcenter(event);
	move_center = base.viewport.getCenter();
	zoom_center =  base.viewport.pointFromPixel(move_origin.minus(Seadragon.Utils.getElementPosition(base.elmt)));
	//log.append("x:"+zoom_center.x+",y:"+zoom_center.y+"<br/>");
}
function do_transform(event) {
	//log.append(event.type+"<br/>");
	//scale = Math.pow(event.scale,0.25)
	zoom_scale = scale*event.scale;
	//log.append(zoom_scale+"<br/>");
//	lock_overlay();
	if(!animation_lock) {
		//log.append("DO<br/>");
		//lock_animation();
		move_to = get_touchcenter(event);
		move_by = base.viewport.deltaPointsFromPixels(move_origin.minus(move_to));
		base.viewport.panTo(move_center.plus(move_by),true);
		base.viewport.zoomTo(zoom_scale,zoom_center,true);
	}
	//else
		//log.append("SKIP<br/>");
	//zoom_scale = 1;
}
function do_transformend(event) {
	do_release(event);
}
function do_zoomBy(m) {
	if(pre_event != '') {
		window[pre_event]();
	}
	scale = base.viewport.getZoom();
	zoom_scale = scale*m
	base.viewport.zoomTo(zoom_scale);
	if(post_event != '') {
		window[post_event]();
	}
}
function do_zoomFit() {
	base.viewport.goHome();
	if(post_event != '') {
		window[post_event]();
	}
}

function do_release(event) {
	//log.append(event.type+"<br/>");
	if(turning_lock)
		turning_lock = false;
	else {
		//Seadragon.Config.animationTime = const_animationTime;
		//base.viewport.applyConstraints();
		if(post_event != '') {
			window[post_event]();
		}
		//show_overlay();
		//onAnimationFinish();
	}
}

function lock_animation() {
	animation_lock = true;
	//log.append("animation start<br/>");
	//console.log('Lock Animation 1 '+arguments.callee.caller.toString());
	if(!touch && pre_event != '' && !magazineFolding) {
		//console.log('Lock Animation 2');
		window[pre_event]();
	}
}

var override_unlock_animation = false;
function unlock_animation() {
	//log.append("animation end<br/>");
	//console.log('Un Lock Animation 1');
	if((!touch || override_unlock_animation) && post_event != '') {
		//console.log('Un Lock Animation 2');
		override_unlock_animation = false;
		window[post_event]();
	}
	animation_lock = false;
}




//https://raw.github.com/furf/jquery-ui-touch-punch/master/jquery.ui.touch-punch.js
//NOT IN USE
/**
* Simulate a mouse event based on a corresponding touch event
* @param {Object} event A touch event
* @param {String} simulatedType The corresponding mouse event
*/
/*
function simulateMouseEvent (event, simulatedType) {
	// Ignore multi-touch events
	if (event.originalEvent.touches.length > 1) {
		return;
	}

	event.preventDefault();

	var touch = event.originalEvent.changedTouches[0],
	simulatedEvent = document.createEvent('MouseEvents');

	// Initialize the simulated mouse event using the touch event's coordinates
	simulatedEvent.initMouseEvent(
		simulatedType,    // type
		true,             // bubbles                    
		true,             // cancelable                 
		window,           // view                       
		1,                // detail                     
		touch.screenX,    // screenX                    
		touch.screenY,    // screenY                    
		touch.clientX,    // clientX                    
		touch.clientY,    // clientY                    
		false,            // ctrlKey                    
		false,            // altKey                     
		false,            // shiftKey                   
		false,            // metaKey                    
		0,                // button                     
		null              // relatedTarget              
	);

	// Dispatch the simulated event to the target element
	//log.append(simulatedType+"<br/>");
	//event.target.
	base.dispatchEvent(simulatedEvent);
}
*/
