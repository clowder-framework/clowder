/**
 * Created by indiragp on 5/15/15.
 */
function removeRole(roleId, url)
{
    if(url === undefined) url = "/admin/roles";

    var request = jsRoutes.controllers.Admin.removeRole(roleId).ajax({
        type: 'DELETE'
    });

    request.done(function (response, textStatus, jqXHR){
        console.log("Response " + response);
        window.location.href=url;
    });

    request.fail(function (jqXHR, textStatus, errorThrown){
        console.error("The following error occured: " + textStatus, errorThrown);
        var errMsg = "You must be logged in and be an administrator to remove a role from the system.";
        if (!checkErrorAndRedirect(jqXHR, errMsg)) {
            notify("The role was not deleted from the system due to : " + errorThrown, "error");
        }
    });
}

function editRole(role, url)
{
    if(url === undefined) url = "/admin/roles";

    var request = jsRoutes.controllers.Admin.editRole(role).ajax({
        type: 'POST'
    });

    request.done(function(response, textStatus, jqXHR){
       console.log("response " + response);
        window.location.href=url;
    });

    request.fail(function(jqXHR, textStatus, errorThrown)
    {
        console.error("The following error occurred" + textStatus, errorThrown);
        var errMsg = "You must be logged in and be an administrator to edit roles.";
        if(!checkErrorAndRedirect(jqXHR, errMsg))
        {
            notify("The role was not updated in the system due to: " + errorThrown, "error");
        }
    })
}