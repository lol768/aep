/**
 * Adds a notification to the bottom of the page that can be removed by clicking the x icon
 * @param {string} notificationText
 */
export default function showNotification() {
  if (window.scrollY === 0) {
    return; // Already at the top of the screen so no need to notify
  }
  if (document.querySelectorAll('.dismissable-notification').length > 0) {
    return; // There's already a notification showing, don't duplicate
  }

  function makeEl(tagName, className) {
    const el = document.createElement(tagName);
    el.className = className;
    return el;
  }

  const notificationContainer = makeEl('div', 'dismissable-notification media');
  const mediaLeft = makeEl('div', 'media-left');
  const mediaBody = makeEl('div', 'media-body');
  const mediaRight = makeEl('div', 'media-right');

  const notificationIcon = makeEl('i', 'fad fa-bullhorn');
  notificationIcon.setAttribute('aria-hidden', 'true');

  const scrollUpLink = makeEl('a', 'scroll-up-link');
  scrollUpLink.setAttribute('href', '#');
  scrollUpLink.setAttribute('role', 'button');
  scrollUpLink.addEventListener('click', (e) => {
    e.preventDefault();
    window.scrollTo(0, 0);
    document.body.removeChild(notificationContainer);
  });
  scrollUpLink.innerText = 'see all announcements ';

  const scrollUpIcon = makeEl('i', 'fad themed-duotone fa-arrow-to-top');
  scrollUpIcon.setAttribute('aria-hidden', 'true');

  scrollUpLink.appendChild(scrollUpIcon);

  const closeLink = makeEl('a', 'close-button');
  closeLink.setAttribute('role', 'button');
  closeLink.setAttribute('href', '#');
  closeLink.setAttribute('title', 'Dismiss');
  closeLink.addEventListener('click', (e) => {
    e.preventDefault();
    document.body.removeChild(notificationContainer);
  });

  const closeIcon = makeEl('i', 'fas fa-times close-icon');
  closeIcon.setAttribute('aria-hidden', 'true');

  closeLink.appendChild(closeIcon);

  mediaLeft.appendChild(notificationIcon);
  mediaBody.innerHTML = 'New announcement&hellip; ';
  mediaBody.appendChild(scrollUpLink);
  mediaRight.appendChild(closeLink);
  notificationContainer.appendChild(mediaLeft);
  notificationContainer.appendChild(mediaBody);
  notificationContainer.appendChild(mediaRight);

  document.body.appendChild(notificationContainer);
}
