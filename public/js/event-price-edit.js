(function () {
    var fixPricingCategoryIndexes = function() {
        $('.pricing fieldset').each(function(index, fieldset){
            $(fieldset).find('div.form-group').each(function(i, element){
                element.id = element.id.replace(/pricing_[\d+]_/, 'pricing_' + index + '_');
            });
            $(fieldset).find('label').each(function(i, element){
                var attr = $(element).attr('for');
                $(element).attr('for', attr.replace(/pricing_[\d+]_/, 'pricing_' + index + '_'));
            });
            $(fieldset).find('input').each(function(i, element){
                element.id = element.id.replace(/pricing_[\d+]_/, 'pricing_' + index + '_');
                element.name = element.name.replace(/pricing\[[\d+]\]/, 'pricing[' + index + ']');
            });
        })

        if ($('.pricing fieldset').length <= 1) {
            $('.pricing .pricing-delete').each(function(i, element){
                $(element).addClass('hidden');
            });
        } else {
            $('.pricing .pricing-delete').each(function(i, element){
                $(element).removeClass('hidden');
            });
        }
    };

    $('.pricing').on('click', '.pricing-delete button', function () {
        $(this).parent().parent().parent().parent().remove();

        fixPricingCategoryIndexes();
    });

    $('.pricing-add').click(function () {
        var category = $(this).parent().parent().prev().clone();
        $(category).find('input').each(function(i, input){
            $(input).val('');
        });

        $(this).parent().parent().before(category);

        fixPricingCategoryIndexes();
    });

    $('form').submit(function () {
        fixPricingCategoryIndexes();
    });
})();