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

(function ($, Configuration) {

    let useTab = Configuration.tab;
    let referenceUrl = Configuration.url;
    let previewer = Configuration.previewer;

    //let fileName = $('#file-name-title').text().trim();
    //let fileNameExtension = fileName.substr(fileName.length - 3);
    //let fileType;

    // This is a trick I use to make the console.log function to work

    console.log = console.log || function (message) { alert(message); };

    console.warn = console.warn || function (message) { alert(message); };

    console.error = console.error || function (message) { alert(message); };

    let scripts = ["three.min.js", "stats.min.js", "OrbitControls.js", "FBXLoader.js", "fflate.min.js"];

    // We use the for loop to load the files into our program

    for (index = 0; index < scripts.length; index++) {
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

    camera = new THREE.PerspectiveCamera(45, window.innerWidth / window.innerHeight, 1, 2000);
    camera.position.set(100, 200, 300);

    scene = new THREE.Scene();
    scene.background = new THREE.Color(0xa0a0a0);
    scene.fog = new THREE.Fog(0xa0a0a0, 200, 1000);

    const hemiLight = new THREE.HemisphereLight(0xffffff, 0x444444);
    hemiLight.position.set(0, 200, 0);
    scene.add(hemiLight);

    const dirLight = new THREE.DirectionalLight(0xffffff);
    dirLight.position.set(0, 200, 100);
    dirLight.castShadow = true;
    dirLight.shadow.camera.top = 180;
    dirLight.shadow.camera.bottom = - 100;
    dirLight.shadow.camera.left = - 120;
    dirLight.shadow.camera.right = 120;
    scene.add(dirLight);

    // scene.add( new THREE.CameraHelper( dirLight.shadow.camera ) );

    // ground
    const mesh = new THREE.Mesh(new THREE.PlaneGeometry(2000, 2000), new THREE.MeshPhongMaterial({ color: 0x999999, depthWrite: false }));
    mesh.rotation.x = - Math.PI / 2;
    mesh.receiveShadow = true;
    scene.add(mesh);

    const grid = new THREE.GridHelper(2000, 20, 0x000000, 0x000000);
    grid.material.opacity = 0.2;
    grid.material.transparent = true;
    scene.add(grid);

    const loader = new THREE.FBXLoader();

    loader.setPath(urlAddress);

    loader.load('', function (object) {

        if (object.animations[0]) {
            mixer = new THREE.AnimationMixer(object);

            const action = mixer.clipAction(object.animations[0]);
            action.play();
        }

        object.traverse(function (child) {

            if (child.isMesh) {

                child.castShadow = true;
                child.receiveShadow = true;

            }

        });

        scene.add(object);

    });

    renderer = new THREE.WebGLRenderer({ antialias: true });
    renderer.setPixelRatio(window.devicePixelRatio);
    renderer.setSize(previewerWidth, previewerHeight);
    renderer.shadowMap.enabled = true;

    const controls = new THREE.OrbitControls(camera, renderer.domElement);
    controls.target.set(0, 100, 0);
    controls.update();
    container.appendChild(renderer.domElement);

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

//

function animate() {

    requestAnimationFrame(animate);

    const delta = clock.getDelta();

    if (mixer) mixer.update(delta);

    renderer.render(scene, camera);

    stats.update();

}

