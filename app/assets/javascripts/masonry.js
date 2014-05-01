require.config({
    baseUrl: 'assets/javascripts',
    paths: {
        'jquery': 'jquery-1.10.2',
        'masonry': 'lib/masonry.pkgd.min',
        'imagesloaded': 'lib/imagesloaded.pkgd.min'
    }
});

//require(["jquery"],function($) {
//	console.log('masonry');
//});

require( ['jquery', 'masonry'], function( $, Masonry ) {
  console.log("masonry");
  new Masonry( '#masonry', {
    itemSelector: '.post-box',
    columnWidth: '.post-box',
    transitionDuration: 4
  });
});

//require(['require',"jquery","masonry"],function(require, $,imagesLoaded, Masonry) {
//  // require jquery-bridget, it's included in masonry.pkgd.js
//  require( [ 'jquery-bridget/jquery.bridget' ],
//  function() {
//    // make Masonry a jQuery plugin
//    $.bridget( 'masonry', Masonry );
//    // layout Masonry again after all images have loaded
//    imagesLoaded( '#masonry', function() {
//      alert("masonry");
//      $container.masonry({
//        itemSelector: '.post-box',
//        columnWidth: '.post-box',
//        transitionDuration: 4
//      });
//    });
//  });
//});


//// require the require function
//requirejs( [ 'require', 'jquery', 'masonry' ],
//  function( require, $, Masonry ) {
//    // require jquery-bridget, it's included in masonry.pkgd.js
//    require( [ 'jquery-bridget/jquery.bridget' ],
//    function() {
//      // make Masonry a jQuery plugin
//      $.bridget( 'masonry', Masonry );
//      // now you can use $().masonry()
//      $('#container').masonry({
//        itemSelector: '.post-box',
//        columnWidth: '.post-box',
//        transitionDuration: 4});
//    }
//  );
//});