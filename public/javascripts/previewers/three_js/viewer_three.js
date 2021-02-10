/****************************************************************
 * @file viewer_three.js
 * Creating a 3D object loader using the three.js library
 *
 * Language: JavaScript
 *
 * Chrysovalantis Constantinou
 * Maria-Spyridoula Tzima
 *
 * The Cyprus Institute
 *
 * + 11/03/20 (cc): Created.
 * + 11/21/20 (cc): Code development started
 * + 02/08/21 (cc): Ability to load from url added.
 *                  First functional version

****************************************************************/

/****************************************************************
  Wish list:

  1) Become able to upload zip files into clowder which will be 
  automatically unzipped. Subsequently, the files they contain will be
  accessible to the previewer. This is necessary for 3D models which come
  with a texture file.

  2) Figure out how to load FBX files with texture files manually.
  Note that the FBXLoader() loads everything automatically, if they
  are downloaded from sketchfab. For the GLTF loader, I think the BIN file contains information
  about how the textures are loaded.

  3) Figure out how the user will interact with the model smoothly using
  OrbitControls().

  4) Figure out how to extract the dimensions of the model and then adjust
  the dimensions so that the model displays properly in the browser. (Some models
  display really big or really small).

  5) For the moment, we are loading the three.js files from the folder js. Specifically,
  I found the files "three.min.js", "FBXloader.js", "OrbitControls.js" from
  https://cdn.jsdelivr.net/npm/three@0.117.1/.
  However, the three.js library is available online and we should gradually switch to loading
  the library from an online server (preferably an https server, in a fashion similar to what's
  done in "viewer_hop.js").

  6) So far we are able to load FBX models using the FBX Loader. I believe we can easily
  load 3D models which come in a different format using for example the GLTFLoader. We need
  to extract the extension of the file and depending of the type of the file, we can use the 
  appropriate loader. This is exactly what we are doing in viewer_hop.js lines 61-70.

  7) Add the option to go full screen, and add additional tools.

  Note: You can come up with your own ideas about how to attack these issues, add your own
  items in the wish list, and whenever you make changes to the code, you can add them on the 
  top like I do. Example: adding something + 01/24/21 (cc) Added smthg. Removing smthg
  - 01/24/21 (cc) Removed smthg. (Your initials are mt). Finally, once we have something 
  functional, we can create a pull request with clowder and contribute our code. Also, we need
  to add you into the list of contributors. :)

****************************************************************/

(function ($, Configuration) {

    let useTab = Configuration.tab;
    let referenceUrl = Configuration.url;
    let previewer = Configuration.previewer;

    // The following variables are not needed for now

    //let fileName = $('#file-name-title').text().trim();
    //let fileNameExtension = fileName.substr(fileName.length - 3);
    //let fileType;

    // This is a trick I use to make the console.log function to work

    console.log = console.log || function(message) { alert(message);};

    console.warn = console.warn || function(message) { alert(message);};

    console.error = console.error || function(message) { alert(message);};

    // scripts is an array containing three library files which are found under
    // the js folder. We use various functions from those library files
    // to load our model.

    // These libraries can be found online and eventually we should load
    // them from a server I think.

    let scripts =[ "three.min.js",  "stats.min.js", "OrbitControls.js", "FBXLoader.js", "fflate.min.js" ];

    // We use the for loop to load the files into our program

    for (index = 0; index < scripts.length; index++) {
        let s = document.createElement("script");
        s.type = "text/javascript";
        s.src = previewer + "/js/" + scripts[index];
        $(useTab).append(s);
       }

    // $(document.ready()) is a jQuery function inside which you can call other functions
    // to run your program

     $(document).ready(function(){
         init(referenceUrl);
         animate();
     });

}(jQuery, Configuration));

let camera, scene, renderer, stats;

const clock = new THREE.Clock();

let mixer;

let previewerWidth = 640;
let previewerHeight = 480;

function init(urlAddress) {

    const container = document.getElementById(Configuration.tab.replace("#",""));

    camera = new THREE.PerspectiveCamera( 45, window.innerWidth / window.innerHeight, 1, 2000 );
    camera.position.set( 100, 200, 300 );

    scene = new THREE.Scene();
    scene.background = new THREE.Color( 0xa0a0a0 );
    scene.fog = new THREE.Fog( 0xa0a0a0, 200, 1000 );

    const hemiLight = new THREE.HemisphereLight( 0xffffff, 0x444444 );
    hemiLight.position.set( 0, 200, 0 );
    scene.add( hemiLight );

    const dirLight = new THREE.DirectionalLight( 0xffffff );
    dirLight.position.set( 0, 200, 100 );
    dirLight.castShadow = true;
    dirLight.shadow.camera.top = 180;
    dirLight.shadow.camera.bottom = - 100;
    dirLight.shadow.camera.left = - 120;
    dirLight.shadow.camera.right = 120;
    scene.add( dirLight );

    // scene.add( new THREE.CameraHelper( dirLight.shadow.camera ) );

    // ground
    const mesh = new THREE.Mesh( new THREE.PlaneGeometry( 2000, 2000 ), new THREE.MeshPhongMaterial( { color: 0x999999, depthWrite: false } ) );
    mesh.rotation.x = - Math.PI / 2;
    mesh.receiveShadow = true;
    scene.add( mesh );

    const grid = new THREE.GridHelper( 2000, 20, 0x000000, 0x000000 );
    grid.material.opacity = 0.2;
    grid.material.transparent = true;
    scene.add( grid );

    const loader = new THREE.FBXLoader();

    // Added on 11/02/2021

    let  desiredScale = 240;
    let scaleV3 = new THREE.Vector3().setScalar(desiredScale);

    //

    loader.setPath(urlAddress);

    loader.load( '', function ( object ) {

        mixer = new THREE.AnimationMixer( object );

        const action = mixer.clipAction( object.animations[ 0 ] );
        action.play();



        // Added on 11/02/2021

        let box = new THREE.Box3();
        box.setFromObject(object);

        let size = new THREE.Vector3();
        box.getSize(size);

        let center = new THREE.Vector3();
        box.getCenter(center);

        let scaleTemp = new THREE.Vector3().copy(scaleV3).divide(size);
        let scale = Math.min(scaleTemp.x, Math.min(scaleTemp.y, scaleTemp.z));


        object.scale.setScalar(scale);
        object.position.sub(center.multiplyScalar(scale/10));


        //

        object.traverse( function ( child ) {

            if ( child.isMesh ) {

                child.castShadow = true;
                child.receiveShadow = true;

            }

        } );

        scene.add( object );

    } );

    renderer = new THREE.WebGLRenderer( { antialias: true } );
    renderer.setPixelRatio( window.devicePixelRatio );
    renderer.setSize( previewerWidth, previewerHeight );
    renderer.shadowMap.enabled = true;

    const controls = new THREE.OrbitControls( camera, renderer.domElement );
    controls.target.set( 0, 100, 0 );
    controls.update();
    container.appendChild(renderer.domElement);

    window.addEventListener( 'resize', onWindowResize );

    // stats
    stats = new Stats();
    //container.appendChild( stats.dom );


}

function onWindowResize() {

    camera.aspect = window.innerWidth / window.innerHeight;
    camera.updateProjectionMatrix();

    renderer.setSize( window.innerWidth, window.innerHeight );

}

//

function animate() {

    requestAnimationFrame( animate );

    const delta = clock.getDelta();

    if ( mixer ) mixer.update( delta );

    renderer.render( scene, camera );

    stats.update();

}

