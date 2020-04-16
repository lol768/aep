import $ from 'jquery';

$(() => {
  const $durationField = $('select[name=durationMinutes]');

  $('#platform_field').on('change', (e) => {
    const count = $('#platform_field').find('input:checkbox:checked').length;
    if (count > 2) {
      $('#platformModal').modal('show');
      e.target.checked = false;
    } else {
      const isOnlineExams = $('#platform_OnlineExams').is(':checked');
      const hideUrlField = isOnlineExams && count === 1;
      const urlFields = $('input[name^="urls."]');
      urlFields.prop('disabled', true).closest('.form-group').addClass('hidden');

      $('#url_field').toggleClass('hide', hideUrlField)
        .find('input').prop('disabled', hideUrlField);
      const checkedPlatforms = $('input[name="platform[]"]:checked');
      checkedPlatforms.each((i, input) => {
        urlFields.filter((j, urlInput) => $(urlInput).data('platform') === input.value)
          .prop('disabled', false)
          .closest('.form-group')
          .removeClass('hidden');
      });
    }
  }).trigger('change');

  $('#assessmentType_field').on('change', () => {
    const isBespoke = $('#assessmentType_Bespoke').is(':checked');

    if (isBespoke) {
      $('#platform_OnlineExams').prop('disabled', true).prop('checked', false);
    } else {
      $('#platform_OnlineExams').prop('disabled', false);
    }

    const validDurations = $('input[name=assessmentType]:checked').data('valid-durations') || [];
    $durationField.closest('.form-group').toggle(validDurations.length > 0);
    $durationField.find('option').each((_, option) => {
      const $option = $(option);
      const isValidOption = validDurations.includes(Number.parseInt($option.val(), 10));
      $option.toggle(isValidOption).prop('disabled', !isValidOption);
    });
  }).trigger('change');
});
