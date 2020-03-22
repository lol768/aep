/* eslint-env browser */
/*
 * Entrypoint for the frontend section of the app.
 *
 * render.js rules
 *  - no ID7
 *  - no jQuery
 *  - minimal libraries (let's see if we can do without moment-timezone)
 *  - if it isn't essential for page, load dynamically (import() call returning Promise)
 */

import './polyfills';
import * as log from './log';
import UploadWithProgress from './upload-with-progress';
import '@universityofwarwick/id7/js/id7-default-feature-detect';
import WebSocketConnection from './web-sockets';

// dynamic import, fire and forget.
/* eslint-ignore no-unused-expressions */
import(/* webpackChunkName: "statuspage-widget" */'@universityofwarwick/statuspage-widget/dist/main').then(() => {
  log.info('statuspage-widget script loaded');
});

// not doing a dynamic import at the moment, since this seems reasonably critical
// (if relevant to the page)

(new UploadWithProgress(document, () => {
  log.info('Upload success callback');
}, () => {
  log.warn('Upload failure callback');
})).initialise();

document.addEventListener('DOMContentLoaded', () => {
  if (!document.body.classList.contains('connect-ws')) {
    return;
  }

  function setVisibilityByClassName(className, visible) {
    document.querySelectorAll(`.${className}`).forEach((node) => {
      if (visible) {
        node.classList.remove('hide');
      } else {
        node.classList.add('hide');
      }
    });
  }

  import('./web-sockets').then(() => {
    const websocket = new WebSocketConnection(`wss://${window.location.host}/websocket`, {
      onConnect: () => {
        setVisibilityByClassName('ws-connected', true);
        setVisibilityByClassName('ws-disconnected', false);
        setVisibilityByClassName('ws-error', false);
      },
      onError: () => {
        setVisibilityByClassName('ws-connected', false);
        setVisibilityByClassName('ws-error', true);
      },
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
        }
      },
      onClose: () => {
        setVisibilityByClassName('ws-connected', false);
        setVisibilityByClassName('ws-disconnected', true);
      },
    });
    websocket.connect();
    window.ws = websocket.ws;
  });
});
