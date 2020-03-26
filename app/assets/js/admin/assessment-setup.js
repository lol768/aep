import $ from 'jquery';

$(() => {
  $('#platform_field').find('input:radio').on('change', () => {
    const isOnlineExams = $('#platform_OnlineExams').is(':checked');

    $('#url_field').toggleClass('hide', isOnlineExams);
    $('#files_field').toggleClass('hide', !isOnlineExams);
  }).trigger('change');
});
