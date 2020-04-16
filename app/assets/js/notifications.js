import { checkNotificationPromise } from './assessment-announcements';

function requestPermission(e) {
  e.preventDefault();

  const callback = (result) => {
    if (result !== 'default') {
      const alert = document.querySelector('div.notification-permission');
      if (alert) {
        alert.classList.add('hidden');
      }
    }
  };

  if (checkNotificationPromise()) {
    Notification.requestPermission().then(callback);
  } else {
    Notification.requestPermission(callback);
  }

  return false;
}

if ('Notification' in window) {
  if (Notification.permission === 'default') {
    const alert = document.querySelector('div.notification-permission');
    if (alert) {
      const btn = alert.querySelector('a.btn');
      btn.addEventListener('click', requestPermission);
      btn.addEventListener('keydown', requestPermission);
      alert.classList.remove('hidden');
    }
  }
}
