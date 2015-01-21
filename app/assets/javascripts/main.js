/**
 * Created by lmarini on 1/21/15.
 */
console.log("in main");
function notify(text, type) {
    noty({
        layout: 'topCenter',
        theme: 'relax',
        type: type,
        text: text
    });
}

window['notify'] = notify;