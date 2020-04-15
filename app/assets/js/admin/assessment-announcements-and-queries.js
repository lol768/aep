import $ from 'jquery';

function handleMessage(data) {
  $('<div/>')
    .addClass('query new')
    .append($('<div/>').addClass('query-user').text(data.client))
    .append($('<div/>').addClass('query-text').html(data.messageHTML))
    .append($('<div/>').addClass('query-time').html(data.timestamp))
    .prependTo($('.query-container'));
}

function handleAnnouncement(data) {
  $('<div/>')
    .addClass('announcement')
    .append($('<div/>').addClass('query-user').text('Announcement'))
    .append($('<div/>').addClass('query-text').html(data.messageHTML))
    .append($('<div/>').addClass('query-time').html(data.timestamp))
    .prependTo($('.announcement-container'));
}

function init() {
  import('../central-web-socket').then(({ default: websocket }) => {
    websocket.add({
      onData: (d) => {
        switch (d.type) {
          case 'assessmentMessage':
            handleMessage(d);
            break;
          case 'announcement':
            handleAnnouncement(d);
            break;
          default:
            break;
        }
      },
    });
  });
}

init();
