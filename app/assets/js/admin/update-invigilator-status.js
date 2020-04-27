import $ from 'jquery';
import log from 'loglevel';

$(() => {
  const updateAll = () => {
    const $container = $('.invigilator-list');
    $container.find('.loading').removeClass('hidden');
    $.get($container.eq(0).data('uri'))
      .then((data) => {
        $container.prop('outerHTML', data);
        $container.find('.loading').addClass('hidden');
      })
      .catch((errorText) => {
        log.error(errorText.statusText);
        $container.find('.loading').addClass('hidden');
        $container.find('.last-updated').addClass('hidden');
        $container.find('.alert-danger').removeClass('hidden');
      });
  };
  if ($('.invigilator-list[data-uri]').length > 0) {
    setInterval(updateAll, 30000);
  }
});
