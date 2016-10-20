(function ($, Configuration) {
    var useTab = Configuration.tab;
    var referenceUrl = Configuration.url;

    $(useTab).append(
        "<audio controls><source src='" + referenceUrl + "'></audio>"
    );

}(jQuery, Configuration));
