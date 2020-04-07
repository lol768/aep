import $ from 'jquery';

$(() => {
  $('#platform_field').find('input:checkbox').on('change', (e) => {
    const count = $('#platform_field').find('input:checkbox:checked').length;
    if (count > 2) {
      $('#platformModal').modal('show');
      e.target.checked = false;
    } else {
      const isOnlineExams = $('#OnlineExams').is(':checked');

      $('#url_field').toggleClass('hide', isOnlineExams)
        .find('input').prop('disabled', isOnlineExams);
    }
  }).trigger('change');

  $('#assessmentType_field').find('input:radio').on('change', () => {
    const isBespoke = $('#assessmentType_Bespoke').is(':checked');

    if (isBespoke) {
      $('#OnlineExams').prop('disabled', true);
      $('#OnlineExams').prop('checked', false);
    } else {
      $('#OnlineExams').prop('disabled', false);
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
