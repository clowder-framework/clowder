/**
 * Source: http://berzniz.com/post/24743062344/handling-handlebars-js-like-a-pro
 */
Handlebars.getTemplate = function(path) {
    if (Handlebars.templates === undefined || Handlebars.templates[path] === undefined) {
        jQuery.ajax({
            url : path + '.handlebars',
            crossDomain: true,
            success : function(data) {
                if (Handlebars.templates === undefined) {
                    Handlebars.templates = {};
                }
                Handlebars.templates[path] = Handlebars.compile(data);
            },
            async : false
        });
    }
    return Handlebars.templates[path];
};