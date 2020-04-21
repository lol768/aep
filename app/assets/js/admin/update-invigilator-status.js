import $ from 'jquery';

$(() => {
  const updateAll = () => {
    $('.invigilator-list .loading').removeClass('hide');
    $.get($('.invigilator-list').eq(0).data('uri'), (data) => {
      $('.invigilator-list').prop('outerHTML', data);
      $('.invigilator-list .loading').addClass('hide');
    });
  };
  if ($('.invigilator-list[data-uri]').length > 0) {
    setInterval(updateAll, 30000);
  }
});
