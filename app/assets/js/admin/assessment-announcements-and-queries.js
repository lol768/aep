import $ from 'jquery';
import WebSocketConnection from '../web-sockets';

function handleMessage(data) {
  $('<div/>')
    .addClass('query new')
    .append($('<div/>').addClass('query-user').text(data.client))
    .append($('<div/>').addClass('query-text').html(data.message))
    .append($('<div/>').addClass('query-time').html(data.timestamp))
    .prependTo($('.query-container'));
}

function handleAnnouncement(data) {
  $('<div/>')
    .addClass('announcement')
    .append($('<div/>').addClass('query-user').text('Announcement'))
    .append($('<div/>').addClass('query-text').html(data.message))
    .append($('<div/>').addClass('query-time').html(data.timestamp))
    .prependTo($('.announcement-container'));
}

function init() {
  import('../web-sockets').then(() => {
    const websocket = new WebSocketConnection(`wss://${window.location.host}/websocket`);

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

    websocket.connect();
  });
}

init();
