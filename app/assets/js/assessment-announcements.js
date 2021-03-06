import JDDT from './jddt';
import { checkNotificationPromise } from './notifications-api';
import * as log from './log';

/**
 * @typedef {Object} AnnouncementResponse
 * @property {string} id - Unique identifier for this announcement
 * @property {string} assessmentId - Unique identifier for the assessment
 * this announcement relates to
 * @property {string} messageHTML
 * @property {string} messageText
 * @property {string} timestamp - Formatted date string
 */

/**
 * Separated for testing purposes - takes data and returns it as nice HTML
 * @param {AnnouncementResponse} d - as received by the websocket
 * @returns {HTMLDivElement} - formatted nicely and ready for appending to the document
 */
export function formatAnnouncement(d) {
  const el = document.createElement('div');
  el.classList.add('alert', 'alert-info', 'media');
  el.dataset.announcementId = d.id;

  const icon = document.createElement('i');
  icon.setAttribute('aria-hidden', 'true');
  const iconName = 'bullhorn';
  icon.classList.add('fad', `fa-${iconName}`);

  const timestamp = document.createElement('div');
  timestamp.classList.add('query-time');
  timestamp.innerHTML = d.timestamp;

  const mediaLeft = document.createElement('div');
  mediaLeft.classList.add('media-left');

  const mediaBody = document.createElement('div');
  mediaBody.classList.add('media-body');

  // assemble
  mediaLeft.appendChild(icon);
  mediaBody.innerHTML = d.messageHTML;
  mediaBody.appendChild(timestamp);
  el.appendChild(mediaLeft);
  el.appendChild(mediaBody);
  return el;
}

/**
 * Separated for testing purposes - decides what to do with received data
 * @param {AnnouncementResponse} d - JSON from the web socket
 * @param {string} assessmentId - UUID found in body dataset
 * @param {HTMLElement} messageList - output destination
 */
export function handleData(d, assessmentId, messageList) {
  if (d.assessmentId && d.assessmentId === assessmentId) {
    if (messageList && d.type === 'announcement') {
      const existingAnnouncement = messageList.querySelector(`[data-announcement-id="${d.id}"]`);
      if (!existingAnnouncement) {
        const el = formatAnnouncement(d);
        messageList.appendChild(el);
        JDDT.initialise(el);

        if ('Notification' in window && Notification.permission === 'granted') {
          new Notification('Assessment announcement', { // eslint-disable-line no-new
            body: d.messageText,
            requireInteraction: true,
          });
        } else {
          window.alert(`New message from invigilators: \n${d.messageText}`); // eslint-disable-line no-alert
        }
      }
    }
  }
}

/**
 * @param {WebSocketConnection} websocket
 */
export default function initAnnouncements(websocket) {
  const { assessmentId } = document.body.dataset;
  const messageList = document.querySelector('.message-list');

  if (!assessmentId) {
    log.error('No data-assessment-id found on body');
    return;
  }

  websocket.add({
    onHeartbeat: (ws) => {
      ws.send(JSON.stringify({
        type: 'RequestAnnouncements',
        data: {
          assessmentId,
        },
      }));
    },
    onData: (d) => handleData(d, assessmentId, messageList),
  });

  const button = document.getElementById('request-notification-permission');
  const testButton = document.getElementById('send-test-notification');

  if (button && testButton) {
    if ('Notification' in window) {
      if (Notification.permission === 'granted') {
        button.disabled = true;
        testButton.disabled = false;
        button.textContent = 'Notifications enabled';
      } else if (Notification.permission === 'denied') {
        button.disabled = true;
        testButton.disabled = true;
        button.textContent = 'Notifications blocked';
      } else {
        testButton.disabled = true;

        const callback = (permission) => {
          button.disabled = true;

          if (permission === 'granted') {
            button.textContent = 'Notifications enabled';
            testButton.disabled = false;

            new Notification('Notifications enabled', { // eslint-disable-line no-new
              body: 'Notifications are enabled. Any announcements made during your assessment will appear here.',
              requireInteraction: true,
            });
          } else {
            button.textContent = 'Notifications blocked';
          }
        };

        button.addEventListener('click', () => {
          if (checkNotificationPromise()) {
            Notification.requestPermission().then(callback);
          } else {
            Notification.requestPermission(callback);
          }
        });
      }
    } else {
      button.disabled = true;
      testButton.disabled = true;
      button.textContent = 'Notifications unsupported';
    }

    testButton.addEventListener('click', () => {
      if ('Notification' in window && Notification.permission === 'granted') {
        new Notification('Test notification', { // eslint-disable-line no-new
          body: 'This is a test notification.',
          requireInteraction: true,
        });
      }
    });
  }
}
