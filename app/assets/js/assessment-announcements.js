function checkNotificationPromise() {
  // https://developer.mozilla.org/en-US/docs/Web/API/Notifications_API/Using_the_Notifications_API
  try {
    Notification.requestPermission().then();
  } catch (e) {
    return false;
  }

  return true;
}

export default function initAnnouncements(websocket) {
  websocket.add({
    onData: (d) => {
      if (d.type === 'announcement' && document.querySelector('.message-list') !== undefined) {
        const messageList = document.querySelector('.message-list');
        const el = document.createElement('div');
        el.classList.add('alert', 'alert-info');
        const icon = document.createElement('i');
        icon.setAttribute('aria-hidden', 'true');
        const iconName = 'bullhorn';
        icon.classList.add('fad', `fa-${iconName}`);
        const data = document.createTextNode(d.message);
        el.appendChild(icon);
        el.appendChild(document.createTextNode(' '));
        el.appendChild(data);
        messageList.appendChild(el);

        if ('Notification' in window && Notification.permission === 'granted') {
          new Notification('Exam announcement', { // eslint-disable-line no-new
            body: d.message,
            requireInteraction: true,
          });
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
              body: 'Notifications are enabled. Any announcements made during your exam will appear here.',
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
