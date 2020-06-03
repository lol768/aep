import $ from 'jquery';
import { postJsonWithCredentials } from '@universityofwarwick/serverpipe';
import log from 'loglevel';
import { updateDocumentTitle, originalDocumentTitle } from '../sets-document-title-prefix';

function handleMessage(data) {
  const actualAssessmentId = data.assessmentId;
  const expectedAssessmentId = $('.in-progress-assessment-data').data('assessment');
  if (actualAssessmentId !== expectedAssessmentId) {
    return;
  }

  const $newMessage = $('<div/>')
    .addClass(`message message-${data.sender.toLowerCase()}`)
    .append($('<div/>').addClass('message-date').html(data.timestamp))
    .append($('<div/>').addClass('message-author').text(data.senderName))
    .append($('<div/>').addClass('message-text').html(data.messageHTML));

  let $container = $(`.panel-body[data-student-id="${data.studentId}"]`);
  const $threadContainer = $('#messages-accordion');

  if ($threadContainer.length > 0) {
    let $thread = $container.closest('.panel');
    if ($container.length === 0) {
      $thread = $(data.messageThread);
      $container = $thread.find('.panel-body');
      $thread.prependTo($threadContainer);
    } else {
      $container.prepend($newMessage);
      $thread.find('.panel-title .original-date').html($('<div/>').append(data.timestamp).find('.original-date'));
      $thread.remove().prependTo($threadContainer);
    }

    if ($thread.find('.panel-footer form').length > 0) {
      $container.append($newMessage);
      $container.scrollTop(Number.MAX_SAFE_INTEGER);
    } else {
      $container.prepend($newMessage);
    }

    if ($thread.find('.collapsed').length > 0 && $thread.find('.panel-title i.heartbeat').length === 0) {
      const $title = $thread.find('.panel-title');
      $('<i/>').addClass('fad fa-comment-edit heartbeat').insertAfter($title.find('a div.pull-right'));
    }

    const $summary = $threadContainer.closest('details').find('summary');
    const studentCount = $threadContainer.find('.panel').length;
    const messageCount = $threadContainer.find('.message-student').length;
    $summary.text(`Queries (${messageCount} ${messageCount === 1 ? 'query' : 'queries'} from ${studentCount} ${studentCount === 1 ? 'student' : 'students'})`);

    document.title = `(${messageCount}) ${originalDocumentTitle}`;
  } else {
    $container.prepend($newMessage);
  }

  if ('Notification' in window && Notification.permission === 'granted') {
    new Notification(`Query from ${data.senderName}`, { // eslint-disable-line no-new
      body: data.messageText,
      requireInteraction: true,
    });
  } else {
    window.alert(`New query from ${data.senderName}:\n${data.messageText}`); // eslint-disable-line no-alert
  }
}

function handleAnnouncement(data) {
  const $container = $('.announcement-container');

  $('<div/>')
    .addClass('announcement')
    .append($('<div/>').addClass('message-date').html(data.timestamp))
    .append($('<div/>').addClass('message-author').text(data.senderName))
    .append($('<div/>').addClass('message-text').html(data.messageHTML))
    .prependTo($container);

  $container.prev('summary').text(`Announcements (${$container.children('.announcement').length})`);
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
    const $target = $(e.target);
    $target.find('.panel-body').scrollTop(Number.MAX_SAFE_INTEGER);
    $target.closest('.panel').find('.panel-title i.heartbeat').remove();
  }).on('submit', (e) => {
    e.preventDefault();
    const $form = $(e.target);
    $form.find('.alert-danger').empty().addClass('hidden');
    $form.find('button').prop('disabled', true);
    postJsonWithCredentials($form.attr('action'), { message: $form.find('textarea').val() })
      .then((response) => response.json())
      .then((response) => {
        $form.find('button').prop('disabled', false);
        if (response.success) {
          $form.find('.alert-danger').empty().addClass('hidden');
          $form.find('textarea').val('');
          $form.closest('.panel').find('.panel-body').scrollTop(Number.MAX_SAFE_INTEGER);
        } else {
          let errorMessage = 'The reply couldn\'t be sent';
          if (response.errors) {
            errorMessage = response.errors.map((error) => error.message || error).join(', ');
          }
          $form.find('.alert-danger').removeClass('hidden').text(errorMessage);
        }
      })
      .catch((error) => {
        log.error(error);
        $form.find('.alert-danger').removeClass('hidden').text(error.message || error);
        $form.find('button').prop('disabled', false);
      });
  });
}

init();
updateDocumentTitle(document);
