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
 * 
 * + 07/05/22 (cc): Bug fixes

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

console.log("This is the 3D viewer");

/**
 * Jquery 
 */

let loader;
let useTab;
let referenceUrl;
let previewer;
let fileName;
let fileNameExtension;

let scripts = [
    "three.min.js",
    "stats.min.js",
    "OrbitControls.js",
    "FBXLoader.js",
    // "GLTFLoader.js",
    // "DRACOLoader.js",
    "fflate.min.js"
];

(function ($, Configuration) {
    useTab = Configuration.tab;
    referenceUrl = Configuration.url;
    previewer = Configuration.previewer;

    fileName = $('#file-name-title').text().trim();
    fileNameExtension = fileName.split('.').pop();

    $(useTab).append('<link rel="stylesheet" type="text/css" href = "/assets/javascripts/previewers/three_js/style.css" >');

    /**
     * Decipher whether the uploaded file is an 
     * FBX or a GLTF (GLB) file and load the appropriate model loader 
     * For the special case of GLTF/GLB model usel Google's
     * model viewer 
     */

    for (let index = 0; index < scripts.length; index++) {
        let s = document.createElement("script");
        s.type = "text/javascript";
        s.src = previewer + "/js/" + scripts[index];
        $(useTab).append(s);
    }

    if (fileNameExtension === 'fbx') {

        $(useTab).append('<section id="loading-screen">\n' +
            '\n' +
            '\t<div id="loader"></div>\n' +
            '\n' +
            '</section>')

        $(document).ready(function () {
            init(referenceUrl);
            animate();
        });
    }

    if (fileNameExtension === 'gltf' || fileNameExtension === 'glb') {

        $(useTab).append('<script nomodule src="https://www.unpkg.com/@google/model-viewer@1.4.1/dist/model-viewer-legacy.js"></script>')


        $(useTab).append("<model-viewer id='mv-demo' ar ar-modes='scene-viewer webxr' shadow-intensity='1'  auto-rotate camera-controls enable-pan src='" + referenceUrl + "'></model-viewer>")
    }

}(jQuery, Configuration));

/**
 * Three.js
 */

let camera, scene, renderer, stats;
let mixer;
let previewerWidth = 640;
let previewerHeight = 480;
const clock = new THREE.Clock();

function init(urlAddress) {
    const container = document.getElementById(Configuration.tab.replace("#", ""));


    /**
     * Renderer
     */

    renderer = new THREE.WebGLRenderer(
        {
            antialias: false
        }
    );

    //renderer.physicallyCorrectLights = true;
    renderer.outputEncoding = THREE.sRGBEncoding;
    renderer.setClearColor(0xcccccc);
    renderer.setPixelRatio(window.devicePixelRatio);
    renderer.setSize(previewerWidth, previewerHeight);
    renderer.shadowMap.enabled = true;

    /**
     * Camera
     */

    camera = new THREE.PerspectiveCamera(55, previewerWidth / previewerHeight, 0.1, 1000);
    //camera.position.set(0, 20, 100);

    /**
     * Scene
     */

    scene = new THREE.Scene();
    scene.background = new THREE.Color("White");
    //scene.fog = new THREE.Fog( 0xa0a0a0, 200, 1000 );

    /**
     * Lights
     */

    const hemiLight1 = new THREE.HemisphereLight(0xffffff, 0x444444);
    const hemiLight2 = new THREE.HemisphereLight(0xffffff, 1);
    //hemiLight.position.set(0, -20, 0);
    scene.add(hemiLight1, hemiLight2);

    const ambientLight = new THREE.AmbientLight(0xffffff, 1);
    //ambientLight.position.set(0, 200, 0);
    scene.add(ambientLight);

    const dirLight = new THREE.DirectionalLight(0xffffff, 0.8);
    //dirLight.position.set(0, 4, 4);

    // const helper = new THREE.DirectionalLightHelper(dirLight, 2);
    // scene.add(helper);
    dirLight.castShadow = true;
    // dirLight.shadow.camera.top = 180;
    // dirLight.shadow.camera.bottom = -100;
    // dirLight.shadow.camera.left = -120;
    // dirLight.shadow.camera.right = 120;
    scene.add(dirLight);

    /**
     * Orbit controls
     */

    const controls = new THREE.OrbitControls(camera, renderer.domElement);
    //controls.target.set(0, 100, 0);
    controls.update();
    container.appendChild(renderer.domElement);

    // scene.add( new THREE.CameraHelper( dirLight.shadow.camera ) );

    // ground
    // const mesh = new THREE.Mesh(
    //     new THREE.PlaneGeometry(1000, 1000),
    //     new THREE.MeshPhongMaterial({ color: 0x999999, depthWrite: false })
    // );
    // mesh.rotation.x = - Math.PI / 2;
    // mesh.receiveShadow = true;
    // mesh.material.transparent = false;
    // scene.add(mesh);

    // const grid = new THREE.GridHelper(1000, 100, 0x000000, 0x000000);
    // grid.material.opacity = 0.3;
    // grid.material.transparent = true;
    // scene.add(grid);

    /**
     * Loading manager
     */

    // The loading manager is needed for the loading screen

    const loadingManager = new THREE.LoadingManager(() => {
        const loadingScreen = document.getElementById('loading-screen');
        loadingScreen.classList.add('fade-out');
        loadingScreen.addEventListener('transitionend', onTransitionEnd);
    });

    loader = new THREE.FBXLoader(loadingManager);

    // Set loader's path to the url address of the file

    loader.setPath(urlAddress);

    //loader.setPath("/assets/javascripts/previewers/three_js/models/chimpanzee/source/");

    loader.load('', function (object) {

        if (fileNameExtension === 'gltf' || fileNameExtension === 'glb') {
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

        //controls.reset();

        object.position.x += (object.position.x - center.x);
        object.position.y += (object.position.y - center.y);
        object.position.z += (object.position.z - center.z);

        controls.maxDistance = size * 10;
        controls.minDistance = size;

        camera.near = size / 100;
        camera.far = size * 100;
        camera.updateProjectionMatrix();
        camera.position.copy(center);
        camera.position.x += size / 2.0;
        camera.position.y += size / 5.0;
        camera.position.z += size / 2.0;
        camera.lookAt(center);
        controls.update();

        scene.add(object);
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