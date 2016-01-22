// Following code will make all elements that have class clipboard be able to be clicked on and
// copy the text that is in data-copy to the clipboard. An example of how to use this:
// <a class="btn btn-link btn-sm clipboard" data-copy="hello world"><span class="glyphicon glyphicon-copy"></span></a></td></tr>
$(function() {
    $(".clipboard").click(function() {
        if (document.queryCommandSupported('copy')) {
            var input = document.createElement('textarea');
            $(this).append(input);
            input.value = $(this).data("copy");
            input.focus();
            input.select();
            if (!document.execCommand('Copy')) {
                $(".clipboard").attr('disabled','disabled');
            }
            input.remove();
        } else {
            $(".clipboard").attr('disabled','disabled');
        }
    });
});
