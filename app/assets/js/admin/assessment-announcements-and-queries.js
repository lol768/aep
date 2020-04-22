import $ from 'jquery';

function handleMessage(data) {
  const $newMessage = $('<div/>')
    .addClass(`message message-${data.sender.toLowerCase()}`)
    .append($('<div/>').addClass('message-date').html(data.timestamp))
    .append($('<div/>').addClass('message-author').text(data.senderName))
    .append($('<div/>').addClass('message-text').html(data.messageHTML));

  const $container = $(`.panel-body[data-student-id="${data.studentId}"]`);
  const $threadContainer = $('#messages-accordion');

  if ($threadContainer.length > 0) {
    let $thread = $container.closest('.panel');
    if ($container.length === 0) {
      $thread = $(data.messageThread);
      $thread.find('.panel-collapse').append($('<div/>').addClass('panel-body').attr('data-student-id', data.studentId).prepend($newMessage));
      $thread.prependTo($threadContainer);
    } else {
      $container.prepend($newMessage);
      $thread.find('.panel-title .original-date').html($('<div/>').append(data.timestamp).find('.original-date'));
      $thread.remove().prependTo($threadContainer);
    }

    if ($thread.find('.collapsed').length > 0) {
      const $title = $thread.find('.panel-title');
      $('<i/>').addClass('fad fa-comment-edit heartbeat').insertAfter($title.find('a div.pull-right'));
    }

    const $summary = $threadContainer.closest('details').find('summary');
    const studentCount = $threadContainer.find('.panel').length;
    const messageCount = $threadContainer.find('.message-student').length;
    $summary.text(`Queries (${messageCount} ${messageCount === 1 ? 'query' : 'queries'} from ${studentCount} ${studentCount === 1 ? 'student' : 'students'})`);
  } else {
    $container.prepend($newMessage);
  }
}

function handleAnnouncement(data) {
  $('<div/>')
    .addClass('announcement')
    .append($('<div/>').addClass('message-author').text(data.senderName))
    .append($('<div/>').addClass('message-text').html(data.messageHTML))
    .append($('<div/>').addClass('message-date').html(data.timestamp))
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

  $('#messages-accordion').on('shown.bs.collapse', (e) => {
    $(e.target).closest('.panel').find('.panel-title i.heartbeat').remove();
  });
}

init();
