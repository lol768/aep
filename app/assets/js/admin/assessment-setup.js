import $ from 'jquery';

$(() => {
  $('#platform_field').find('input:radio').on('change', () => {
    const isOnlineExams = $('#platform_OnlineExams').is(':checked');

    $('#url_field').toggleClass('hide', isOnlineExams)
      .find('input').prop('disabled', isOnlineExams);
  }).trigger('change');

  const $durationField = $('select[name=durationMinutes]');
  $('input[name=assessmentType]').on('change', () => {
    const validDurations = $('input[name=assessmentType]:checked').data('valid-durations');
    const hasOptions = validDurations.length > 0;
    $durationField.closest('.form-group').toggle(hasOptions);
    $durationField.prop('disabled', !hasOptions);
    $durationField.find('option').each((_, option) => {
      const $option = $(option);
      const isValidOption = validDurations.includes(Number.parseInt($option.val(), 10));
      $option.toggle(isValidOption).prop('disabled', !isValidOption);
    });
  }).filter(':checked').trigger('change');
});
