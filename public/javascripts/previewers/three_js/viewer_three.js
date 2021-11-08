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
 * + 11/08/21 (cc): Added loading screen and ability
 *                  to load gltf files

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

 3) For the moment, we are loading the three.js files from the folder js. Specifically,
 I found the files "three.min.js", "FBXloader.js", "OrbitControls.js" from
 https://cdn.jsdelivr.net/npm/three@0.117.1/.
 However, the three.js library is available online and we should gradually switch to loading
 the library from an online server (preferably an https server, in a fashion similar to what's
 done in "viewer_hop.js").

 4) Add the option to go full screen, and add additional tools.

 ****************************************************************/

let fileNameExtension;
let loader;

(function ($, Configuration) {
    let useTab = Configuration.tab;
    let referenceUrl = Configuration.url;
    let previewer = Configuration.previewer;

    $(useTab).append('<section id="loading-screen">\n' +
        '\n' +
        '\t<div id="loader"></div>\n' +
        '\n' +
        '</section>')

    $(useTab).append('<link rel="stylesheet" type="text/css" href = "/assets/javascripts/previewers/three_js/loading_screen.css" >');

    let fileName = $('#file-name-title').text().trim();
    fileNameExtension = fileName.split('.').pop();

    let scripts = ["three.min.js", "stats.min.js", "OrbitControls.js", "FBXLoader.js", "GLTFLoader.js", "fflate.min.js"];

    for (let index = 0; index < scripts.length; index++) {
        let s = document.createElement("script");
        s.type = "text/javascript";
        s.src = previewer + "/js/" + scripts[index];
        $(useTab).append(s);
    }

    $(document).ready(function () {
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
    const container = document.getElementById(Configuration.tab.replace("#", ""));

    renderer = new THREE.WebGLRenderer({antialias: false, powerPreference: "high-performance"});
    renderer.setPixelRatio(window.devicePixelRatio);
    renderer.setSize(previewerWidth, previewerHeight);
    renderer.shadowMap.enabled = true;

    camera = new THREE.PerspectiveCamera(30, window.innerWidth / window.innerHeight, 1, 2000000);
    camera.position.set(0, 100, 1250);

    scene = new THREE.Scene();
    scene.background = new THREE.Color(0x444444);
    //scene.fog = new THREE.Fog( 0xa0a0a0, 200, 1000 );

    const hemiLight = new THREE.HemisphereLight(0xffffff, 0x444444);
    hemiLight.position.set(0, 200, 0);
    scene.add(hemiLight);

    const dirLight = new THREE.DirectionalLight(0xffffff);
    dirLight.position.set(0, 200, 100);
    dirLight.castShadow = true;
    dirLight.shadow.camera.top = 180;
    dirLight.shadow.camera.bottom = -100;
    dirLight.shadow.camera.left = -120;
    dirLight.shadow.camera.right = 120;
    scene.add(dirLight);

    const controls = new THREE.OrbitControls(camera, renderer.domElement);
    controls.target.set(0, 100, 0);
    controls.update();
    container.appendChild(renderer.domElement);

    // scene.add( new THREE.CameraHelper( dirLight.shadow.camera ) );

    // ground
    // const mesh = new THREE.Mesh(
    //     new THREE.PlaneGeometry( 4000, 4000 ),
    //     new THREE.MeshPhongMaterial( { color: 0x999999, depthWrite: false } )
    // );
    //
    // mesh.rotation.x = - Math.PI / 2;
    // mesh.receiveShadow = true;
    // scene.add( mesh );
    //
    // const grid = new THREE.GridHelper( 4000, 20, 0x000000, 0x000000 );
    // grid.material.opacity = 0.2;
    // grid.material.transparent = true;
    // scene.add( grid );

    const loadingManager = new THREE.LoadingManager(() => {
        const loadingScreen = document.getElementById('loading-screen');
        loadingScreen.classList.add('fade-out');
        loadingScreen.addEventListener('transitionend', onTransitionEnd);
    });

    if (fileNameExtension === 'fbx') {
        loader = new THREE.FBXLoader(loadingManager);
    }

    if (fileNameExtension === 'gltf') {
        loader = new THREE.GLTFLoader(loadingManager);
    }

    loader.setPath(urlAddress);

    //loader.setPath("/assets/javascripts/previewers/three_js/models/chimpanzee/source/");

    //const texture = new THREE.TextureLoader().setPath('/assets/javascripts/previewers/three_js/models/chimpanzee/source/');

    loader.load('', function (object) {

        if (fileNameExtension === 'gltf') {
            object = object.scene;
        }

        object.traverse(function (child) {

            if (child.isMesh) {

                child.castShadow = true;
                child.receiveShadow = true;

            }

        });

        if (object.animations[0]) {
            mixer = new THREE.AnimationMixer(object);

            const action = mixer.clipAction(object.animations[0]);
            action.play();
        }

        const box = new THREE.Box3().setFromObject(object);
        const size = box.getSize(new THREE.Vector3()).length();
        const center = box.getCenter(new THREE.Vector3());

        controls.reset();

        object.position.x += (object.position.x - center.x);
        object.position.y += (object.position.y - center.y);
        object.position.z += (object.position.z - center.z);

        controls.maxDistance = size * 10;
        camera.near = size / 100;
        camera.far = size * 100;

        camera.updateProjectionMatrix();

        scene.add(object);


        // object.traverse( function ( child ) {
        //
        //     if ( child.isMesh ) {
        //         child.castShadow = true;
        //         child.receiveShadow = true;
        //
        //         child.material = new THREE.MeshBasicMaterial({map: texture.load('EYESLP_defaultMat_BaseColor.png')});
        //     }
        //
        //     if ( child.isMesh ) {
        //         child.material = new THREE.MeshBasicMaterial({map: texture.load('EYESLP_defaultMat_Normal.png')});
        //     }
        //
        //     if ( child.isMesh ) {
        //         child.material = new THREE.MeshBasicMaterial({map: texture.load('LOW_POLY_defaultMat_AO.png')});
        //     }
        //     //
        //     if ( child.isMesh ) {
        //         child.material = new THREE.MeshBasicMaterial({map: texture.load('LOW_POLY_defaultMat_BaseColor.png')});
        //     }
        //     //
        //     if ( child.isMesh ) {
        //         child.material = new THREE.MeshBasicMaterial({map: texture.load('LOW_POLY_defaultMat_Metallic.png')});
        //     }
        //     //
        //     if ( child.isMesh ) {
        //         child.material = new THREE.MeshBasicMaterial({map: texture.load('LOW_POLY_defaultMat_Roughness.png')});
        //     }
        //
        //     if ( child.isMesh ) {
        //         child.material = new THREE.MeshBasicMaterial({map: texture.load('Pruyebanm.jpg')});
        //     }
        //
        // } );
        //
        // scene.add( object );

    });


    window.addEventListener('resize', onWindowResize);

    // stats
    stats = new Stats();
    //container.appendChild( stats.dom );

}

function onWindowResize() {

    camera.aspect = window.innerWidth / window.innerHeight;
    camera.updateProjectionMatrix();

    renderer.setSize(window.innerWidth, window.innerHeight);

}

function onTransitionEnd(event) {

    event.target.remove();

}

function animate() {
    requestAnimationFrame(animate);

    const delta = clock.getDelta();

    if (mixer) mixer.update(delta);

    renderer.render(scene, camera);

    stats.update();
}