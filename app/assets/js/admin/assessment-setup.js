import $ from 'jquery';

$(() => {
  $('#platform_field').find('input:radio').on('change', () => {
    const isOnlineExams = $('#platform_OnlineExams').is(':checked');

    $('#url_field').toggleClass('hide', isOnlineExams)
      .find('input').prop('disabled', isOnlineExams);
  }).trigger('change');
});
