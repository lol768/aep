import $ from 'jquery';

$(() => {
  $('#platform_field').find('input:checkbox').on('change', () => {
    const isOnlineExams = $('#platform[]OnlineExams').is(':checked');

    $('#url_field').toggleClass('hide', isOnlineExams)
      .find('input').prop('disabled', isOnlineExams);

    $('#files_field').toggleClass('hide', !isOnlineExams);
  }).trigger('change');
});
