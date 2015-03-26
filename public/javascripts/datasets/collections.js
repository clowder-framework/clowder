/**
 * Created by lmarini on 3/25/15.
 */
$("#collectionAddSelect").combobox();

function selectClickChange() {
    var theSelectedColl = document.getElementById("collectionAddSelect");
    document.getElementById("selectCollectionInput").value = theSelectedColl.options[theSelectedColl.selectedIndex].text;
}
document.getElementById("collectionAddSelect").onchange = selectClickChange;

$('body').on('click','#addCollBtn',function(e){
    if($("#doesNotExistErrorCollections").css('display') == 'block')
        return;
    if($("#selectingInputCollections").val() == ""){
        $("#doesNotExistErrorCollections").css('display','block');
        return;
    }

    var selectedId = $("#collectionAddSelect").val();
    var selectedName = $("#collectionAddSelect option:selected").text();
    var request = $.ajax({
        type: 'POST',
        url: window.location.protocol + "//" + window.location.hostname + (window.location.port ? ':' + window.location.port: '')+"/api/collections/"+selectedId+"/datasets/"+"@dataset.id"
    });
    request.done(function (response, textStatus, jqXHR) {
        console.log ( "Response " + response ) ;
        $ ( "#collectionAddSelect option[value=" + selectedId + "]" ).remove ( ) ;
        $ ( "#selectingInputCollections" ).val ( "" ) ;
        var i = 1 ;
        var tableLength = $ ( "#collTable tbody tr" ).length ;
        while ( i <= tableLength && $ ( "#collTable tbody tr:nth-child(" + i + ") td:nth-child(1) a" ).text ( ) < selectedName )
            i ++ ;
        if ( i <= tableLength ) {
            $ ( "#collTable tbody tr:nth-child(" + i + ")" ).before ( "<tr><td><a href='" + window.location.protocol + "//" + window.location.hostname + ( window.location.port ? ':' + window.location.port : '' ) + "/collection/" + selectedId + "'>" + selectedName + "</a>"
                + "&nbsp;&nbsp;&nbsp;&nbsp;</td><td><a href='#' style='font-style:italic;' onclick='removeCollection(\"" + selectedId + "\",\"" + selectedName + "\",event)'>Remove</a></td></tr>"
            ) ;
        } else if(tableLength > 0) {
            $ ( "#collTable tbody tr:nth-child(" + ( i - 1 ) + ")" ).after ( "<tr><td><a href='" + window.location.protocol + "//" + window.location.hostname + ( window.location.port ? ':' + window.location.port : '' ) + "/collection/" + selectedId + "'>" + selectedName + "</a>"
                + "&nbsp;&nbsp;&nbsp;&nbsp;</td><td><a href='#' style='font-style:italic;' onclick='removeCollection(\"" + selectedId + "\",\"" + selectedName + "\",event)'>Remove</a></td></tr>"
            ) ;
        } else {
            if($("#collTable tbody").length == 0)
                $("#collTable").append("<tbody></tbody>");
            $("#collTable tbody").append("<tr><td><a href='"+window.location.protocol + "//" + window.location.hostname + (window.location.port ? ':' + window.location.port: '')+"/collection/"+selectedId+"'>"+selectedName+"</a>"
                + "&nbsp;&nbsp;&nbsp;&nbsp;</td><td><a href='#' style='font-style:italic;' onclick='removeCollection(\""+selectedId+"\",\""+selectedName+"\",event)'>Remove</a></td></tr>"
            );
        }
    });
    request.fail(function (jqXHR, textStatus, errorThrown){
        console.error("The following error occured: " + textStatus, errorThrown);
        var errMsg = "You must be logged in to add a dataset to a collection.";
        if (!checkErrorAndRedirect(jqXHR, errMsg)) {
            notify("The dataset was not added to the collection due to the following : " + errorThrown, "error");
        }
    });
});

function removeCollection(collectionId, collectionName,event){
    var request = $.ajax({
        type: 'POST',
        url: window.location.protocol + "//" + window.location.hostname + (window.location.port ? ':' + window.location.port: '')+"/api/collections/"+collectionId+"/datasetsRemove/"+"@dataset.id"+"/True"
    });
    request.done(function (response, textStatus, jqXHR){
        console.log("Response " + response);
        $(event.target.parentNode.parentNode).remove();
        var i = 1;
        var selectLength = $("#collectionAddSelect option").length;
        while(i <= selectLength && $("#collectionAddSelect option:nth-child("+i+")").text() < collectionName)
            i++;
        if(i <= selectLength)
            $("#collectionAddSelect option:nth-child("+i+")").before("<option value='"+collectionId+"'>"+collectionName+"</option>");
        else if(selectLength > 0)
            $("#collectionAddSelect option:nth-child("+(i-1)+")").after("<option value='"+collectionId+"'>"+collectionName+"</option>");
        else
            $("#collectionAddSelect").append("<option value='"+collectionId+"'>"+collectionName+"</option>");

        $("#collectionAddSelect").combobox();
    });
    request.fail(function (jqXHR, textStatus, errorThrown){
        console.error("The following error occured: " + textStatus, errorThrown);
        var errMsg = "You must be logged in to remove a dataset from a collection.";
        if (!checkErrorAndRedirect(jqXHR, errMsg)) {
            notify("The dataset was not removed from the collection due to : " + errorThrown, "error");
        }
    });
}