log = (args...) ->
  console.log.apply console, args if console.log?

$(document).ready ->
  per_page = 10
  $input = $("input.member-sel2")
  groupId = $input.attr("data-group-id")
  route = jsRoutes.controllers.GroupDetails.ajaxMembers(groupId)
  $input.select2
    placeholder: "Select member to add"
    minimumInputLength: 2
    multiple: false
    ajax:
      url: route.url
      type: route.method
      dataType: "json"
      quietMillis: 100
      data: (term, page) ->
        q: term
        limit: per_page
        page: page

      results: (data, page) ->
        more = (page * per_page) < data.total
        results: data.results
        more: more

    dropdownCssClass: "bigdrop"
