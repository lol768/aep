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
import initErrorReporter from './error-reporter';
import * as log from './log';
import UploadWithProgress from './upload-with-progress';
import '@universityofwarwick/id7/js/id7-default-feature-detect';
import JDDT from './jddt';

initErrorReporter();

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

JDDT.initialise(document);

document.addEventListener('DOMContentLoaded', () => {
  if (document.body.classList.contains('connect-ws')) {
    import('./assessment-announcements');
  }

  if (document.querySelectorAll('.undisable-with-checkbox[data-undisable-selector]').length > 0) {
    import('./undisable-with-checkbox');
  }

  if (document.querySelector('.time-left-to-start')) import('./assessment-timing');
});
