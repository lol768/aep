// ESLint ignoring this as I'm sure more helpers will be here later
// eslint-disable-next-line import/prefer-default-export
export function checkNotificationPromise() {
  // https://developer.mozilla.org/en-US/docs/Web/API/Notifications_API/Using_the_Notifications_API
  try {
    Notification.requestPermission().then();
  } catch (e) {
    return false;
  }

  return true;
}
