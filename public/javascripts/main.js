/**
 * Created by lmarini on 1/21/15.
 */
function notify(text, type, logConsole, timeout) {
    // default parameters
    if (typeof(text)==='undefined') text = "Notification improperly created";
    if (typeof(type)==='undefined') type = "alert";
    if (typeof(logConsole)==='undefined') logConsole = false;
    if (typeof(timeout)==='undefined') timeout = false;


    noty({
        layout: 'topCenter',
        theme: 'relax',
        type: type,
        text: text,
        timeout: timeout
    });

    if (logConsole) console.log(type + ": " + text);
}

window['notify'] = notify;