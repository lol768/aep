function requestPermission(e) {
  e.preventDefault();
  Notification.requestPermission().then((result) => {
    if (result !== 'default') {
      const alert = document.querySelector('div.notification-permission');
      if (alert) {
        alert.classList.add('hidden');
      }
    }
  });
  return false;
}

if (Notification.permission === 'default') {
  const alert = document.querySelector('div.notification-permission');
  if (alert) {
    const btn = alert.querySelector('a.btn');
    btn.addEventListener('click', requestPermission);
    btn.addEventListener('keydown', requestPermission);
    alert.classList.remove('hidden');
  }
}
