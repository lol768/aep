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
import * as countdown from 'countdown';
import * as log from './log';
import UploadWithProgress from './upload-with-progress';
import '@universityofwarwick/id7/js/id7-default-feature-detect';

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
  if (document.body.classList.contains('connect-ws')) {
    import('./assessment-announcements');
  }
});

[...document.getElementsByClassName('time-left-to-start')].forEach((node) => {
  const refresh = () => {
    const { dataset } = node;
    const start = Number(dataset.start);
    const end = Number(dataset.end);
    const now = Number(new Date());

    if (Number.isNaN(end) || Number.isNaN(end)) return;

    let text;
    if (now < start) {
      text = `Start in ${countdown(null, new Date(start), 220).toString()}`;
    } else if (now > start && now < end) {
      text = `${countdown(null, new Date(end), 220).toString()} left to start`;
    } else {
      text = 'You missed the exam';
    }

    const textNode = document.createTextNode(text);
    const existingTextNode = node.lastChild;
    if (existingTextNode) {
      node.replaceChild(textNode, existingTextNode);
    } else {
      node.appendChild(textNode);
    }
  };
  refresh();
  setInterval(refresh, 30000);
});
