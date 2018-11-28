(function($, Configuration) {
    var initialTab = Configuration.tab;

    console.log("Zipfile previewer for " + Configuration.id);

    // load the zip library
    var s = document.createElement("script");
    s.type = "text/javascript";
    s.src = Configuration.previewer + "/../../zip.js";
    $(Configuration.tab).append(s);

    zip.workerScriptsPath = Configuration.previewer + '../../../';

    var btn = document.createElement("button");
    btn.id = "zipLoadBtn";
    btn.className = "btn btn-link";
    btn.style["margin"] = "20vh auto 20vh auto";
    btn.style["display"] = "block";
    btn.innerText = "Click to Preview Zip File Contents";

    $(document).ready(function() {
        $(initialTab).append(btn);
        $("#zipLoadBtn").click(function () {
            loadZipContents(initialTab);
            $("zipLoadBtn").hide();
        });
    });

    var loadZipContents = function() {
        console.log("Button clicked!");
        $(initialTab).append("<link rel='stylesheet' href='/assets/stylesheets/glyphicon-animations.css'>")
        $(initialTab).append("<span class='glyphicon glyphicon-refresh glyphicon-animation-spin glyphicon-2x'></span> Zip file contents are loading...");

        var xhr = new XMLHttpRequest();
        xhr.tab = initialTab;
        xhr.onreadystatechange = function (response) {
            if (this.readyState == 4 && this.status == 200) {
                var data = this.response;
                var box = this.tab;

                zip.createReader(new zip.BlobReader(data), function (reader) {
                    // get all entries from the zip
                    reader.getEntries(function (entries) {
                        if (entries.length) {
                            var content = "</br><p>" + entries.length + " contents listed in zip file:</p></br>";
                            ;
                            content += "<ul>"
                            for (var e = 0; e < entries.length; e++) {
                                content += "<li>" + entries[e]['filename'] + "</li>"
                            }
                            content += "</ul>";
                            $(box).html(content);
                        }
                    });
                });
            }
        };
        xhr.open('GET', Configuration.url);
        xhr.responseType = 'blob';
        xhr.send();
    }

}(jQuery, Configuration));