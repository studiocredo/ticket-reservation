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

$(document).ready ->
  $input = $("input.member-sel2")
  route = jsRoutes.controllers.GroupDetails.ajaxMembers($input.attr("data-group-id"))
  simpleSelect2($input, route, 10, "Select member to add")


$(document).ready ->
  $input = $("input.class-sel2")
  route = jsRoutes.controllers.MemberDetails.ajaxGroups($input.attr("data-member-id"))
  simpleSelect2($input, route, 10, "Select class")
