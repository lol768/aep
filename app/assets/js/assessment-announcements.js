import JDDT from './jddt';
import { checkNotificationPromise } from './notifications-api';

/**
 * Separated for testing purposes - takes data and returns it as nice HTML
 * @param {object} d - as received by the websocket
 * @returns {HTMLDivElement} - formatted nicely and ready for appending to the document
 */
export function formatAnnouncement(d) {
  const el = document.createElement('div');
  el.classList.add('alert', 'alert-info', 'media');
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
  mediaLeft.appendChild(icon);
  mediaBody.innerHTML = d.messageHTML;
  mediaBody.appendChild(timestamp);
  el.appendChild(mediaLeft);
  el.appendChild(mediaBody);
  return el;
}

export default function initAnnouncements(websocket) {
  websocket.add({
    onData: (d) => {
      const messageList = document.querySelector('.message-list');
      if (messageList && d.type === 'announcement') {
        const el = formatAnnouncement(d);
        messageList.appendChild(el);
        JDDT.initialise(messageList);

        if ('Notification' in window && Notification.permission === 'granted') {
          new Notification('Assessment announcement', { // eslint-disable-line no-new
            body: d.messageText,
            requireInteraction: true,
          });
        } else {
          window.alert(`New message from invigilators: \n${d.messageText}`); // eslint-disable-line no-alert
        }
      }
    },
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
