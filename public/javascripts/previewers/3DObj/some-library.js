(function ($, Configuration) {
  console.log("3D obj previewer for " + Configuration.id);

  var fileUrl = "http://" + Configuration.hostIp + ":" + window.location.port + Configuration.url + "/textures/dataset/" + Configuration.id + "/json";
  var pathJs = "http://" + Configuration.hostIp + ":" + window.location.port + Configuration.jsPath + "/";
  
  var width = 750;
  var height = 550;
  
  $(Configuration.tab).append("<p>Move mouse while left-clicking and pressing CTRL to pan model. Move mouse wheel or move mouse up and down </br> while left-clicking and pressing SHIFT to change distance. Move mouse while left-clicking and not pressing CTRL or SHIFT to rotate.</p>");
  

  
  var s = document.createElement("script");
  s.type = "text/javascript";
  s.src = pathJs + "Detector.js";
  console.log("Updating tab " + Configuration.tab);
  $(Configuration.tab).append(s);
  
  var s = document.createElement("script");
  s.type = "text/javascript";
  s.src = pathJs + "Three.js";
  console.log("Updating tab " + Configuration.tab);
  $(Configuration.tab).append(s);
  
  var s = document.createElement("script");
  s.type = "text/javascript";
  s.src = pathJs + "RequestAnimationFrame.js";
  console.log("Updating tab " + Configuration.tab);
  $(Configuration.tab).append(s);
    
  if ( ! Detector.webgl ) Detector.addGetWebGLMessage();

	var SCREEN_WIDTH = width;
	var SCREEN_HEIGHT = height;
	var FLOOR = 0;

	var container;

	var camera, scene;
	var webglRenderer;

	var zmesh, geometry;

	var mouseX = 0, mouseY = 0;
	var windowHalfX = width / 2;
	var windowHalfY = height / 2;
	
	var mouseDown = false;
	
	init();
	animate();

	function init() {
		
		container = document.getElementById(Configuration.tab.replace("#",""));		
		container.onmousedown = function() { 
		  mouseDown = true;
		}
		document.body.onmouseup = function() {
		  mouseDown = false;
		}
		document.body.addEventListener( 'mousemove', onDocumentMouseMove, false );		
		// IE, Chrome, Safari, Opera
		document.body.addEventListener( 'mousewheel', onContainerMouseWheelMove, false );
		// Firefox
		document.body.addEventListener( 'DOMMouseScroll', onContainerMouseWheelMove, false );
		
		// camera
		camera = new THREE.PerspectiveCamera( 75, SCREEN_WIDTH / SCREEN_HEIGHT, 1, 100000 );
		camera.position.z = 75;
		
		controls = new THREE.TrackballControls( camera);		
		
		//scene
		scene = new THREE.Scene();

		// lights
		var ambient = new THREE.AmbientLight( 0xffffff );
		scene.add( ambient );

		// more lights
		var directionalLight = new THREE.DirectionalLight( 0xffeedd );
		directionalLight.position.set( 0, -70, 100 ).normalize();
		scene.add( directionalLight );
		
		// renderer
		webglRenderer = new THREE.WebGLRenderer();
		webglRenderer.setSize( SCREEN_WIDTH, SCREEN_HEIGHT );
		webglRenderer.domElement.style.position = "relative";
		container.appendChild( webglRenderer.domElement );
		
		// loader
		var loader = new THREE.JSONLoader(),
			callbackModel   = function( geometry ) { createScene( geometry) };
		loader.load( { model: fileUrl, callback: callbackModel } );
				
	}

	function createScene( geometry) {
		
		zmesh = new THREE.Mesh( geometry, new THREE.MeshFaceMaterial() );
		zmesh.position.set( 0, -20, 0 );
		zmesh.scale.set( 1, 1, 1 );
		scene.add( zmesh );
		
	}
	

	function onDocumentMouseMove(event) {
		
		if (mouseDown == true){
			//Rotates when CTRL key not pressed, pans when pressed.
			if(event.ctrlKey == true){
				camera.position.x -= (event.clientX - mouseX)/5;
				camera.position.y += (event.clientY - mouseY)/5;
			}
			else if(event.shiftKey == true){
				camera.position.z += (event.clientY - mouseY)/5;
			}
			else{								
				zmesh.rotation.x += (event.clientY - mouseY)/80;
				zmesh.rotation.y += (event.clientX - mouseX)/80;
			}
		}	
		mouseX = event.clientX;
		mouseY = event.clientY;			
	}
	
	function onContainerMouseWheelMove(event) {
		var delta = Math.max(-1, Math.min(1, (event.wheelDelta || -event.detail)));
		camera.position.z += - delta * 8;
		
		event.preventDefault();
	}
	
	function animate() {

		requestAnimationFrame( animate );
		render();
	}

	function render() {
		webglRenderer.render( scene, camera );
				
	}

  
}(jQuery, Configuration));