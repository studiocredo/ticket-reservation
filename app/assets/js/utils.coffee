log = (args...) ->
    console.log.apply console, args if console.log?

simpleSelect2 = ($input, route, perPage, placeHolder) ->
    $input.select2
        placeholder: placeHolder
        minimumInputLength: 2
        multiple: false
        ajax:
            url: route.url
            type: route.method
            dataType: "json"
            quietMillis: 100
            data: (term, page) ->
                q: term
                limit: perPage
                page: page

            results: (data, page) ->
                more = (page * perPage) < data.total
                results: data.results
                more: more

        dropdownCssClass: "bigdrop"


window.credo =
    flash: (type, msg) ->
        $.bootstrapGrowl msg,
            ele: "#content"
            type: 'success',
            align: 'right',
            width: 'auto',
            allow_dismiss: true



$(document).ready ->
    $("[data-toggle=offcanvas]").click ->
        $(".row-offcanvas").toggleClass "active"

$(document).ready ->
    $('.pull-down').each ->
 	    $(this).css('margin-top', $(this).parent().height()-$(this).height())

$(document).ready ->
 	$('.pull-middle').each ->
     	$(this).css('margin-top', ($(this).parent().height()-$(this).height()) /2 )
