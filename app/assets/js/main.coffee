window.credo =
    flash: (type, msg) ->
      $.bootstrapGrowl msg,
        ele: "#content"
        type: 'success',
        align: 'right',
        width: 'auto',
        allow_dismiss: true

