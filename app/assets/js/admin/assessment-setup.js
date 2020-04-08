import $ from 'jquery';

$(() => {
  $('#platform_field').find('input:checkbox').on('change', (e) => {
    const count = $('#platform_field').find('input:checkbox:checked').length;
    if (count > 2) {
      $('#platformModal').modal('show');
      e.target.checked = false;
    } else {
      const isOnlineExams = $('#platform_OnlineExams').is(':checked');
      const hideUrlField = isOnlineExams && count === 1;

      $('#url_field').toggleClass('hide', hideUrlField)
        .find('input').prop('disabled', hideUrlField);
    }
  }).trigger('change');

  $('#assessmentType_field').find('input:radio').on('change', () => {
    const isBespoke = $('#assessmentType_Bespoke').is(':checked');

    if (isBespoke) {
      $('#platform_OnlineExams').prop('disabled', true);
      $('#platform_OnlineExams').prop('checked', false);
    } else {
      $('#platform_OnlineExams').prop('disabled', false);
    }
  }).trigger('change');

  const $durationField = $('select[name=durationMinutes]');
  $('input[name=assessmentType]').on('change', () => {
    const validDurations = $('input[name=assessmentType]:checked').data('valid-durations');
    $durationField.closest('.form-group').toggle(validDurations.length > 0);
    $durationField.find('option').each((_, option) => {
      const $option = $(option);
      const isValidOption = validDurations.includes(Number.parseInt($option.val(), 10));
      $option.toggle(isValidOption).prop('disabled', !isValidOption);
    });
  }).filter(':checked').trigger('change');
});
