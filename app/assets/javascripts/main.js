require.config({
    baseUrl: 'assets/javascripts',
    paths: {
        'jquery': 'jquery-1.10.2',
        'masonry': '/lib/masonry.pkgd.min.js',
        'imagesloaded': '/lib/imagesloaded.pkgd.min.js'
    }
});

require(["jquery","helper/lib"],function($,l) {
	var s = l.sum(4, 5);
	console.log(s);
//	alert(s);
});