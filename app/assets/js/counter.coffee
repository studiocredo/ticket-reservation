window.CounterInput ||= {}

CounterInput.increment = (elem, max = 9) ->
    $(elem).val( (i, oldval) -> Math.min(++oldval,max) )

CounterInput.decrement = (elem, min = 0) ->
    $(elem).val( (i, oldval) -> Math.max(--oldval,min) )

CounterInput.isNumberKey = (evt) ->
  charCode = if evt.which then evt.which else event.keyCode
  if (charCode > 31 && (charCode < 48 || charCode > 57)) then false else true