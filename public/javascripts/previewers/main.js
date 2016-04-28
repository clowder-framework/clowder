/**
 * Created by lmarini on 3/18/15.
 *
 * Moved here from file.scala.html.
 * TODO: Will try to reuse with web sockets to update previewers on page as they become available.
 *
 */
function addPreview(pv, fileId){
    String.prototype.endsWith = function(str)
    {
        var lastIndex = this.lastIndexOf(str);
        return (lastIndex != -1) && (lastIndex + str.length == this.length);
    }

    var prevsOfFile = parseInt($("#filePrevContainer_" + fileId).attr('data-previewsCount'))
    var newTabLink = document.createElement('a');
    newTabLink.setAttribute('href', '#previewer_' + fileId +  '_' + (prevsOfFile + 1));
    $("#filePrevContainer_" + fileId).attr('data-previewsCount', '' + (prevsOfFile + 1));
    newTabLink.setAttribute('data-toggle', 'tab');
    newTabLink.innerHTML= pv.p_id;
    var newListItem = document.createElement('li');
    newListItem.appendChild(newTabLink);
    $("#myTab_" + fileId).append(newListItem);

    var newPrevDiv = document.createElement('div');
    newPrevDiv.setAttribute('class', 'tab-pane previewDiv');
    newPrevDiv.setAttribute('id', 'previewer_' + fileId +  '_' + (prevsOfFile + 1));
    newPrevDiv.setAttribute('data-previewId', pv.pv_id);
    newPrevDiv.setAttribute('data-previewerId', pv.p_id);
    $("#previewsContent_" + fileId).append(newPrevDiv);

    //Previewer config vars
    Configuration.tab = '#previewer_' + fileId +  '_' + (prevsOfFile + 1);
    Configuration.url = pv.pv_route;
    Configuration.fileid = pv.pv_id;
    Configuration.previewer = pv.p_path;
    if(pv.p_id === "Thumbnail"){
        Configuration.fileType = pv.pv_contenttype;
        Configuration.fileSize = pv.pv_length;
    }
    else if(pv.p_id === "X3d"){
        Configuration.annotationsEditPath = pv.pv_annotationsEditPath;
        Configuration.annotationsListPath = pv.pv_annotationsListPath;
        Configuration.annotationsAttachPath = pv.pv_annotationsAttachPath;
        if("@file.filename".endsWith(".ptm") || "@file.contentType".indexOf("ptmimages") != -1)
            Configuration.wasPTM = 'true';
        else
            Configuration.wasPTM = 'false';
        Configuration.calledFrom = "file";
    }
    ////

    var s = document.createElement("script");
    s.type = "text/javascript";
    s.src = pv.p_path + "/" + pv.p_main;
    $("#previewsContent_" + fileId).append(s);

    if(prevsOfFile == 1){
        $("#myTab_" + fileId).attr('style', 'display:block;');
    }

    $('#myTab_' + fileId + ' a:first').tab('show');
}